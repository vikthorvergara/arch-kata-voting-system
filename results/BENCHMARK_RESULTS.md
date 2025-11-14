# Load Testing Results

Performance comparison of 5 web frameworks for a voting system API.

## Test Summary

**Date:** November 13-14, 2025
**Environment:** WSL2, 6 CPU cores, 7.7GB RAM
**Load Generator:** k6
**Services:** Spring Boot, Go Gin, Go Fiber, Rust Actix, Rust Axum

All services implement identical POST /api/vote endpoint with in-memory atomic counters.

## Baseline Test Results (100 VUs, 30s)

Low load performance test to establish baseline metrics.

| Framework | RPS | Avg Latency | P95 Latency | P99 Latency | Error Rate |
|-----------|-----|-------------|-------------|-------------|------------|
| Rust Axum | 199.8 | 0.49ms | 0.87ms | 1.05ms | 0% |
| Go Fiber | 199.7 | 0.47ms | 0.89ms | 1.11ms | 0% |
| Rust Actix | 199.7 | 0.48ms | 0.97ms | 1.26ms | 0% |
| Go Gin | 199.7 | 0.48ms | 0.90ms | 1.13ms | 0% |
| Spring Boot | 199.5 | 0.48ms | 0.93ms | 1.27ms | 0% |

**Key Findings:**
- All frameworks perform similarly under low load
- Sub-millisecond latency across the board
- No errors or failures
- Throughput limited by test configuration (100 VUs)

## Constant Load Test Results (1000 VUs, 2min)

Sustained load test to measure realistic performance.

| Framework | RPS | Avg Latency | P95 Latency | P99 Latency | Max Latency | Error Rate |
|-----------|-----|-------------|-------------|-------------|-------------|------------|
| **Rust Axum** | **7,602** | 6.70ms | 27.39ms | 67.25ms | 117ms | 0% |
| Go Fiber | 7,595 | 6.85ms | 26.69ms | 65.52ms | 121ms | 0% |
| Go Gin | 7,583 | 7.17ms | 28.94ms | 69.78ms | 120ms | 0% |
| Rust Actix | 7,579 | 6.60ms | 27.20ms | 66.54ms | 112ms | 0% |
| Spring Boot | 7,125 | 13.59ms | 43.89ms | 94.79ms | 191ms | 0% |

**Key Findings:**
- Rust Axum achieves highest throughput: 7,602 RPS
- Spring Boot 2x slower in latency (13.59ms vs 6.60ms)
- All Go/Rust frameworks perform similarly
- Zero errors across all frameworks
- All frameworks handle 1000 VUs comfortably

**Performance Gap:**
- Target: 250,000 RPS
- Achieved: 7,602 RPS (best case)
- **Gap: 33x more capacity needed**

## Stress Test Results (10,000 VU Target, 14min)

Extreme load test to find breaking points.

### Completed Tests

| Framework | Status | RPS | Avg Latency | P99 Latency | Error Rate | Max VUs | Test Duration |
|-----------|--------|-----|-------------|-------------|------------|---------|---------------|
| **Go Gin** | ✅ Complete | 842 | 148ms | 318ms | 0.015% | 3,602 | 24m09s |
| Rust Axum | ✅ Complete | 383 | 1,159ms | 909ms | 0.36% | 3,286 | 19m38s |

### Failed Tests

| Framework | Status | Max VUs | Iterations | Timeouts | Failure Point |
|-----------|--------|---------|------------|----------|---------------|
| Rust Actix | ❌ Hung | 5,316 | 1,689,529 | 5,349 | 19m39s - test hung indefinitely |
| Go Fiber | ❌ Hung | 2,986 | 1,796,619 | 92 | 8m03s - service became unresponsive |
| Spring Boot | ❌ Hung | 2,867 | 1,646,160 | 0 | 1m52s - hung during ramp-up |

**Key Findings:**
- **Only 2 of 5 frameworks completed the stress test**
- None reached 10,000 VU target
- Go Gin most resilient (0.015% error rate)
- Rust Axum severe performance collapse (95% throughput loss)
- Rust Actix had catastrophic failure (5,349 timeouts)

### Performance Degradation Under Stress

| Framework | Normal RPS | Stress RPS | Loss | Latency Increase |
|-----------|------------|------------|------|------------------|
| Go Gin | 7,583 | 842 | -89% | 20x slower |
| Rust Axum | 7,602 | 383 | -95% | 173x slower |

### Virtual Users Achieved

```
Target: 10,000 VUs

Rust Actix:   5,316 VUs (53%) - CRASHED
Go Gin:       3,602 VUs (36%) - Completed
Rust Axum:    3,286 VUs (33%) - Completed
Go Fiber:     2,986 VUs (30%) - HUNG
Spring Boot:  2,867 VUs (29%) - HUNG
```

## Overall Rankings

### Best for Constant Load (1000 VUs)
1. **Rust Axum** - 7,602 RPS, 6.70ms avg latency
2. Go Fiber - 7,595 RPS, 6.85ms avg latency
3. Go Gin - 7,583 RPS, 7.17ms avg latency

### Best Under Stress (10,000 VU target)
1. **Go Gin** - Only framework with <0.1% error rate
2. Rust Axum - Completed but severe degradation
3. Others - All failed/hung

### Recommended Framework
**Go Gin** - Best balance of performance and reliability under stress.

## Conclusions

### What We Learned

1. **Under Normal Load (1000 VUs):** All frameworks perform well
   - 7,500+ RPS achievable
   - Sub-50ms P95 latency
   - Zero errors

2. **Under Stress (10,000 VUs):** Most frameworks fail
   - 60% failed to complete (3 of 5)
   - Performance collapses by 89-95%
   - Only Go Gin maintained low error rate

3. **For 250k RPS Target:** Need distributed architecture
   - Current best: 7,602 RPS (single instance)
   - Target: 250,000 RPS
   - **Requires 33+ instances minimum**

### Limitations

These tests have significant limitations:
- All services on single machine (shared resources)
- No database or caching layer
- WSL2 virtualization overhead
- Default configurations (no tuning)
- Spring Boot limited to 512MB heap
- Load generator on same machine as services

### Production Recommendations

To reach 250k RPS:
1. **Horizontal Scaling:** Deploy 30-50 instances behind load balancers
2. **Distributed Caching:** Add Redis cluster
3. **Async Processing:** Use Kafka for vote processing
4. **Database:** PostgreSQL with read replicas
5. **CDN:** Cloudflare or similar for static assets
6. **Auto-scaling:** Kubernetes HPA based on CPU/RPS
7. **Optimization:** Tune connection pools, thread pools, JVM settings

Single-instance POCs prove frameworks work but cannot meet production requirements.

## Test Data Location

- **Baseline + Constant Load:** `results/benchmark_20251113_231617/`
- **Stress Tests:** `results/stress_test_20251114_001412/`
- **Methodology:** `results/BENCHMARK_METHODOLOGY.md`

## How to Reproduce

```bash
# Start services
cd load-testing
docker-compose up -d

# Run benchmark tests (baseline + constant load)
./benchmark.sh

# Run stress tests (optional - takes hours and may hang)
./run-stress-tests.sh

# View results
ls -lh ../results/
```

See `BENCHMARK_METHODOLOGY.md` for detailed test procedures and environment specifications.
