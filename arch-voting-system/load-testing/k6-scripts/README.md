# K6 Load Testing Scripts

## Overview
Collection of k6 load testing scripts for benchmarking voting system POCs.

## Scripts

### 1. baseline.js
Quick baseline test with 100 VUs for 30 seconds.
- Tests both health and vote endpoints
- Validates response structure
- Low load for initial validation

**Usage:**
```bash
k6 run baseline.js
k6 run -e SERVICE_URL=http://localhost:8080 baseline.js
```

### 2. constant-load.js
Constant load test ramping up to 1000 VUs.
- 30s ramp-up, 2m sustained, 30s ramp-down
- Custom metrics for vote operations
- Strict thresholds (p95 < 500ms, p99 < 1000ms)

**Usage:**
```bash
k6 run constant-load.js
k6 run -e SERVICE_URL=http://localhost:8080 constant-load.js
```

### 3. spike-test.js
Spike test simulating sudden traffic bursts.
- Spikes from 100 to 5000 VUs
- Tests recovery and stability
- More relaxed thresholds for spike scenarios

**Usage:**
```bash
k6 run spike-test.js
k6 run -e SERVICE_URL=http://localhost:8080 spike-test.js
```

### 4. stress-test.js
Extended stress test ramping up to 10,000 VUs.
- Finds breaking point of the system
- 14-minute duration
- Tests sustained high load

**Usage:**
```bash
k6 run stress-test.js
k6 run -e SERVICE_URL=http://localhost:8080 stress-test.js
```

## Environment Variables

- `SERVICE_URL`: URL of the service to test (default: http://localhost:8080)

## Output Options

### Console output with summary
```bash
k6 run baseline.js
```

### JSON output for analysis
```bash
k6 run --out json=results.json baseline.js
```

### InfluxDB output for visualization
```bash
k6 run --out influxdb=http://localhost:8086/k6 baseline.js
```

## Metrics

All scripts track:
- `http_req_duration`: Request duration
- `http_req_failed`: Failed request rate
- `vote_success_rate`: Custom metric for vote success
- `vote_errors`: Custom counter for errors

## Thresholds

Each script defines specific thresholds for pass/fail criteria:
- Response time percentiles (p95, p99)
- Error rates
- Success rates
