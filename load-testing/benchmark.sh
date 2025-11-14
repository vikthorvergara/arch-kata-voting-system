#!/bin/bash

set -e

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Configuration
RESULTS_DIR="../results"
TIMESTAMP=$(date +%Y%m%d_%H%M%S)
RESULTS_SUBDIR="$RESULTS_DIR/benchmark_$TIMESTAMP"
SERVICES=(
    "spring-boot:8081"
    "go-gin:8082"
    "go-fiber:8083"
    "rust-actix:8084"
    "rust-axum:8085"
)

echo -e "${BLUE}========================================${NC}"
echo -e "${BLUE}  Voting System POC Benchmark Suite${NC}"
echo -e "${BLUE}========================================${NC}"
echo ""

# Check if k6 is installed
if ! command -v k6 &> /dev/null; then
    echo -e "${RED}Error: k6 is not installed${NC}"
    echo "Please install k6: https://k6.io/docs/get-started/installation/"
    exit 1
fi

# Create results directory
mkdir -p "$RESULTS_SUBDIR"
echo -e "${GREEN}✓ Created results directory: $RESULTS_SUBDIR${NC}"

# Check if Docker Compose services are running
echo ""
echo -e "${YELLOW}Checking services...${NC}"
for service_config in "${SERVICES[@]}"; do
    IFS=':' read -r service port <<< "$service_config"
    if curl -sf "http://localhost:$port/health" > /dev/null 2>&1; then
        echo -e "${GREEN}✓ $service is healthy on port $port${NC}"
    else
        echo -e "${RED}✗ $service is not responding on port $port${NC}"
        echo -e "${YELLOW}Please start services with: docker-compose up -d${NC}"
        exit 1
    fi
done

# Function to run benchmark for a service
run_benchmark() {
    local service=$1
    local port=$2
    local test_script=$3
    local test_name=$4

    echo ""
    echo -e "${BLUE}Running $test_name for $service...${NC}"

    local service_clean=$(echo "$service" | sed 's/-/_/g')
    local output_file="$RESULTS_SUBDIR/${service_clean}_${test_name}.json"

    SERVICE_URL="http://localhost:$port" k6 run \
        --out json="$output_file" \
        --summary-export="$RESULTS_SUBDIR/${service_clean}_${test_name}_summary.json" \
        "k6-scripts/$test_script" 2>&1 | tee "$RESULTS_SUBDIR/${service_clean}_${test_name}_output.txt"

    if [ ${PIPESTATUS[0]} -eq 0 ]; then
        echo -e "${GREEN}✓ Completed $test_name for $service${NC}"
    else
        echo -e "${RED}✗ Failed $test_name for $service${NC}"
    fi
}

# Run baseline tests
echo ""
echo -e "${BLUE}========================================${NC}"
echo -e "${BLUE}  Phase 1: Baseline Tests${NC}"
echo -e "${BLUE}========================================${NC}"

for service_config in "${SERVICES[@]}"; do
    IFS=':' read -r service port <<< "$service_config"
    run_benchmark "$service" "$port" "baseline.js" "baseline"
    sleep 5  # Cool down between tests
done

# Run constant load tests
echo ""
echo -e "${BLUE}========================================${NC}"
echo -e "${BLUE}  Phase 2: Constant Load Tests${NC}"
echo -e "${BLUE}========================================${NC}"

for service_config in "${SERVICES[@]}"; do
    IFS=':' read -r service port <<< "$service_config"
    run_benchmark "$service" "$port" "constant-load.js" "constant_load"
    sleep 10  # Longer cool down for heavier tests
done

# Generate comparison report
echo ""
echo -e "${BLUE}========================================${NC}"
echo -e "${BLUE}  Generating Comparison Report${NC}"
echo -e "${BLUE}========================================${NC}"

REPORT_FILE="$RESULTS_SUBDIR/comparison_report.md"

cat > "$REPORT_FILE" << EOF
# Voting System POC Benchmark Results

**Date**: $(date)
**Test Run**: benchmark_$TIMESTAMP

