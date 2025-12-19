import http from 'k6/http'
import { check, sleep, group } from 'k6'
import { Trend, Rate, Counter } from 'k6/metrics'
import { textSummary as k6TextSummary } from 'https://jslib.k6.io/k6-summary/0.0.1/index.js'

/**
 * Dashboard Performance Load Test
 * ============================================================================
 * Tests dashboard widget endpoints against AC 8.0.4 requirements:
 * - All widgets load in <2s P95 for datasets ≤50k transactions
 *
 * Usage:
 *   k6 run --env BASE_URL=http://localhost:8080 dashboard-performance.k6.js
 *   k6 run --env BASE_URL=http://localhost:8080 --vus 10 --duration 2m dashboard-performance.k6.js
 */

// Custom metrics
const widgetDuration = new Trend('widget_duration', true)
const widgetErrors = new Rate('widget_errors')
const widgetRequests = new Counter('widget_requests')

// Test configuration
export const options = {
  stages: [
    { duration: '30s', target: 5 },   // Ramp up to 5 users
    { duration: '1m', target: 10 },   // Hold at 10 users
    { duration: '30s', target: 20 },  // Spike to 20 users
    { duration: '1m', target: 10 },   // Back to 10 users
    { duration: '30s', target: 0 },   // Ramp down
  ],
  thresholds: {
    // AC 8.0.4: All widgets must load in <2s P95
    'http_req_duration': ['p(95)<2000'],
    'widget_duration': ['p(95)<2000'],
    'widget_errors': ['rate<0.01'], // <1% error rate
    
    // Per-endpoint thresholds
    'http_req_duration{endpoint:freshness}': ['p(95)<500'],
    'http_req_duration{endpoint:revenue_expense}': ['p(95)<2000'],
    'http_req_duration{endpoint:aging}': ['p(95)<2000'],
    'http_req_duration{endpoint:cash_flow}': ['p(95)<2000'],
    'http_req_duration{endpoint:top_debtors}': ['p(95)<1000'],
    'http_req_duration{endpoint:top_creditors}': ['p(95)<1000'],
    'http_req_duration{endpoint:period_summary}': ['p(95)<2000'],
  },
}

// Configuration from environment
const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080'
const API_TOKEN = __ENV.API_TOKEN || ''
const COMPANY_ID = __ENV.COMPANY_ID || '1'

// Common headers
const headers = {
  'Content-Type': 'application/json',
  'Accept': 'application/json',
}

if (API_TOKEN) {
  headers['Authorization'] = `Bearer ${API_TOKEN}`
}

// Helper function to make API requests
function apiRequest(endpoint, name, params = {}) {
  const url = `${BASE_URL}${endpoint}`
  const startTime = Date.now()
  
  const res = http.get(url, {
    headers,
    tags: { endpoint: name },
    ...params,
  })
  
  const duration = Date.now() - startTime
  
  widgetDuration.add(duration, { endpoint: name })
  widgetRequests.add(1, { endpoint: name })
  
  const success = check(res, {
    [`${name}: status 200`]: (r) => r.status === 200,
    [`${name}: response time < 2s`]: (r) => r.timings.duration < 2000,
    [`${name}: has body`]: (r) => r.body && r.body.length > 0,
  })
  
  if (!success) {
    widgetErrors.add(1, { endpoint: name })
    console.log(`[${name}] Failed: status=${res.status}, duration=${duration}ms`)
  }
  
  return res
}

// Setup function - runs once before test
export function setup() {
  console.log(`Testing dashboard performance at: ${BASE_URL}`)
  console.log(`Company ID: ${COMPANY_ID}`)
  
  // Verify API is accessible
  const healthCheck = http.get(`${BASE_URL}/actuator/health`, { headers })
  if (healthCheck.status !== 200) {
    console.warn(`Health check failed: ${healthCheck.status}`)
  }
  
  return {
    companyId: COMPANY_ID,
    startDate: '2024-01-01',
    endDate: '2024-12-31',
  }
}

