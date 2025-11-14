# Rust Axum Voting POC

## Overview
Minimal HTTP endpoint POC using Rust with the Axum web framework (built on Tokio and Tower).

## Features
- Built on Tokio async runtime
- Type-safe routing with extractors
- Atomic counter for thread-safe vote tracking
- Optimized release build with LTO

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
  "service": "rust-axum-voting-poc"
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
docker build -t voting-rust-axum .
docker run -p 8080:8080 voting-rust-axum
```

## Performance Target
100k+ RPS per core (as per README analysis, often faster than Actix for specific workloads)
