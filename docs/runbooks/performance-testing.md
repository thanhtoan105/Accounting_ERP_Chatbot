# Performance Testing Runbook

This runbook covers performance testing for dashboard widgets per **AC 8.0.4**: All widgets load in <2s P95 for datasets ≤50k transactions.

## Quick Start

```bash
# Generate test data + analyze queries + benchmark
./scripts/performance/run_performance_test.sh --all

# Or run individual steps
./scripts/performance/run_performance_test.sh --generate    # Generate 50k transactions
./scripts/performance/run_performance_test.sh --analyze     # Run EXPLAIN ANALYZE
./scripts/performance/run_performance_test.sh --benchmark   # Run 100 iterations
```

## Prerequisites

1. **PostgreSQL** running with the accounting database
2. **psql** client installed
3. **Chart of Accounts** seeded (TT200 template)
4. **k6** (optional, for load testing): `brew install k6` or [k6.io](https://k6.io/docs/getting-started/installation/)

## Test Data Generation

The `generate_test_data.sql` script creates:

| Entity | Count | Purpose |
|--------|-------|---------|
| Customers | 1,000 | AR aging, top debtors |
| Suppliers | 500 | AP aging, top creditors |
| Accounting Periods | 12 | Period summary, period locks |
| Vouchers | 50,000 | Transaction volume test |
| Journal Entries | ~100,000 | 2 entries per voucher |

### Manual Execution

```bash
# Using psql directly
psql -d accounting -f scripts/performance/generate_test_data.sql

# With custom database connection
PGPASSWORD=secret psql -h localhost -p 5432 -d accounting -U postgres \
  -f scripts/performance/generate_test_data.sql
```

### Expected Output

```
Step 1: Generating 1000 customers...
Created/Found 1000 customers
Step 2: Generating 500 suppliers...
Created/Found 500 suppliers
Step 3: Generating 12 accounting periods...
Step 4: Looking up chart of accounts...
Step 5: Generating 50000 vouchers with journal entries...
Progress: 10% (5000 vouchers)
Progress: 20% (10000 vouchers)
...
Step 6: Refreshing materialized views...
============================================================
PERFORMANCE TEST DATA GENERATION COMPLETE
============================================================
```

## Query Performance Analysis

### EXPLAIN ANALYZE

Run detailed query analysis:

```bash
psql -d accounting -f scripts/performance/analyze_mv_queries.sql
```

This analyzes 6 dashboard queries:

1. **Revenue vs Expenses** - `mv_daily_revenue_expense`
2. **AR/AP Aging** - `mv_ar_ap_aging`
3. **Cash Position** - `mv_cash_flow_summary`
4. **Top 5 Debtors** - `mv_top_debtors_creditors`
5. **Top 5 Creditors** - `mv_top_debtors_creditors`
6. **Period Summary** - `mv_period_summary`

### Reading EXPLAIN ANALYZE Output

```
 Aggregate  (cost=100..200 rows=1 width=32) (actual time=0.145..0.146 rows=1 loops=1)
   Buffers: shared hit=50
   ->  Index Scan using idx_mv_daily_rev_exp_company on mv_daily_revenue_expense
         Index Cond: (company_id = 1)
         Buffers: shared hit=50
 Planning Time: 0.150 ms
 Execution Time: 0.180 ms
```

**Key metrics:**
- `actual time=X..Y` - Execution time in milliseconds
- `Buffers: shared hit=N` - Pages read from cache (lower is better)
- `Index Scan` - Good (using index)
- `Seq Scan` - May be slow on large tables

### Expected Performance

| Query | Expected P95 | Notes |
|-------|-------------|-------|
| Revenue/Expense | <100ms | Indexed by company_id, date |
| AR/AP Aging | <200ms | Pre-aggregated buckets |
| Cash Flow | <100ms | Indexed by company_id, date |
| Top Debtors | <50ms | Pre-ranked, LIMIT 5 |
| Top Creditors | <50ms | Pre-ranked, LIMIT 5 |
| Period Summary | <50ms | Pre-aggregated by period |

## Benchmark Testing

### SQL-Based Benchmark

The runner script executes each query 100 times and calculates P95:

```bash
./scripts/performance/run_performance_test.sh --benchmark --iterations 100
```

Results are saved to `scripts/performance/results/benchmark_TIMESTAMP.csv`

### k6 Load Testing

For HTTP endpoint testing with concurrent users:

```bash
# Basic run
k6 run tests/load/dashboard-performance.k6.js

# With custom settings
k6 run \
  --env BASE_URL=http://localhost:8080 \
  --env API_TOKEN=your-jwt-token \
  --env COMPANY_ID=1 \
  --vus 20 \
  --duration 5m \
  tests/load/dashboard-performance.k6.js
```

### k6 Test Stages

1. **Ramp up** (30s): 0 → 5 users
2. **Steady** (1m): 10 users
3. **Spike** (30s): 20 users
4. **Recovery** (1m): 10 users
5. **Ramp down** (30s): 10 → 0 users

### k6 Thresholds

```javascript
thresholds: {
  'http_req_duration': ['p(95)<2000'],        // Overall P95 < 2s
  'widget_duration': ['p(95)<2000'],          // Widget-specific P95
  'widget_errors': ['rate<0.01'],             // <1% error rate
}
```

## Interpreting Results

### Pass Criteria (AC 8.0.4)

✓ **PASS** if:
- All queries complete in **<2000ms P95**
- Error rate **<1%**

✗ **FAIL** if:
- Any query exceeds **2000ms P95**
- Error rate **≥1%**

### Sample Output

```
============================================================================
P95 LATENCY RESULTS
============================================================================
  revenue_expense      P95:    45.32 ms  Avg:    12.50 ms  Max:    98.00 ms  ✓ PASS
  ar_ap_aging          P95:    82.15 ms  Avg:    25.30 ms  Max:   145.00 ms  ✓ PASS
  cash_flow            P95:    38.90 ms  Avg:    10.20 ms  Max:    75.00 ms  ✓ PASS
  top_debtors          P95:    15.50 ms  Avg:     5.00 ms  Max:    32.00 ms  ✓ PASS
  top_creditors        P95:    14.80 ms  Avg:     4.80 ms  Max:    28.00 ms  ✓ PASS
  period_summary       P95:    22.40 ms  Avg:     8.10 ms  Max:    45.00 ms  ✓ PASS
============================================================================
Target: P95 < 2000ms (2 seconds)
============================================================================
```

## Troubleshooting

### Slow Query Performance

1. **Check if MVs are refreshed:**
   ```sql
   SELECT * FROM dashboard_freshness WHERE company_id = 1;
   ```

2. **Refresh MVs manually:**
   ```sql
   SELECT refresh_dashboard_materialized_views();
   ```

3. **Check index usage:**
   ```sql
   SELECT * FROM pg_stat_user_indexes WHERE relname LIKE 'mv_%';
   ```

4. **Analyze tables:**
   ```sql
   ANALYZE mv_daily_revenue_expense;
   ANALYZE mv_ar_ap_aging;
   -- etc.
   ```

### Missing Test Data

If queries return empty results:

1. Verify vouchers exist:
   ```sql
   SELECT COUNT(*) FROM vouchers WHERE voucher_number LIKE 'PERF-%';
   ```

2. Verify journal entries:
   ```sql
   SELECT COUNT(*) FROM journal_entries je
   JOIN vouchers v ON je.voucher_id = v.id
   WHERE v.voucher_number LIKE 'PERF-%';
   ```

3. Re-run data generation:
   ```bash
   ./scripts/performance/run_performance_test.sh --generate
   ```

### Database Connection Issues

```bash
# Test connection
psql -h localhost -p 5432 -d accounting -U postgres -c "SELECT 1"

# Check Docker container
docker compose ps
docker compose logs postgres
```

## Optimization Recommendations

If P95 exceeds 2000ms:

1. **Add missing indexes**
   ```sql
   -- Example: Add composite index
   CREATE INDEX CONCURRENTLY idx_mv_example_composite
     ON mv_daily_revenue_expense(company_id, transaction_date);
   ```

2. **Increase work_mem for complex queries**
   ```sql
   SET work_mem = '256MB';
   ```

3. **Partition large MVs by date/period**
   - Consider TimescaleDB hypertables for time-series data

4. **Implement query caching**
   - Redis cache for frequently accessed dashboards
   - Consider 1-minute TTL for near-real-time data

5. **Optimize MV refresh strategy**
   - Use `CONCURRENTLY` for non-blocking refreshes
   - Stagger refresh times to avoid lock contention

## CI/CD Integration

Add to your pipeline:

```yaml
# .github/workflows/performance.yml
performance-test:
  runs-on: ubuntu-latest
  services:
    postgres:
      image: postgres:15
      env:
        POSTGRES_DB: accounting_test
        POSTGRES_PASSWORD: test
  steps:
    - uses: actions/checkout@v4
    - name: Run migrations
      run: |
        psql -d accounting_test -f backend/flyway/migrate.sql
    - name: Generate test data
      run: |
        psql -d accounting_test -f scripts/performance/generate_test_data.sql
    - name: Run performance tests
      run: |
        ./scripts/performance/run_performance_test.sh --benchmark
    - name: Upload results
      uses: actions/upload-artifact@v3
      with:
        name: performance-results
        path: scripts/performance/results/
```

## Related Files

- `scripts/performance/generate_test_data.sql` - Test data generator
- `scripts/performance/analyze_mv_queries.sql` - EXPLAIN ANALYZE queries
- `scripts/performance/run_performance_test.sh` - Runner script
- `tests/load/dashboard-performance.k6.js` - k6 load test
- `backend/.../V20251216005__fix_mvs_add_period_lock_columns.sql` - MV definitions
