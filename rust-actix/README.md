# Rust Actix Web Voting POC

## Overview
Minimal HTTP endpoint POC using Rust with the Actix Web framework.

## Features
- Actor-based concurrency model
- Atomic counter for thread-safe vote tracking
- Optimized release build with LTO
- Auto-scales workers based on CPU cores

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
  "service": "rust-actix-voting-poc"
}
```

## Build & Run

### Local (requires Rust 1.77+)
```bash
cargo build --release
cargo run --release
```

### Docker
```bash
docker build -t voting-rust-actix .
docker run -p 8080:8080 voting-rust-actix
```

## Performance Target
100k+ RPS per core (as per README analysis)
