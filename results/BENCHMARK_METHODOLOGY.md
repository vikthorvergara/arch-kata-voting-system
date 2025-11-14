# Load Testing Methodology

This document describes how the performance tests were conducted, what resources were available, and how to reproduce the results.

## Test Environment

### Hardware Resources
- **CPU:** 6 cores (x86_64)
- **RAM:** 7.7 GB total (4.7 GB available during tests)
- **Swap:** 2 GB
- **Platform:** WSL2 (Windows Subsystem for Linux 2)
- **Kernel:** Linux 6.6.87.2-microsoft-standard-WSL2

### Container Resources
All services ran in Docker containers with the following resource allocations:

- **Spring Boot:** 512 MB max heap (-Xmx512m), 256 MB initial heap (-Xms256m)
- **Go Gin:** No explicit limits (shares host resources)
- **Go Fiber:** No explicit limits (shares host resources)
- **Rust Actix:** No explicit limits (shares host resources)
- **Rust Axum:** No explicit limits (shares host resources)

### Network Configuration
- All containers connected via Docker bridge network
- k6 load generator ran on host machine
- Services exposed on localhost ports 8081-8085

## Test Scenarios

### 1. Baseline Test
**Purpose:** Establish baseline performance with low load

**Configuration:**
- Virtual Users (VUs): 100
- Duration: 30 seconds
- Ramp-up: None (constant load)

**Script:** `load-testing/k6-scripts/baseline.js`

### 2. Constant Load Test
**Purpose:** Measure sustained performance under realistic load

**Configuration:**
- Virtual Users: Ramps from 0 to 1000
- Duration: 2 minutes
- Stages:
  - 30s: ramp to 500 VUs
  - 1m: hold at 1000 VUs
  - 30s: ramp down to 0

**Script:** `load-testing/k6-scripts/constant-load.js`

### 3. Stress Test
**Purpose:** Find breaking points and maximum capacity

**Configuration:**
- Virtual Users: Ramps to 10,000 VUs
- Duration: 14 minutes
- Stages:
  - 1m: ramp to 2,000 VUs
  - 3m: ramp to 5,000 VUs
  - 5m: ramp to 10,000 VUs
  - 3m: hold at 10,000 VUs
  - 2m: ramp down to 0

**Script:** `load-testing/k6-scripts/stress-test.js`

## Test Implementation

### Services Under Test
All services implement the same minimal HTTP API:

**Endpoint:** `POST /api/vote`

**Request Body:**
```json
{
  "userId": "user-123",
  "candidateId": "candidate-5"
}
```

**Response:**
```json
{
  "success": true,
  "message": "Vote recorded successfully",
  "totalVotes": 12345
}
```

### Implementations
1. **Spring Boot 3.3** - Java 21 with Virtual Threads (Project Loom)
2. **Go Gin 1.10.0** - Go web framework with goroutines
3. **Go Fiber 2.52.5** - FastHTTP-based zero-allocation framework
4. **Rust Actix Web 4.9** - Actor-based async framework
5. **Rust Axum 0.7** - Tokio-based async framework

All implementations use atomic counters (AtomicLong/AtomicI64/atomic.Int64) for thread-safe vote counting.

## How Tests Were Run

### 1. Service Deployment
```bash
cd load-testing
docker-compose build
docker-compose up -d
```

### 2. Benchmark Execution
```bash
cd load-testing
./benchmark.sh
```

The benchmark script:
1. Runs baseline test on each service (100 VUs, 30s)
2. Waits 10 seconds between tests
3. Runs constant load test on each service (1000 VUs, 2min)
4. Waits 10 seconds between tests
5. Generates JSON summaries and text output for each test

### 3. Stress Test Execution
```bash
cd load-testing
./run-stress-tests.sh
```

The stress test script:
1. Runs stress test on each service sequentially (10,000 VU target, 14min)
2. Waits 30 seconds between tests for system recovery
3. Generates JSON summaries and text output

### 4. Results Collection
Results are stored in timestamped directories:
- `results/benchmark_YYYYMMDD_HHMMSS/` - Baseline and constant load results
- `results/stress_test_YYYYMMDD_HHMMSS/` - Stress test results

