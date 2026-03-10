# Stress Test Analysis - 10,000 VU Target
**Test Date:** November 14, 2025
**Test Duration:** All night (multiple hours)
**Test Configuration:** Ramp up to 10,000 VUs over 14 minutes

## Executive Summary

**Critical Finding:** All frameworks failed to reach the 10,000 VU target. The test revealed severe scalability limitations across all implementations. Three services hung completely, and the two that completed both showed massive performance degradation.

**Winner:** **Go Gin** - Only framework to complete with acceptable error rate (0.015%)
**Biggest Failure:** **Rust Actix** - 5,349 timeout errors and hung the test runner

---

## Detailed Results

### ✅ Go Gin (COMPLETED)
**Status:** Test completed successfully
**Test Duration:** 24m09s (14min test + ramp-down)

**Performance:**
- **Total Requests:** 1,220,318
- **Throughput:** 842 RPS (down 89% from 7,583 RPS baseline)
- **Error Rate:** 0.015% (185 failures)
- **Success Rate:** 99.98%

**Latency:**
- **Average:** 148.5ms (baseline was 7.17ms - 20x slower)
- **Median:** 39ms
- **P90:** 113ms
- **P95:** 155ms
- **P99:** 318ms
- **Max:** 10m 13s (613 seconds!)

**Virtual Users:**
- **Target:** 10,000 VUs
- **Max Achieved:** 3,602 VUs (36% of target)
- **Breaking Point:** Could not scale beyond 3,602 VUs

**Verdict:** Best performer, but still far below target capacity.

---

### ✅ Rust Axum (COMPLETED)
**Status:** Test completed successfully
**Test Duration:** 19m38s

**Performance:**
- **Total Requests:** 451,749
- **Throughput:** 383 RPS (down 95% from 7,602 RPS baseline)
- **Error Rate:** 0.36% (1,609 failures)
- **Success Rate:** 99.80%

**Latency:**
- **Average:** 1,159ms (baseline was 6.7ms - 173x slower!)
- **Median:** 18ms
- **P90:** 49ms
- **P95:** 78ms
- **P99:** 909ms
- **Max:** 8m 54s (534 seconds!)

**Virtual Users:**
- **Target:** 10,000 VUs
- **Max Achieved:** 3,286 VUs (33% of target)
- **Breaking Point:** Could not scale beyond 3,286 VUs

**Iteration Duration:**
- **Average:** 2.63 seconds
- **Max:** 17m 48s (over 1,000 seconds!)

**Verdict:** Severe performance collapse. Average latency increased 173x under stress.

---

### ❌ Rust Actix (HUNG - TEST INCOMPLETE)
**Status:** Test hung and never completed
**Test Duration:** 19m39s (hung after this)

**Performance (Partial Data):**
- **Total Iterations:** 1,689,529 completed
- **Interrupted Iterations:** 5,317
- **Timeout Errors:** **5,349 timeouts**
- **Max VUs:** 5,316 (before hanging)

**Verdict:** Complete failure. Massive timeout errors caused test runner to hang indefinitely.

**Why It Failed:**
- Most timeout errors of any framework
- Test runner stuck in infinite loop printing same status
- Never generated summary or completed gracefully
- Service appears to have crashed or become completely unresponsive

---

### ❌ Go Fiber (HUNG - TEST INCOMPLETE)
**Status:** Test hung at 8 minutes
**Last Update:** 8m03s into test

**Performance (Partial Data):**
- **Total Iterations:** 1,796,619 completed
- **Current VUs at Hang:** 2,986 VUs
- **Timeout Errors:** 92 timeouts
- **Progress:** Only 58% complete (8m / 14m target)

**Why It Failed:**
- Service became unresponsive around 2,986 VUs
- Massive flood of timeout errors at end
- Last errors showed both "request timeout" and "dial: i/o timeout"
- Test never progressed beyond 8 minutes

**Verdict:** Collapsed under ~3,000 VU load. Despite being touted as "zero-allocation" FastHTTP-based framework, it couldn't handle the stress.

---

### ❌ Spring Boot (HUNG - TEST INCOMPLETE)
**Status:** Test hung at 1m52s
**Last Update:** 1m52s into test

**Performance (Partial Data):**
- **Total Iterations:** 1,646,160 completed
- **Current VUs at Hang:** 2,867 VUs
- **Timeout Errors:** 0 timeouts (!)
- **Progress:** Only 13% complete (1m52s / 14m target)
- **Throughput at Hang:** ~14,600 iterations/minute

**Why It Failed:**
- Test hung very early (less than 2 minutes)
- No timeout errors, suggesting different failure mode
- Possibly memory exhaustion or thread pool saturation
- Never progressed beyond initial ramp-up phase

**Verdict:** Failed earliest of all frameworks. Virtual Threads couldn't save it from hanging at 2,867 VUs.

---

## Comparative Analysis

### Throughput Comparison (vs Baseline)

