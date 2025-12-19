#!/bin/bash
# ============================================================================
# Performance Test Runner
# ============================================================================
# Runs performance tests for dashboard materialized views
# Target: AC 8.0.4 - All widgets load in <2s P95 for datasets ≤50k transactions
#
# Usage: ./run_performance_test.sh [options]
#   Options:
#     --generate     Generate test data (50k transactions)
#     --analyze      Run EXPLAIN ANALYZE on all MV queries
#     --benchmark    Run multiple iterations and calculate P95
#     --all          Run all tests (default)
#     --company-id   Specify company ID (default: 1)
#     --iterations   Number of benchmark iterations (default: 100)
# ============================================================================

set -e

# Configuration
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"
RESULTS_DIR="$SCRIPT_DIR/results"
TIMESTAMP=$(date +%Y%m%d_%H%M%S)

# Default values
COMPANY_ID=1
ITERATIONS=100
RUN_GENERATE=false
RUN_ANALYZE=false
RUN_BENCHMARK=false
RUN_ALL=true

# Database connection (from environment or defaults)
DB_HOST="${DB_HOST:-localhost}"
DB_PORT="${DB_PORT:-5432}"
DB_NAME="${DB_NAME:-accounting}"
DB_USER="${DB_USER:-postgres}"

# Parse arguments
while [[ $# -gt 0 ]]; do
    case $1 in
        --generate)
            RUN_GENERATE=true
            RUN_ALL=false
            shift
            ;;
        --analyze)
            RUN_ANALYZE=true
            RUN_ALL=false
            shift
            ;;
        --benchmark)
            RUN_BENCHMARK=true
            RUN_ALL=false
            shift
            ;;
        --all)
            RUN_ALL=true
            shift
            ;;
        --company-id)
            COMPANY_ID="$2"
            shift 2
            ;;
        --iterations)
            ITERATIONS="$2"
            shift 2
            ;;
        --help)
            head -20 "$0" | tail -18
            exit 0
            ;;
        *)
            echo "Unknown option: $1"
            exit 1
            ;;
    esac
done

# Set run flags for --all
if [ "$RUN_ALL" = true ]; then
    RUN_GENERATE=true
    RUN_ANALYZE=true
    RUN_BENCHMARK=true
fi

# Create results directory
mkdir -p "$RESULTS_DIR"

echo "============================================================================"
echo "PERFORMANCE TEST RUNNER"
echo "============================================================================"
echo "Database: $DB_NAME @ $DB_HOST:$DB_PORT"
echo "Company ID: $COMPANY_ID"
echo "Results: $RESULTS_DIR"
echo "============================================================================"
echo ""

# Function to run psql
run_psql() {
    PGPASSWORD="${DB_PASSWORD:-postgres}" psql \
        -h "$DB_HOST" \
        -p "$DB_PORT" \
        -d "$DB_NAME" \
        -U "$DB_USER" \
        "$@"
}

# ============================================================================
# Step 1: Generate Test Data
# ============================================================================
if [ "$RUN_GENERATE" = true ]; then
    echo ">>> Step 1: Generating 50k test transactions..."
    echo ""
    
    GENERATE_LOG="$RESULTS_DIR/generate_${TIMESTAMP}.log"
    
    if run_psql -f "$SCRIPT_DIR/generate_test_data.sql" > "$GENERATE_LOG" 2>&1; then
        echo "✓ Test data generation complete. Log: $GENERATE_LOG"
        
        # Extract summary from log
        grep -A 10 "PERFORMANCE TEST DATA GENERATION COMPLETE" "$GENERATE_LOG" || true
    else
        echo "✗ Test data generation failed. Check log: $GENERATE_LOG"
        exit 1
    fi
    
    echo ""
fi

# ============================================================================
# Step 2: Run EXPLAIN ANALYZE
# ============================================================================
if [ "$RUN_ANALYZE" = true ]; then
    echo ">>> Step 2: Running EXPLAIN ANALYZE on MV queries..."
    echo ""
    
    ANALYZE_LOG="$RESULTS_DIR/analyze_${TIMESTAMP}.log"
    
    if run_psql \
        -v ANALYSIS_COMPANY_ID="$COMPANY_ID" \
        -f "$SCRIPT_DIR/analyze_mv_queries.sql" > "$ANALYZE_LOG" 2>&1; then
        echo "✓ EXPLAIN ANALYZE complete. Log: $ANALYZE_LOG"
        
        # Extract execution times
        echo ""
        echo "Query Execution Times (from EXPLAIN ANALYZE):"
        grep -E "Execution Time:" "$ANALYZE_LOG" | head -10 || echo "  (Check log for details)"
    else
        echo "✗ EXPLAIN ANALYZE failed. Check log: $ANALYZE_LOG"
        exit 1
    fi
    
    echo ""