Each test generates:
- `*_summary.json` - Aggregated metrics (RPS, latency percentiles, error rates)
- `*_output.txt` - Complete k6 console output
- `*.json` - Raw request-level data (large files, 1-5GB each)

## Test Metrics

### Primary Metrics
- **RPS (Requests Per Second):** Total throughput
- **Error Rate:** Percentage of failed requests
- **Latency Percentiles:**
  - P50 (Median)
  - P90 (90th percentile)
  - P95 (95th percentile)
  - P99 (99th percentile)
- **Max VUs:** Maximum virtual users achieved

### Custom Metrics
- `vote_success_rate` - Percentage of successful vote operations
- `vote_errors` - Count of failed vote operations
- `vote_duration` - Vote-specific latency measurements

## Test Limitations

### Environment Constraints
1. **Single Host:** All services and load generator on same machine
2. **Shared Resources:** Services compete for CPU/memory
3. **WSL2 Overhead:** Virtual machine layer adds latency
4. **No Database:** In-memory atomic counters only (unrealistic for production)
5. **No Caching:** No Redis or distributed cache layer
6. **No Load Balancing:** Single instance per framework

### Test Constraints
1. **Sequential Testing:** Services tested one at a time (not parallel)
2. **No Warmup Period:** Tests start immediately
3. **Default Configurations:** No tuning of connection pools, thread pools, etc.
4. **Memory Limits:** Spring Boot limited to 512MB heap
5. **Network Localhost:** No actual network latency

### Known Issues During Testing
1. **Memory Exhaustion:** k6 ran out of memory writing large JSON files during stress tests
2. **Test Hangs:** 3 of 5 services hung during stress testing (Spring Boot, Go Fiber, Rust Actix)
3. **Timeout Errors:** Massive timeout errors under stress (5,349 for Rust Actix)

## Reproducibility

### Prerequisites
- Docker and Docker Compose
- k6 load testing tool
- 8GB+ RAM recommended
- 50GB+ disk space for results

### Running Tests
```bash
# 1. Clone repository
git clone <repository-url>
cd arch-kata-voting-system

# 2. Build and start services
cd load-testing
docker-compose build
docker-compose up -d

# 3. Verify services are healthy
docker-compose ps

# 4. Run baseline and constant load tests
./benchmark.sh

# 5. Run stress tests (optional - takes hours)
./run-stress-tests.sh

# 6. View results
ls -lh ../results/
```

### Expected Results
- **Baseline (100 VUs):** ~200 RPS per service, <1ms latency, 0% errors
- **Constant Load (1000 VUs):** ~7,500 RPS per service, <50ms P95 latency, 0% errors
- **Stress Test (10,000 VUs):** Most services will fail or hang; only Go Gin and Rust Axum complete

## Target vs. Actual Performance

### Project Requirements
- **Users:** 300 million registered voters
- **Peak Load:** 250,000 requests per second
- **Availability:** 99.9% uptime

### Test Results Summary
- **Best Throughput (Constant Load):** 7,602 RPS (Rust Axum)
- **Best Under Stress:** 842 RPS (Go Gin)
- **Gap to Target:** 297x more capacity needed

**Conclusion:** Single-instance deployments cannot meet 250k RPS requirement. Distributed architecture with horizontal scaling required.

## Analysis Tools

### View Summary
```bash
# Quick summary of a test
cat results/benchmark_*/go_gin_constant_load_summary.json | jq '.metrics.http_reqs.rate'
```

### Count Errors
```bash
# Count timeout errors in stress test
grep -c "timeout" results/stress_test_*/rust_actix_stress_output.txt
```

### Compare All Services
```bash
# Use the analysis script
cd load-testing
./analyze-stress-results.sh ../results/stress_test_YYYYMMDD_HHMMSS/
```

## References

- **k6 Documentation:** https://k6.io/docs/
- **Docker Compose:** https://docs.docker.com/compose/
- **Project Guide:** See `PROJECT_GUIDE.md` in repository root