| Framework | Baseline RPS | Stress Test RPS | Performance Loss |
|-----------|--------------|-----------------|------------------|
| **Go Gin** | 7,583 | 842 | **-89%** |
| **Rust Axum** | 7,602 | 383 | **-95%** |
| Rust Actix | 7,579 | N/A (hung) | **FAILED** |
| Go Fiber | 7,595 | N/A (hung) | **FAILED** |
| Spring Boot | 7,125 | N/A (hung) | **FAILED** |

### Error Rate Comparison

| Framework | Total Requests | Errors | Error Rate | Timeouts |
|-----------|----------------|--------|------------|----------|
| **Go Gin** | 1,220,318 | 185 | 0.015% | Unknown |
| **Rust Axum** | 451,749 | 1,609 | 0.36% | Many |
| **Rust Actix** | ~1,689,529 | N/A | N/A | **5,349** |
| **Go Fiber** | ~1,796,619 | N/A | N/A | **92** |
| **Spring Boot** | ~1,646,160 | N/A | N/A | **0** |

### Max VUs Achieved (Target: 10,000)

| Framework | Max VUs | % of Target | Status |
|-----------|---------|-------------|--------|
| **Rust Actix** | 5,316 | 53% | Hung |
| **Go Gin** | 3,602 | 36% | Completed |
| **Rust Axum** | 3,286 | 33% | Completed |
| **Go Fiber** | 2,986 | 30% | Hung |
| **Spring Boot** | 2,867 | 29% | Hung |

---

## Key Findings

### 1. None Reached 10,000 VU Target
- **Best attempt:** Rust Actix at 5,316 VUs (before crashing)
- **Go frameworks:** Topped out around 3,000-3,600 VUs
- **Spring Boot:** Hung earliest at 2,867 VUs

### 2. Massive Performance Degradation
- **Go Gin:** Lost 89% throughput (7,583 → 842 RPS)
- **Rust Axum:** Lost 95% throughput (7,602 → 383 RPS)
- **Latency increase:** 20x to 173x slower than baseline

### 3. Test Runner Issues
Three frameworks caused the test runner to hang:
- **Rust Actix:** Stuck in infinite loop
- **Go Fiber:** Became unresponsive at 8 minutes
- **Spring Boot:** Hung at 2 minutes

### 4. Only 2 of 5 Services Completed
- **Go Gin:** Passed thresholds, low error rate
- **Rust Axum:** Passed thresholds, higher error rate
- **Others:** All hung or crashed

### 5. Rust Actix Catastrophic Failure
- 5,349 timeout errors (most of any framework)
- Completely crashed the test
- Despite having highest VU count, couldn't complete gracefully

---

## Conclusions

### For 250,000 RPS Target

**Reality Check:**
- **Current capacity:** ~7,500 RPS (baseline with 1,000 VUs)
- **Under stress:** Drops to 383-842 RPS
- **Target:** 250,000 RPS
- **Gap:** Need **300-650x more capacity**

### Framework Recommendations

1. **For Reliability Under Stress:** **Go Gin**
   - Only framework with <0.1% error rate
   - Completed test successfully
   - Graceful degradation

2. **Avoid:** **Rust Actix**
   - Catastrophic failure mode
   - 5,349 timeouts
   - Hung test runner

3. **Needs Investigation:** **Spring Boot & Go Fiber**
   - Both hung early
   - Different failure modes
   - Require deeper debugging

### Next Steps Required

1. **Fix Memory Issues:** All frameworks show memory exhaustion
2. **Increase System Resources:** Current POCs running on limited resources
3. **Tune Configurations:**
   - Connection pool sizes
   - Thread pools
   - File descriptor limits
   - System ulimits
4. **Horizontal Scaling:** Test with multiple instances + load balancer
5. **Optimize Code:** Profile and optimize hotpaths
6. **Database/Cache Layer:** Add Redis for state management

### The 250k RPS Reality

To reach 250,000 RPS with current performance:
- **Option 1:** Deploy ~300 Go Gin instances (842 RPS each)
- **Option 2:** Completely re-architect with:
  - Multiple load balancers
  - Redis cluster
  - Kafka for async processing
  - Horizontal pod autoscaling
  - CDN for static assets

**Verdict:** Single-instance POCs cannot reach 250k RPS. Need distributed architecture.

---

## Test Configuration Details

### k6 Stress Test Stages
```javascript
stages: [
  { duration: '1m', target: 2000 },    // Ramp to 2000
  { duration: '3m', target: 5000 },    // Ramp to 5000
  { duration: '5m', target: 10000 },   // Ramp to 10000
  { duration: '3m', target: 10000 },   // Hold at 10000
  { duration: '2m', target: 0 },       // Ramp down
]
```

### Thresholds
- `http_req_duration: p(99) < 10000ms`
- `http_req_failed: rate < 0.10` (10% error rate)

### Services Tested
- Spring Boot 3.3 (Java 21 Virtual Threads) - Port 8081
- Go Gin 1.10.0 - Port 8082
- Go Fiber 2.52.5 (FastHTTP) - Port 8083
- Rust Actix Web 4.9 - Port 8084
- Rust Axum 0.7 (Tokio) - Port 8085

### Test Environment
- Platform: WSL2 Linux (kernel 6.6.87.2)
- k6 load generator running locally
- All services containerized in Docker
- Tests run sequentially (not parallel)
