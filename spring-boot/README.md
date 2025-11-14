# Spring Boot 4 Voting POC

## Overview
Minimal HTTP endpoint POC using Spring Boot 3.3 with Kotlin and Virtual Threads (Java 21).

## Features
- Virtual Threads enabled for improved I/O concurrency
- Atomic counter for vote tracking
- Minimal dependencies for maximum performance
- Production-ready Tomcat configuration

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
  "service": "spring-boot-voting-poc"
}
```

## Build & Run

### Local (requires Java 21)
```bash
./gradlew bootRun
```

### Docker
```bash
docker build -t voting-spring-boot .
docker run -p 8080:8080 voting-spring-boot
```

## Performance Target
250k-300k RPS (as per README analysis)