## Test Configuration

### Services Tested
EOF

for service_config in "${SERVICES[@]}"; do
    IFS=':' read -r service port <<< "$service_config"
    echo "- **$service**: http://localhost:$port" >> "$REPORT_FILE"
done

cat >> "$REPORT_FILE" << EOF

### Test Scenarios
1. **Baseline Test**: 100 VUs for 30 seconds
2. **Constant Load Test**: Ramp to 1000 VUs, sustain for 2 minutes

## Results Summary

### Baseline Test Results
EOF

# Extract key metrics from baseline tests
echo "" >> "$REPORT_FILE"
echo "| Service | Avg RPS | P95 Latency | P99 Latency | Error Rate |" >> "$REPORT_FILE"
echo "|---------|---------|-------------|-------------|------------|" >> "$REPORT_FILE"

for service_config in "${SERVICES[@]}"; do
    IFS=':' read -r service port <<< "$service_config"
    service_clean=$(echo "$service" | sed 's/-/_/g')
    summary_file="$RESULTS_SUBDIR/${service_clean}_baseline_summary.json"

    if [ -f "$summary_file" ]; then
        # Extract metrics using basic text processing
        # Note: This is a simplified version. For production, use jq or similar JSON parser
        echo "| $service | See JSON | See JSON | See JSON | See JSON |" >> "$REPORT_FILE"
    else
        echo "| $service | N/A | N/A | N/A | N/A |" >> "$REPORT_FILE"
    fi
done

cat >> "$REPORT_FILE" << EOF

### Constant Load Test Results

| Service | Avg RPS | P95 Latency | P99 Latency | Error Rate |
|---------|---------|-------------|-------------|------------|
EOF

for service_config in "${SERVICES[@]}"; do
    IFS=':' read -r service port <<< "$service_config"
    service_clean=$(echo "$service" | sed 's/-/_/g')
    summary_file="$RESULTS_SUBDIR/${service_clean}_constant_load_summary.json"

    if [ -f "$summary_file" ]; then
        echo "| $service | See JSON | See JSON | See JSON | See JSON |" >> "$REPORT_FILE"
    else
        echo "| $service | N/A | N/A | N/A | N/A |" >> "$REPORT_FILE"
    fi
done

cat >> "$REPORT_FILE" << EOF

## Analysis

### Performance Targets (from README)
- **Spring Boot**: 250k-300k RPS
- **Go Gin**: 800k-1M+ RPS
- **Go Fiber**: 800k-1M+ RPS
- **Rust Actix**: 100k+ RPS per core
- **Rust Axum**: 100k+ RPS per core

### Detailed Results
For detailed metrics, see the JSON files in this directory:
EOF

echo "" >> "$REPORT_FILE"
for file in "$RESULTS_SUBDIR"/*.json; do
    if [ -f "$file" ]; then
        echo "- \`$(basename "$file")\`" >> "$REPORT_FILE"
    fi
done

cat >> "$REPORT_FILE" << EOF

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

\`\`\`bash
# Example: Get average request duration
jq '.metrics.http_req_duration.values.avg' spring_boot_baseline_summary.json

# Example: Get requests per second
jq '.metrics.http_reqs.values.rate' go_gin_baseline_summary.json
\`\`\`
EOF

echo -e "${GREEN}✓ Generated comparison report: $REPORT_FILE${NC}"

# Final summary
echo ""
echo -e "${BLUE}========================================${NC}"
echo -e "${BLUE}  Benchmark Complete!${NC}"
echo -e "${BLUE}========================================${NC}"
echo ""
echo -e "Results saved to: ${GREEN}$RESULTS_SUBDIR${NC}"
echo -e "Comparison report: ${GREEN}$REPORT_FILE${NC}"
echo ""
echo -e "${YELLOW}To view detailed metrics, use jq to parse JSON files:${NC}"
echo -e "  jq '.metrics' $RESULTS_SUBDIR/spring_boot_baseline_summary.json"
echo ""
