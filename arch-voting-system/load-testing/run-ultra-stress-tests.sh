#!/bin/bash

# Create results directory
RESULT_DIR="../results/ultra_stress_test_$(date +%Y%m%d_%H%M%S)"
mkdir -p "$RESULT_DIR"

echo "=========================================="
echo "Running ULTRA Stress Tests (20,000 VUs)"
echo "Results will be saved to: $RESULT_DIR"
echo "=========================================="

# Array of services to test
declare -A services
services["spring-boot"]="http://localhost:8081"
services["go-gin"]="http://localhost:8082"
services["go-fiber"]="http://localhost:8083"
services["rust-actix"]="http://localhost:8084"
services["rust-axum"]="http://localhost:8085"

# Run ultra-stress test for each service
for service in "${!services[@]}"; do
    url="${services[$service]}"
    echo ""
    echo "=========================================="
    echo "Testing: $service at $url"
    echo "=========================================="

    SERVICE_URL="$url" k6 run \
        --out json="$RESULT_DIR/${service}_ultra_stress.json" \
        --summary-export="$RESULT_DIR/${service}_ultra_stress_summary.json" \
        k6-scripts/ultra-stress.js \
        2>&1 | tee "$RESULT_DIR/${service}_ultra_stress_output.txt"

    echo "$service ultra-stress test completed"
    echo ""

    # Wait a bit between tests to let system recover
    echo "Waiting 60 seconds before next test..."
    sleep 60
done

echo "=========================================="
echo "All ultra-stress tests completed!"
echo "Results saved in: $RESULT_DIR"
echo "=========================================="
