# Load Testing Environment

## Overview
Comprehensive load testing setup for comparing voting system POC performance across 5 different tech stacks.

## Architecture

### Services
All POCs run on port 8080 internally, mapped to different host ports:
- **Spring Boot**: `http://localhost:8081`
- **Go Gin**: `http://localhost:8082`
- **Go Fiber**: `http://localhost:8083`
- **Rust Actix**: `http://localhost:8084`
- **Rust Axum**: `http://localhost:8085`

## Quick Start

### 1. Build and Start All Services
```bash
cd load-testing
docker-compose up --build
```

### 2. Start in Background
```bash
docker-compose up -d
```

### 3. Check Service Health
```bash
# All services
docker-compose ps

# Individual health checks
curl http://localhost:8081/health  # Spring Boot
curl http://localhost:8082/health  # Go Gin
curl http://localhost:8083/health  # Go Fiber
curl http://localhost:8084/health  # Rust Actix
curl http://localhost:8085/health  # Rust Axum
```

### 4. View Logs
```bash
# All services
docker-compose logs -f

# Specific service
docker-compose logs -f spring-boot
docker-compose logs -f go-gin
```

### 5. Stop Services
```bash
docker-compose down
```

## Running Load Tests

### Prerequisites
Install k6: https://k6.io/docs/get-started/installation/

### Run Tests Against Each POC

#### Baseline Test
```bash
# Spring Boot
k6 run -e SERVICE_URL=http://localhost:8081 k6-scripts/baseline.js

# Go Gin
k6 run -e SERVICE_URL=http://localhost:8082 k6-scripts/baseline.js

# Go Fiber
k6 run -e SERVICE_URL=http://localhost:8083 k6-scripts/baseline.js

# Rust Actix
k6 run -e SERVICE_URL=http://localhost:8084 k6-scripts/baseline.js

# Rust Axum
k6 run -e SERVICE_URL=http://localhost:8085 k6-scripts/baseline.js
```

#### Constant Load Test
```bash
k6 run -e SERVICE_URL=http://localhost:8082 k6-scripts/constant-load.js
```

#### Spike Test
```bash
k6 run -e SERVICE_URL=http://localhost:8083 k6-scripts/spike-test.js
```

#### Stress Test
```bash
k6 run -e SERVICE_URL=http://localhost:8084 k6-scripts/stress-test.js
```

## Automated Benchmarking

Use the benchmark script to test all POCs automatically:

```bash
./benchmark.sh
```

This will:
1. Verify all services are running
2. Run baseline tests on each POC
3. Run constant load tests on each POC
4. Generate comparison report
5. Save results to `../results/` directory

## Resource Monitoring

### Docker Stats
```bash
docker stats
```

### Individual Container Resources
```bash
docker stats voting-spring-boot
docker stats voting-go-gin
docker stats voting-go-fiber
docker stats voting-rust-actix
docker stats voting-rust-axum
```

## Troubleshooting

### Service Won't Start
```bash
# Check logs
docker-compose logs <service-name>

# Rebuild specific service
docker-compose up --build <service-name>
```

### Port Conflicts
If ports 8081-8085 are already in use, modify `docker-compose.yml` to use different ports.

### Out of Memory
Adjust Docker resource limits in Docker Desktop settings or add resource constraints in `docker-compose.yml`.

## Performance Testing Best Practices

1. **Warm-up**: Always run a baseline test first to warm up the service
2. **Isolation**: Test one service at a time for accurate results
3. **Resources**: Ensure Docker has adequate CPU and memory allocated
4. **Network**: Run tests on the same machine to minimize network latency
5. **Comparison**: Use identical test parameters across all POCs

## Expected Performance Targets

Based on README analysis:
- **Spring Boot**: 250k-300k RPS
- **Go Gin**: 800k-1M+ RPS
- **Go Fiber**: 800k-1M+ RPS (often faster than Gin)
- **Rust Actix**: 100k+ RPS per core
- **Rust Axum**: 100k+ RPS per core

## Results Directory

Test results are saved to `../results/` with timestamps for comparison and analysis.
