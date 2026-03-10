# Go Fiber Voting POC

## Overview
Minimal HTTP endpoint POC using Go with the Fiber web framework (built on FastHTTP).

## Features
- Built on FastHTTP for maximum performance
- Atomic counter for thread-safe vote tracking
- Zero memory allocation routing
- Optimized for high-throughput scenarios

## Endpoints

### POST /api/vote
Records a vote.

**Request:**
```json
{
  "userId": "user123",
  "candidateId": "candidate456"
}
```

**Response:**
```json
{
  "success": true,
  "message": "Vote recorded successfully",
  "totalVotes": 1
}
```

### GET /health
Health check endpoint.

**Response:**
```json
{
  "status": "UP",
  "service": "go-fiber-voting-poc"
}
```

## Build & Run

### Local (requires Go 1.22+)
```bash
go mod download
go run main.go
```

### Docker
```bash
docker build -t voting-go-fiber .
docker run -p 8080:8080 voting-go-fiber
```

## Performance Target
800k-1M+ RPS (as per README analysis, often faster than Gin)