fi

# ============================================================================
# Step 3: Benchmark (Multiple Iterations)
# ============================================================================
if [ "$RUN_BENCHMARK" = true ]; then
    echo ">>> Step 3: Running benchmark ($ITERATIONS iterations)..."
    echo ""
    
    BENCHMARK_LOG="$RESULTS_DIR/benchmark_${TIMESTAMP}.csv"
    
    # Write CSV header
    echo "query_name,iteration,execution_time_ms" > "$BENCHMARK_LOG"
    
    # Define queries to benchmark
    declare -A QUERIES
    QUERIES["revenue_expense"]="SELECT transaction_date, SUM(revenue), SUM(expense) FROM mv_daily_revenue_expense WHERE company_id = $COMPANY_ID AND transaction_date BETWEEN '2024-01-01' AND '2024-12-31' GROUP BY transaction_date ORDER BY transaction_date"
    QUERIES["ar_ap_aging"]="SELECT balance_type, SUM(bucket_current), SUM(bucket_1_30), SUM(total_outstanding) FROM mv_ar_ap_aging WHERE company_id = $COMPANY_ID GROUP BY balance_type"
    QUERIES["cash_flow"]="SELECT transaction_date, SUM(cash_in), SUM(cash_out), SUM(net_flow) FROM mv_cash_flow_summary WHERE company_id = $COMPANY_ID AND transaction_date BETWEEN '2024-01-01' AND '2024-12-31' GROUP BY transaction_date ORDER BY transaction_date"
    QUERIES["top_debtors"]="SELECT entity_name, balance, rank FROM mv_top_debtors_creditors WHERE company_id = $COMPANY_ID AND entity_type = 'DEBTOR' AND rank <= 5 ORDER BY rank"
    QUERIES["top_creditors"]="SELECT entity_name, balance, rank FROM mv_top_debtors_creditors WHERE company_id = $COMPANY_ID AND entity_type = 'CREDITOR' AND rank <= 5 ORDER BY rank"
    QUERIES["period_summary"]="SELECT period_id, total_revenue, total_expense, ar_balance, ap_balance, cash_balance FROM mv_period_summary WHERE company_id = $COMPANY_ID ORDER BY period_start DESC LIMIT 12"
    
    # Run benchmarks
    for query_name in "${!QUERIES[@]}"; do
        echo "  Benchmarking: $query_name"
        
        for ((i=1; i<=ITERATIONS; i++)); do
            # Run query and capture timing
            timing=$(run_psql -c "\\timing on" -c "${QUERIES[$query_name]}" 2>&1 | grep "Time:" | awk '{print $2}')
            
            if [ -n "$timing" ]; then
                echo "$query_name,$i,$timing" >> "$BENCHMARK_LOG"
            fi
            
            # Progress indicator
            if ((i % 20 == 0)); then
                echo "    Progress: $i/$ITERATIONS"
            fi
        done
    done
    
    echo ""
    echo "✓ Benchmark complete. Results: $BENCHMARK_LOG"
    
    # Calculate P95
    echo ""
    echo "============================================================================"
    echo "P95 LATENCY RESULTS"
    echo "============================================================================"
    
    for query_name in "${!QUERIES[@]}"; do
        # Extract times for this query, sort, and get P95
        times=$(grep "^$query_name," "$BENCHMARK_LOG" | cut -d',' -f3 | sort -n)
        count=$(echo "$times" | wc -l)
        p95_index=$(echo "$count * 0.95" | bc | cut -d'.' -f1)
        
        if [ -n "$p95_index" ] && [ "$p95_index" -gt 0 ]; then
            p95=$(echo "$times" | sed -n "${p95_index}p")
            avg=$(echo "$times" | awk '{sum+=$1} END {printf "%.2f", sum/NR}')
            max=$(echo "$times" | tail -1)
            
            # Check if P95 < 2000ms
            if (( $(echo "$p95 < 2000" | bc -l) )); then
                status="✓ PASS"
            else
                status="✗ FAIL"
            fi
            
            printf "  %-20s P95: %8s ms  Avg: %8s ms  Max: %8s ms  %s\n" \
                "$query_name" "$p95" "$avg" "$max" "$status"
        fi
    done
    
    echo "============================================================================"
    echo "Target: P95 < 2000ms (2 seconds)"
    echo "============================================================================"
fi

echo ""
echo "============================================================================"
echo "PERFORMANCE TEST COMPLETE"
echo "============================================================================"
echo "Results saved to: $RESULTS_DIR"
echo ""
echo "Generated files:"
ls -la "$RESULTS_DIR"/*_${TIMESTAMP}* 2>/dev/null || echo "  (no files generated)"
echo ""