// Main test function
export default function(data) {
  const { companyId, startDate, endDate } = data
  
  // ========================================================================
  // Dashboard Freshness Check
  // ========================================================================
  group('Dashboard Freshness', () => {
    apiRequest(
      `/api/v1/dashboard/freshness`,
      'freshness'
    )
  })
  
  sleep(0.5)
  
  // ========================================================================
  // Revenue vs Expense Widget
  // ========================================================================
  group('Revenue vs Expense Widget', () => {
    apiRequest(
      `/api/v1/dashboard/revenue-expense?startDate=${startDate}&endDate=${endDate}`,
      'revenue_expense'
    )
  })
  
  sleep(0.5)
  
  // ========================================================================
  // AR/AP Aging Widget
  // ========================================================================
  group('AR/AP Aging Widget', () => {
    apiRequest(
      `/api/v1/dashboard/aging`,
      'aging'
    )
  })
  
  sleep(0.5)
  
  // ========================================================================
  // Cash Flow Widget
  // ========================================================================
  group('Cash Flow Widget', () => {
    apiRequest(
      `/api/v1/dashboard/cash-flow?startDate=${startDate}&endDate=${endDate}`,
      'cash_flow'
    )
  })
  
  sleep(0.5)
  
  // ========================================================================
  // Top Debtors Widget
  // ========================================================================
  group('Top Debtors Widget', () => {
    apiRequest(
      `/api/v1/dashboard/top-debtors?limit=5`,
      'top_debtors'
    )
  })
  
  sleep(0.5)
  
  // ========================================================================
  // Top Creditors Widget
  // ========================================================================
  group('Top Creditors Widget', () => {
    apiRequest(
      `/api/v1/dashboard/top-creditors?limit=5`,
      'top_creditors'
    )
  })
  
  sleep(0.5)
  
  // ========================================================================
  // Period Summary Widget
  // ========================================================================
  group('Period Summary Widget', () => {
    apiRequest(
      `/api/v1/dashboard/period-summary`,
      'period_summary'
    )
  })
  
  sleep(1)
}

// Teardown function - runs once after test
export function teardown(data) {
  console.log('Performance test complete')
}

// Handle summary
export function handleSummary(data) {
  const summary = {
    timestamp: new Date().toISOString(),
    target: 'AC 8.0.4: All widgets load in <2s P95',
    thresholds: {},
    metrics: {},
  }
  
  // Check threshold results
  for (const [name, threshold] of Object.entries(data.thresholds || {})) {
    summary.thresholds[name] = {
      passed: threshold.thresholds.every(t => t.ok),
      details: threshold.thresholds,
    }
  }
  
  // Extract key metrics
  if (data.metrics.http_req_duration) {
    summary.metrics.http_req_duration = {
      avg: data.metrics.http_req_duration.values.avg,
      p50: data.metrics.http_req_duration.values['p(50)'],
      p90: data.metrics.http_req_duration.values['p(90)'],
      p95: data.metrics.http_req_duration.values['p(95)'],
      p99: data.metrics.http_req_duration.values['p(99)'],
      max: data.metrics.http_req_duration.values.max,
    }
  }
  
  // Determine overall pass/fail
  const p95 = summary.metrics.http_req_duration?.p95 || 0
  summary.passed = p95 < 2000
  summary.message = summary.passed
    ? `✓ PASS: P95=${p95.toFixed(0)}ms < 2000ms`
    : `✗ FAIL: P95=${p95.toFixed(0)}ms >= 2000ms`
  
  return {
    'results/k6-summary.json': JSON.stringify(summary, null, 2),
    stdout: formatDashboardSummary(data) + '\n\n' + k6TextSummary(data, { indent: '  ', enableColors: true }),
  }
}

// Custom dashboard summary formatter
function formatDashboardSummary(data) {
  const lines = []
  lines.push('============================================================================')
  lines.push('DASHBOARD PERFORMANCE TEST RESULTS')
  lines.push('============================================================================')
  lines.push('')
  
  if (data.metrics.http_req_duration) {
    const m = data.metrics.http_req_duration.values
    lines.push('HTTP Request Duration:')
    lines.push(`  Avg:  ${m.avg?.toFixed(2) || 'N/A'} ms`)
    lines.push(`  P50:  ${m['p(50)']?.toFixed(2) || 'N/A'} ms`)
    lines.push(`  P90:  ${m['p(90)']?.toFixed(2) || 'N/A'} ms`)
    lines.push(`  P95:  ${m['p(95)']?.toFixed(2) || 'N/A'} ms (Target: <2000ms)`)
    lines.push(`  P99:  ${m['p(99)']?.toFixed(2) || 'N/A'} ms`)
    lines.push(`  Max:  ${m.max?.toFixed(2) || 'N/A'} ms`)
  }
  
  lines.push('')
  lines.push('Threshold Results:')
  for (const [name, result] of Object.entries(data.thresholds || {})) {
    const passed = result.thresholds.every(t => t.ok)
    lines.push(`  ${passed ? '✓' : '✗'} ${name}`)
  }
  
  lines.push('')
  lines.push('============================================================================')
  
  return lines.join('\n')
}
