# Go Gin Voting POC

## Overview
Minimal HTTP endpoint POC using Go with the Gin web framework.

## Features
- Lightweight and fast routing
- Atomic counter for thread-safe vote tracking
- Release mode for production performance
- Minimal middleware overhead

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
  "service": "go-gin-voting-poc"
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
docker build -t voting-go-gin .
docker run -p 8080:8080 voting-go-gin
```

## Performance Target
800k-1M+ RPS (as per README analysis)
