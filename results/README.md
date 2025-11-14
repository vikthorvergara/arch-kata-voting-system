# Benchmark Results

## Overview
This directory contains performance benchmark results for all voting system POCs.

**Quick Links:**
- **[Benchmark Results Summary](BENCHMARK_RESULTS.md)** - Performance comparison and analysis
- **[Test Methodology](BENCHMARK_METHODOLOGY.md)** - How tests were conducted and resources used

## Directory Structure

Each test run creates a timestamped subdirectory:
```
results/
├── benchmark_20251113_231617/          # Baseline and constant load tests
│   ├── spring_boot_baseline.json
│   ├── spring_boot_baseline_summary.json
│   ├── spring_boot_baseline_output.txt
│   ├── spring_boot_constant_load.json
│   ├── spring_boot_constant_load_summary.json
│   ├── spring_boot_constant_load_output.txt
│   ├── go_gin_*
│   ├── go_fiber_*
│   ├── rust_actix_*
│   ├── rust_axum_*
│   └── comparison_report.md
├── stress_test_20251114_001412/        # Stress test results (10k VUs)
│   ├── go_gin_stress.json
│   ├── go_gin_stress_summary.json
│   ├── go_gin_stress_output.txt
│   ├── rust_axum_stress.json
│   ├── rust_axum_stress_summary.json
│   ├── rust_axum_stress_output.txt
│   ├── (other services - incomplete/hung)
│   └── ANALYSIS.md
├── BENCHMARK_METHODOLOGY.md            # Test procedures and environment
├── BENCHMARK_RESULTS.md                # Results summary and analysis
└── README.md (this file)
```

## File Types

### JSON Files
- **\*_baseline.json**: Raw k6 metrics for baseline test (100 VUs, 30s)
- **\*_constant_load.json**: Raw k6 metrics for constant load test (1000 VUs)
- **\*_summary.json**: Aggregated summary metrics

### Text Files
- **\*_output.txt**: Console output from k6 test execution

### Reports
- **comparison_report.md**: Comparative analysis across all POCs

## Analyzing Results

### Using jq (JSON processor)

Install jq: https://stedolan.github.io/jq/download/

#### Get Average Request Duration
```bash
jq '.metrics.http_req_duration.values.avg' spring_boot_baseline_summary.json
```

#### Get Requests Per Second
```bash
jq '.metrics.http_reqs.values.rate' go_gin_baseline_summary.json
```

#### Get P95 Latency
```bash
jq '.metrics.http_req_duration.values["p(95)"]' rust_actix_baseline_summary.json
```

#### Get P99 Latency
```bash
jq '.metrics.http_req_duration.values["p(99)"]' rust_axum_baseline_summary.json
```

#### Get Error Rate
```bash
jq '.metrics.http_req_failed.values.rate' go_fiber_baseline_summary.json
```

#### Get All Key Metrics
```bash
jq '{
  rps: .metrics.http_reqs.values.rate,
  avg_duration: .metrics.http_req_duration.values.avg,
  p95: .metrics.http_req_duration.values["p(95)"],
  p99: .metrics.http_req_duration.values["p(99)"],
  error_rate: .metrics.http_req_failed.values.rate
}' spring_boot_baseline_summary.json
```

### Compare All POCs

Create a comparison script:
```bash
#!/bin/bash
BENCHMARK_DIR="benchmark_20250113_143022"

echo "Service,RPS,Avg Duration (ms),P95 (ms),P99 (ms),Error Rate"

for service in spring_boot go_gin go_fiber rust_actix rust_axum; do
    file="$BENCHMARK_DIR/${service}_baseline_summary.json"
    if [ -f "$file" ]; then
        rps=$(jq -r '.metrics.http_reqs.values.rate' "$file")
        avg=$(jq -r '.metrics.http_req_duration.values.avg' "$file")
        p95=$(jq -r '.metrics.http_req_duration.values["p(95)"]' "$file")
        p99=$(jq -r '.metrics.http_req_duration.values["p(99)"]' "$file")
        err=$(jq -r '.metrics.http_req_failed.values.rate' "$file")
        echo "$service,$rps,$avg,$p95,$p99,$err"
    fi
done
```

## Key Metrics to Compare

1. **Requests Per Second (RPS)**: Higher is better
2. **Average Latency**: Lower is better
3. **P95 Latency**: 95% of requests completed within this time
4. **P99 Latency**: 99% of requests completed within this time
5. **Error Rate**: Lower is better (target: < 1%)

## Performance Targets

From the main README analysis:

| Framework | Expected RPS | Notes |
|-----------|--------------|-------|
| Spring Boot 4 | 250k-300k | With Virtual Threads |
| Go Gin | 800k-1M+ | High concurrency |
| Go Fiber | 800k-1M+ | FastHTTP-based |
| Rust Actix | 100k+ per core | Actor model |
| Rust Axum | 100k+ per core | Tokio-based |

## Best Practices

1. **Run Multiple Tests**: Execute benchmark suite 3-5 times and average results
2. **Warm-Up**: First run may show lower performance due to JIT compilation, container warm-up, etc.
3. **Resource Monitoring**: Use `docker stats` during tests to monitor CPU and memory
4. **Isolation**: Test one service at a time for most accurate results
5. **Consistency**: Use same hardware and environment for all tests

## Example Analysis

After running benchmarks, you might see results like:

| Framework | RPS | P95 Latency | P99 Latency | Winner? |
|-----------|-----|-------------|-------------|---------|
| Spring Boot | 45k | 180ms | 250ms | Good for familiar stack |
| Go Gin | 120k | 85ms | 120ms | High throughput |
| Go Fiber | 135k | 75ms | 105ms | **Best throughput** |
| Rust Actix | 95k | 55ms | 80ms | Good latency |
| Rust Axum | 100k | 50ms | 75ms | **Best latency** |

*Note: Actual results will vary based on hardware, configuration, and test parameters*

## Troubleshooting

### Missing Metrics
If summary files are missing metrics, check the raw JSON files or output.txt for errors.

### High Error Rates
- Check service logs: `docker-compose logs <service>`
- Verify service health: `curl http://localhost:808X/health`
- Reduce load in k6 scripts

### Inconsistent Results
- Ensure no other processes are using significant resources
- Run tests multiple times and average
- Increase test duration for more stable metrics
