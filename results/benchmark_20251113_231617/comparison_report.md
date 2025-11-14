# Voting System POC Benchmark Results

**Date**: Thu Nov 13 23:35:10 -03 2025
**Test Run**: benchmark_20251113_231617

## Test Configuration

### Services Tested
- **spring-boot**: http://localhost:8081
- **go-gin**: http://localhost:8082
- **go-fiber**: http://localhost:8083
- **rust-actix**: http://localhost:8084
- **rust-axum**: http://localhost:8085

### Test Scenarios
1. **Baseline Test**: 100 VUs for 30 seconds
2. **Constant Load Test**: Ramp to 1000 VUs, sustain for 2 minutes

## Results Summary

### Baseline Test Results

| Service | Avg RPS | P95 Latency | P99 Latency | Error Rate |
|---------|---------|-------------|-------------|------------|
| spring-boot | See JSON | See JSON | See JSON | See JSON |
| go-gin | See JSON | See JSON | See JSON | See JSON |
| go-fiber | See JSON | See JSON | See JSON | See JSON |
| rust-actix | See JSON | See JSON | See JSON | See JSON |
| rust-axum | See JSON | See JSON | See JSON | See JSON |

### Constant Load Test Results

| Service | Avg RPS | P95 Latency | P99 Latency | Error Rate |
|---------|---------|-------------|-------------|------------|
| spring-boot | See JSON | See JSON | See JSON | See JSON |
| go-gin | See JSON | See JSON | See JSON | See JSON |
| go-fiber | See JSON | See JSON | See JSON | See JSON |
| rust-actix | See JSON | See JSON | See JSON | See JSON |
| rust-axum | See JSON | See JSON | See JSON | See JSON |

## Analysis

### Performance Targets (from README)
- **Spring Boot**: 250k-300k RPS
- **Go Gin**: 800k-1M+ RPS
- **Go Fiber**: 800k-1M+ RPS
- **Rust Actix**: 100k+ RPS per core
- **Rust Axum**: 100k+ RPS per core

### Detailed Results
For detailed metrics, see the JSON files in this directory:

- `go_fiber_baseline.json`
- `go_fiber_baseline_summary.json`
- `go_fiber_constant_load.json`
- `go_fiber_constant_load_summary.json`
- `go_gin_baseline.json`
- `go_gin_baseline_summary.json`
- `go_gin_constant_load.json`
- `go_gin_constant_load_summary.json`
- `rust_actix_baseline.json`
- `rust_actix_baseline_summary.json`
- `rust_actix_constant_load.json`
- `rust_actix_constant_load_summary.json`
- `rust_axum_baseline.json`
- `rust_axum_baseline_summary.json`
- `rust_axum_constant_load.json`
- `rust_axum_constant_load_summary.json`
- `spring_boot_baseline.json`
- `spring_boot_baseline_summary.json`
- `spring_boot_constant_load.json`
- `spring_boot_constant_load_summary.json`

## Recommendations

1. Review detailed JSON summaries for complete metrics
2. Analyze p95 and p99 latencies for user experience impact
3. Compare error rates under load
4. Consider resource utilization (CPU, memory) alongside RPS
5. Run spike and stress tests for deeper insights

## Files in This Directory

- **\*_baseline.json**: Raw k6 output for baseline tests
- **\*_baseline_summary.json**: Summary metrics for baseline tests
- **\*_constant_load.json**: Raw k6 output for constant load tests
- **\*_constant_load_summary.json**: Summary metrics for constant load tests
- **\*_output.txt**: Console output from k6 runs
- **comparison_report.md**: This file

## Next Steps

To analyze detailed metrics, use jq to parse JSON files:

```bash
# Example: Get average request duration
jq '.metrics.http_req_duration.values.avg' spring_boot_baseline_summary.json

# Example: Get requests per second
jq '.metrics.http_reqs.values.rate' go_gin_baseline_summary.json
```
