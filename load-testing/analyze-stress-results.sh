#!/bin/bash

if [ -z "$1" ]; then
    echo "Usage: $0 <results_directory>"
    echo "Example: $0 ../results/stress_test_20251114_001412"
    exit 1
fi

RESULT_DIR="$1"

echo "=========================================="
echo "Stress Test Results Analysis"
echo "=========================================="
echo ""

for summary_file in "$RESULT_DIR"/*_stress_summary.json; do
    if [ ! -f "$summary_file" ]; then
        echo "No summary files found in $RESULT_DIR"
        exit 1
    fi

    service=$(basename "$summary_file" | sed 's/_stress_summary.json//')

    echo "=========================================="
    echo "SERVICE: $service"
    echo "=========================================="

    # Extract key metrics
    if command -v jq > /dev/null 2>&1; then
        total_requests=$(jq -r '.metrics.http_reqs.count // "N/A"' "$summary_file")
        rps=$(jq -r '.metrics.http_reqs.rate // "N/A"' "$summary_file")
        avg_duration=$(jq -r '.metrics.http_req_duration.avg // "N/A"' "$summary_file")
        p95_duration=$(jq -r '.metrics.http_req_duration["p(95)"] // "N/A"' "$summary_file")
        p99_duration=$(jq -r '.metrics.http_req_duration["p(99)"] // "N/A"' "$summary_file")
        error_rate=$(jq -r '.metrics.http_req_failed.value // "N/A"' "$summary_file")
        max_vus=$(jq -r '.metrics.vus_max.value // "N/A"' "$summary_file")

        echo "  Total Requests: $total_requests"
        echo "  Requests/sec: $rps"
        echo "  Avg Duration: ${avg_duration}ms"
        echo "  P95 Duration: ${p95_duration}ms"
        echo "  P99 Duration: ${p99_duration}ms"
        echo "  Error Rate: ${error_rate}%"
        echo "  Max VUs: $max_vus"
    else
        echo "  (jq not available - reading raw file)"
        grep -A 5 '"http_reqs"' "$summary_file" | head -10
    fi

    # Check for timeouts in output file
    output_file="${summary_file%_summary.json}_output.txt"
    if [ -f "$output_file" ]; then
        timeout_count=$(grep -c "request timeout" "$output_file" 2>/dev/null || echo "0")
        memory_errors=$(grep -c "cannot allocate memory" "$output_file" 2>/dev/null || echo "0")
        echo "  Timeout Errors: $timeout_count"
        echo "  Memory Errors: $memory_errors"
    fi

    echo ""
done

echo "=========================================="
echo "Summary"
echo "=========================================="
echo ""
echo "Compare p95 latencies and error rates to identify the most resilient framework under stress."
echo ""
