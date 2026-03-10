# Arch Kata

## Problem
You must design a Realtime voting system with the following requirements:
1. Never loose Data
2. Be secure and prevent bots and bad actors
3. Handle 300M users
4. Handle peak of 250k RPS
5. Must ensure users vote only once
6. Should be Realtime

### Restrictions
- Serverless
- MongoDB
- On-Premise, Google Cloud, Azure
- OpenShift
- Mainframes
- Monolith Solutions

## Messages
Apache Flink integration with Kafka/MSK

## Cache
EC2 Redis vs ElastiCache cost analysis

## Tradeoff Comparison for Realtime Voting System Stack

For a realtime voting system handling 300M users and 250k RPS peaks, the best choices prioritize horizontal scalability, low latency, and write durability while avoiding disallowed technologies. Key tradeoffs center on throughput vs. complexity, consistency vs. availability, and simplicity vs. feature richness. Below, I compare top options for critical components, selecting based on your Spring Boot/Kotlin/AWS background for easier adoption.

## Messages

### Apache Flink vs. Kafka Streams

**Apache Flink**
- **Deployment**: Separate cluster (Kubernetes/AWS Managed Service)
- **Cost**: $800-1,600/month for managed service
- **Complexity**: Requires cluster management
- **Best for**: Complex event processing with cross-stream joins

**Kafka Streams**
- **Deployment**: Embedded in Spring Boot application
- **Cost**: No additional infrastructure
- **Complexity**: Simpler operational model
- **Best for**: Kafka-only pipelines with stateful aggregations

**Recommendation**: Kafka Streams. Embeds in Spring Boot apps, no separate cluster, lower cost.

### Kafka vs. RabbitMQ

**Apache Kafka**
- **Throughput**: Millions of messages/sec
- **Durability**: Built-in replication, no data loss
- **Complexity**: High setup complexity
- **Best for**: High write volume with replay requirements

**RabbitMQ**
- **Throughput**: 4k-10k messages/sec
- **Durability**: Optional persistence, risks data loss
- **Complexity**: Simpler operations
- **Best for**: Prototypes and moderate loads

**Recommendation**: Kafka. Handles 250k RPS, proven at scale.

### AWS Managed: MSK vs. SQS

**Apache Kafka on MSK**
- **Throughput**: 2M+ messages/sec sustained
- **Replay**: Configurable retention for message replay
- **Cost**: Fixed ~$6,200/month for 6 brokers
- **Complexity**: Moderate, manage partitions and consumers
- **Best for**: Sustained high throughput with multiple consumers

**AWS SQS**
- **Throughput**: Nearly unlimited, auto-scaling
- **Replay**: No replay, 14-day max retention
- **Cost**: Pay-per-request, ~$259k/month at 250k RPS
- **Complexity**: Zero infrastructure management
- **Best for**: Variable traffic, simple task distribution

**Recommendation**: MSK. Fixed cost and replay capability justify complexity for 250k RPS.

## Cache

### Redis vs. Memcached

**Redis**
- **Data Structures**: Sets, hashes, pub/sub for deduplication
- **Persistence**: Configurable, no data loss
- **Complexity**: More features, more config
- **Best for**: Vote deduplication with persistence

**Memcached**
- **Data Structures**: Strings only
- **Persistence**: Volatile, data loss on restart
- **Complexity**: Simpler setup
- **Best for**: Pure caching

**Recommendation**: Redis. Persistence required for vote deduplication.

### AWS Managed: ElastiCache for Redis vs. Memcached

**ElastiCache for Redis**
- **Data Structures**: Sets, counters, pub/sub for deduplication
- **Persistence**: RDB/AOF snapshots, no data loss
- **High Availability**: Multi-AZ, 99.99% SLA
- **Cost**: ~$7,500-12,500/month for 30-50 nodes
- **Best for**: Vote deduplication and atomic counters

**ElastiCache for Memcached**
- **Data Structures**: Strings only
- **Persistence**: Fully volatile, data loss on restart
- **High Availability**: No built-in failover
- **Cost**: Similar pricing, simpler deployment
- **Best for**: Pure caching where loss is acceptable

**Recommendation**: ElastiCache Redis. Persistence and atomic operations required for vote integrity.

### EC2 Redis vs. ElastiCache Redis

**Self-Managed Redis on EC2**
- **Cost**: ~$8,700-14,500/month infrastructure + ops engineer
- **Operations**: Manual patching, backups, failover, 24/7 on-call
- **High Availability**: Manual setup, no guaranteed SLA
- **Best for**: Existing Redis ops team

**AWS ElastiCache for Redis**
- **Cost**: ~$13,200-22,000/month fully managed
- **Operations**: AWS handles all infrastructure management
- **High Availability**: Automatic failover, Multi-AZ, 99.99% SLA
- **Best for**: Production requiring high uptime

**Recommendation**: ElastiCache. 99.99% SLA justifies premium for mission-critical vote deduplication.

## API Gateway

### NGINX vs. Kong

**NGINX Plus**
- **Latency**: Consistent <13ms at peaks
- **Features**: Built-in rate limiting and JWT
- **Complexity**: Simpler configuration
- **Best for**: High-performance APIs

**Kong**
- **Latency**: Spikes at high load (3x at p99.99)
- **Features**: Rich plugin ecosystem
- **Complexity**: Plugin management overhead
- **Best for**: Custom routing requirements

**Recommendation**: NGINX Plus. Consistent low latency for 250k RPS.
