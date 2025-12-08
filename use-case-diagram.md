# Voting System API - Use Case Diagram

```mermaid
graph TB
    subgraph Actors
        Voter[👤 Voter]
        Admin[👨‍💼 System Administrator]
        Analytics[📊 Analytics System]
        Bot[🤖 Bot/Bad Actor]
    end

    subgraph "Voting System API"
        UC1[Submit Vote<br/>POST /api/vote]
        UC2[Verify Single Vote<br/>per User]
        UC3[Check System Health<br/>GET /health]
        UC4[Detect Bot Activity]
        UC5[Rate Limit Requests]
        UC6[Retrieve Vote Count]
        UC7[Monitor System<br/>Performance]
        UC8[Handle Peak Load<br/>250k RPS]
    end

    Voter -->|POST /api/vote| UC1
    UC1 -.includes.-> UC2
    UC1 -.includes.-> UC4
    UC1 -.includes.-> UC5
    UC1 -->|returns totalVotes| UC6

    Bot -.attempts.-> UC1
    UC4 -.blocks.-> Bot
    UC5 -.blocks.-> Bot

    Admin -->|GET /health| UC3
    Admin --> UC7

    Analytics --> UC6

    UC1 -.extends.-> UC8
    UC3 -.includes.-> UC7

    style Bot fill:#ff6b6b
    style UC4 fill:#ffd93d
    style UC5 fill:#ffd93d
    style UC8 fill:#6bcf7f
```

## Primary Use Cases

### 1. Submit Vote
- **Actor**: Voter
- **Endpoint**: `POST /api/vote`
- **Request**:
  ```json
  {
    "userId": "user123",
    "candidateId": "candidate456"
  }
  ```
- **Response**:
  ```json
  {
    "success": true,
    "message": "Vote recorded successfully",
    "totalVotes": 42
  }
  ```
- **Requirements**:
  - Handle 250k RPS peaks
  - Support 300M users
  - Never lose data

### 2. Verify Single Vote per User
- **Type**: Security constraint (included in Submit Vote)
- **Mechanism**: Redis cache for deduplication
- **Requirement**: Each user can vote only once

### 3. Check System Health
- **Actor**: System Administrator
- **Endpoint**: `GET /health`
- **Response**:
  ```json
  {
    "status": "UP",
    "service": "service-name-voting-poc"
  }
  ```

### 4. Detect Bot Activity
- **Type**: Security constraint (included in Submit Vote)
- **Purpose**: Prevent bots and bad actors
- **Mechanisms**:
  - Rate limiting
  - Request validation
  - Pattern detection

### 5. Rate Limit Requests
- **Type**: Security/Performance constraint
- **Purpose**: Protect against abuse and ensure fair resource distribution
- **Implementation**: API Gateway (NGINX Plus)

### 6. Retrieve Vote Count
- **Actor**: Analytics System, Voter (via response)
- **Type**: Real-time aggregation
- **Requirement**: Provide real-time vote counts

### 7. Monitor System Performance
- **Actor**: System Administrator
- **Type**: Operational monitoring
- **Metrics**:
  - RPS throughput
  - P95/P99 latency
  - Error rates
  - Resource usage

### 8. Handle Peak Load
- **Type**: Performance requirement (extends Submit Vote)
- **Requirement**: Process 250k requests per second
- **Implementation**: Horizontal scaling with Kafka/MSK and Redis

## System Boundaries

### Included in API:
- Vote submission and validation
- Health monitoring
- Real-time vote counting
- Bot detection and rate limiting

### External Dependencies:
- **Message Queue**: Kafka/MSK for vote processing
- **Cache**: Redis/ElastiCache for deduplication
- **API Gateway**: NGINX Plus for rate limiting and routing
- **Application**: Spring Boot/Go/Rust implementations

## Non-Functional Requirements

1. **Scalability**: Handle 300M users and 250k RPS
2. **Reliability**: Never lose vote data
3. **Security**: Prevent bots and ensure one vote per user
4. **Performance**: Real-time vote counting and results
5. **Availability**: High uptime with health monitoring
