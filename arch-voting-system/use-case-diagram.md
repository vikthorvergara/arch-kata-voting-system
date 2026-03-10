# Voting System API - Use Case Diagram

```mermaid
graph TB
    User[👤 User]

    subgraph "Voting System API"
        UC1[Register<br/>POST v1/auth/register]
        UC2[Login<br/>POST v1/auth/login]
        UC3[Create Event<br/>POST v1/event/create]
        UC4[Get Event Leaderboard<br/>GET v1/event/:event_id/leaderboard]
        UC5[Get Contestants List<br/>GET v1/contestants/:event_id]
        UC6[Submit Vote<br/>POST v1/vote/submit]
        UC7[Verify Vote Status<br/>GET v1/vote/:vote_id/status]
    end

    User --> UC1
    User --> UC2
    User --> UC3
    User --> UC4
    User --> UC5
    User --> UC6
    User --> UC7
```

## API Endpoints

### Authentication

#### Register
- **Method**: POST
- **Path**: `v1/auth/register`
- **Purpose**: Create new user account

#### Login
- **Method**: POST
- **Path**: `v1/auth/login`
- **Purpose**: Authenticate user and obtain session/token

### Event Management

#### Create Event
- **Method**: POST
- **Path**: `v1/event/create`
- **Purpose**: Create new voting event

#### Get Event Leaderboard
- **Method**: GET
- **Path**: `v1/event/{event_id}/leaderboard`
- **Purpose**: Retrieve real-time vote counts for event

#### Get Contestants List
- **Method**: GET
- **Path**: `v1/contestants/{event_id}`
- **Purpose**: List all contestants/candidates for an event

### Voting

#### Submit Vote
- **Method**: POST
- **Path**: `v1/vote/submit`
- **Purpose**: Submit vote for contestant in event

#### Verify Vote Status
- **Method**: GET
- **Path**: `v1/vote/{vote_id}/status`
- **Purpose**: Check status and confirmation of submitted vote
