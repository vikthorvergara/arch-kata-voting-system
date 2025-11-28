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

Stream processing frameworks that handle real-time vote analytics and aggregations on top of Kafka/MSK event streams.

**Apache Flink**
- **Throughput**: Processes millions of events/sec across distributed clusters. Single TaskManager handles 100k+ events/sec.
- **Stateful Processing**: Built-in state management with RocksDB backend. Complex event processing and windowing without external databases.
- **Deployment**: Separate cluster infrastructure (Kubernetes, YARN, or AWS Managed Service). Independent scaling from Kafka brokers.
- **Scalability**: Horizontal scaling via TaskManager parallelism. Automatic state partitioning across workers.
- **Latency**: Sub-second event processing with proper tuning. Event time windows enable accurate vote tallies despite delays.
- **Cost**: AWS Managed Service for Apache Flink runs ~$0.11/hour per KPU. Estimate 10-20 KPUs for 250k RPS analytics ($800-1,600/month). Self-managed on EKS reduces cost but adds operational overhead.
- **Operations**: Managed service handles scaling, checkpointing, recovery. Self-managed requires Kubernetes expertise and monitoring setup.
- **Best for**: Complex event processing, multiple stateful operators, cross-stream joins, advanced windowing, non-Kafka data sources.

**Kafka Streams**
- **Throughput**: Processes millions of events/sec. Performance tied to Kafka partition count and consumer parallelism.
- **Stateful Processing**: RocksDB-backed state stores with changelog topics. Simpler stateful operations and aggregations.
- **Deployment**: Embedded library within your application (Spring Boot). No separate cluster infrastructure needed.
- **Scalability**: Horizontal scaling by adding application instances. Limited by Kafka partition count (1 instance per partition max).
- **Latency**: Sub-millisecond processing latency. Tighter integration with Kafka reduces overhead.
- **Cost**: No separate infrastructure cost. Runs within existing application containers. Only pay for compute instances running your Spring Boot apps.
- **Operations**: Deploy as part of your application. Simpler operational model. No separate cluster to manage.
- **Best for**: Kafka-only pipelines, simpler stateful processing, embedded in Spring Boot apps, lower operational complexity.

**Recommendation**: Kafka Streams for this voting system. Simpler operational model embedding directly in Spring Boot applications eliminates separate cluster management. For vote aggregations and real-time counters, Kafka Streams provides sufficient stateful processing capabilities with lower complexity and cost. Tradeoff is less flexibility for complex event processing vs Flink's advanced features, but the voting use case doesn't require cross-stream joins or complex windowing that would justify Flink's operational overhead.

### Event Streaming: Kafka vs. RabbitMQ

Event streaming decouples vote ingestion from processing, essential for handling bursts without data loss.

**Apache Kafka**
- **Throughput**: 15x faster writes than RabbitMQ. Handles millions of messages/sec on multi-broker clusters. Ideal for 250k RPS vote events.
- **Latency**: Low end-to-end latency at high throughputs (sub-ms at p99.9). Pull-based model batches for efficiency.
- **Scalability**: Horizontal partitioning across brokers. Linear scaling for 300M user events with replayability for audits.
- **Durability**: Built-in replication (factor 3) and log retention. No data loss even on failures.
- **Complexity**: High setup with Zookeeper dependency and partitioning strategy. Steeper learning curve but mature for AWS MSK.
- **Best for**: High write volume with replay requirements. Proven at LinkedIn/Uber scale.

**RabbitMQ**
- **Throughput**: 4k-10k messages/sec. Sufficient for moderate loads but bottlenecks at peaks without careful tuning.
- **Latency**: Lower latency for low-volume real-time tasks with immediate push. Degrades quickly under high load.
- **Scalability**: Vertical scaling preferred. Clustering adds delays, limiting to smaller deployments.
- **Durability**: Optional persistence requiring configuration. Risks data loss during spikes.
- **Complexity**: Moderate operational overhead. Easier routing patterns and operations, better for prototypes.
- **Best for**: Quick starts and simpler deployments. Hybrid approach with Kafka for analytics.

**Recommendation**: Kafka for voting system. High write volume and replay needs outweigh RabbitMQ's simplicity. Tradeoff is more operational overhead, but proven at scale.

### AWS Managed: Kafka (MSK) vs. SQS

For AWS deployments, choosing between managed Kafka (MSK) and native SQS depends on throughput patterns, replay requirements, and operational preferences.

**Apache Kafka on MSK**
- **Throughput**: 2M+ messages/sec on standard clusters; m7g.4xlarge brokers deliver 54 MB/s write throughput per broker. Three-node m7g.4xlarge cluster sustains 200 MB/sec (400 MB/sec with 6 brokers). MSK Serverless limited to 200 MBps makes it unsuitable for 250k RPS peaks.
- **Scalability**: Horizontal partitioning across brokers with up to 500 shards per cluster. Express brokers provide 3x throughput per broker with unlimited storage.
- **Durability**: Replication factor 3 with min.insync.replicas=2 ensures no data loss. Configurable log retention (days to weeks) enables message replay for audits and reprocessing.
- **Latency**: Sub-millisecond at p99.9 with proper tuning. Configurable acks (0/1/all) trade latency for durability guarantees.
- **Cost**: Fixed hourly broker charges (~$1.44/hour per m7g.4xlarge). Three-broker minimum runs ~$3,100/month base plus storage. Better economics for sustained high-volume workloads.
- **Operations**: AWS manages infrastructure, patching, and failover. You manage cluster sizing, partition strategy, consumer lag monitoring. Moderate to high complexity.
- **Best for**: Sustained 250k RPS with multiple consumers, event sourcing, streaming analytics with Flink, strict ordering within partitions.

**AWS SQS**
- **Throughput**: Nearly unlimited on Standard queues (scales automatically). FIFO queues limited to 3,000 msg/sec with batching, 300 msg/sec without.
- **Scalability**: Fully serverless with automatic elastic scaling. No capacity planning required.
- **Durability**: Standard queues provide at-least-once delivery with best-effort ordering and occasional duplicates. FIFO queues guarantee exactly-once processing with strict ordering. Maximum 14-day retention, no replay capability.
- **Latency**: Standard queues have minimal overhead. FIFO adds ordering latency. Centralized architecture can bottleneck at extreme scale.
- **Cost**: Pay-per-request model. $0.40 per million requests for Standard, $0.50 for FIFO. At sustained 250k RPS (21.6B requests/day), daily cost reaches ~$8,640 (Standard), monthly ~$259k. Better for variable/bursty loads.
- **Operations**: Zero infrastructure management. Simple API integration. Low complexity.
- **Best for**: Variable traffic patterns, simple task distribution where replay isn't needed, minimal operational overhead, acceptable duplicate handling.

**Recommendation**: MSK Provisioned for this voting system. The sustained 250k RPS, multi-consumer patterns (analytics, fraud detection, archival), and audit replay requirements justify the fixed cost (~$6,200/month) over SQS's variable pricing. Six m7g.4xlarge brokers provide 400 MB/sec capacity with 16x headroom over peak loads.

## Cache

### In-Memory Store: Redis vs. Memcached

Self-managed in-memory stores for vote deduplication and real-time counters.

**Redis**
- **Performance**: 200M ops/sec cluster-wide (5M/node). Sub-ms latency for sets/gets, but higher tail latency under heavy writes.
- **Data Structures**: Rich support for sets, hashes, pub/sub. Essential for rate limiting and counters. Persistence (AOF/RDB) prevents data loss.
- **Scalability**: Clustering for horizontal growth. Handles 300M sessions with sharding.
- **Durability**: Configurable persistence ensures no data loss. TTL support for vote tokens.
- **Complexity**: More features increase config time. Integrates well with Spring Data Redis.
- **Best for**: Bot prevention, real-time updates, deduplication requiring persistence.

**Memcached**
- **Performance**: Higher throughput/low latency for simple key-value operations. Multi-threaded architecture excels in read-heavy workloads but drops under contention.
- **Data Structures**: Basic strings only. No persistence, risking eviction during peaks.
- **Scalability**: Easy horizontal scaling via client-side sharding. No built-in replication can lead to hotspots.
- **Durability**: Fully volatile. Data loss on restarts makes it unsuitable for critical deduplication.
- **Complexity**: Simpler/faster setup. Lighter resource use but limited extensibility.
- **Best for**: Pure caching. Pair with Redis for sessions.

**Recommendation**: Redis for voting system. Versatility for bot prevention and real-time updates essential. Tradeoff is slightly higher resource use vs. Memcached's speed, but persistence wins for security.

### AWS Managed: ElastiCache for Redis vs. Memcached

ElastiCache provides fully managed Redis and Memcached on AWS, eliminating infrastructure overhead while maintaining performance characteristics. For this voting system, Redis on ElastiCache is essential for vote deduplication and real-time counters.

**ElastiCache for Redis**
- **Performance**: 1M+ RPS per node on r7g.4xlarge or larger. Cluster mode enabled supports 500M ops/sec across cluster with sub-millisecond latency. Sufficient capacity to handle 250k vote operations/sec with massive headroom.
- **Data Structures**: Full Redis feature set including Sets (vote deduplication with O(1) uniqueness checks), SETNX (atomic "vote if not exists"), INCR/DECR (race-condition-free counters), Pub/Sub (real-time broadcast), Sorted Sets (leaderboards), HyperLogLog (cardinality estimation), and Bloom filters via RedisBloom.
- **Persistence**: RDB snapshots for point-in-time backups, AOF for write logging. Configurable persistence prevents data loss on restarts—critical for vote integrity and deduplication state.
- **Clustering**: Cluster mode enabled supports up to 500 shards with horizontal scaling via partitioning. Each shard runs 1 primary + up to 5 read replicas. Data partitioned by key hash across nodes. Handles 300M users with proper sharding strategy (partition by user ID). Cluster mode disabled supports vertical scaling with 1 primary + 5 replicas, simpler but limited.
- **High Availability**: Automatic failover with Multi-AZ replication. Sentinel for health monitoring. 99.99% SLA. Global Datastore provides cross-region replication under 1 second.
- **Cost**: cache.r7g.xlarge runs ~$252/month (~$13/GiB/month with 9.8 GiB maxmemory). For 300M user deduplication and counters, estimate 30-50 nodes total cost ~$7,500-12,500/month. Reserved instances provide up to 75% savings. 20% discount available migrating to Valkey.
- **Operations**: AWS manages patching, backups, failover, monitoring. You manage cluster sizing, key design, data modeling. Integrates with Spring Data Redis and CloudWatch metrics.
- **Use cases**: Vote deduplication (Sets with TTL), real-time counters (atomic INCR), rate limiting, session management, leaderboards, pub/sub for live updates.

**ElastiCache for Memcached**
- **Performance**: Multi-threaded architecture provides better raw throughput for simple key-value operations. Lower P90/P99 tail latency under heavy load compared to Redis. Optimized for high-speed caching with minimal overhead.
- **Data Structures**: Strings only (basic key-value). No complex data structures, no built-in deduplication support, no atomic counters. Requires external coordination for vote uniqueness.
- **Persistence**: No persistence—fully volatile. All data lost on restarts or failures. Unsuitable for critical deduplication data or any state that cannot be rebuilt.
- **Clustering**: Client-side sharding with no built-in replication. Easy horizontal scaling but can create hotspots. No automatic failover.
- **High Availability**: No built-in HA mechanisms. Application must handle node failures and cache warming.
- **Cost**: Similar base pricing to Redis ElastiCache. Simpler deployment may require fewer nodes for pure caching workloads.
- **Operations**: AWS manages infrastructure but simpler configuration due to fewer features. Lower operational complexity but also lower capability.
- **Use cases**: Pure result caching, temporary session storage where loss is acceptable, read-heavy workloads with external persistence.

**Architecture Pattern for 300M User Voting System**
```
Vote Request → ElastiCache Redis SET check (deduplication) →
  If new vote → MSK Kafka (event log) →
    Redis INCR (real-time counter) →
      Redis Pub/Sub (broadcast updates) →
        WebSocket clients (live results)
```

**Scaling Strategy**
- Deploy ElastiCache Redis with cluster mode enabled
- 30-50 r7g.xlarge shards for 30M-50M ops/sec capacity
- Partition keys by user ID hash for even distribution
- Replication factor 2-3 for high availability
- Enable AOF persistence for durability
- Use Redis Sets with TTL for time-windowed vote deduplication
- Use Redis HINCRBY for per-candidate counters (race-condition-free)
- Pub/Sub channels for broadcasting real-time tallies

**Recommendation**: ElastiCache for Redis (cluster mode enabled) is required for this voting system. Memcached lacks essential features—no data structures for O(1) deduplication checks, no persistence for vote integrity, no atomic operations for counters. The managed service reduces operational overhead (automatic patching, failover, backups) while providing the 500M ops/sec capacity needed with 99.99% SLA. Total cost ~$10,000/month for 30-50 node cluster provides both deduplication and real-time counting capabilities.

### AWS Managed: EC2 Redis vs. ElastiCache Redis

Choosing between self-managed Redis on EC2 and fully managed ElastiCache depends on cost optimization vs operational complexity tradeoffs.

**Self-Managed Redis on EC2**
- **Performance**: Same Redis engine performance. You control tuning and optimization.
- **Scalability**: Manual cluster setup with Redis Cluster. Requires scripting for adding nodes and rebalancing. No automatic scaling.
- **Durability**: Self-configured RDB/AOF persistence. Manual backup management and restoration procedures. Risk of misconfiguration.
- **High Availability**: Manual setup of Redis Sentinel or Cluster mode. You handle failover detection and promotion logic. No guaranteed SLA.
- **Cost**: r7g.4xlarge instance ~$490/month (on-demand) or ~$290/month (reserved). 30-50 instances total ~$8,700-14,500/month (reserved). Lower base cost than ElastiCache but requires dedicated ops engineer (~$10k+/month).
- **Operations**: Full responsibility for patching, monitoring, backup automation, cluster management, failover testing, security updates. Requires Redis expertise and 24/7 on-call.
- **Best for**: Cost-sensitive deployments with existing Redis expertise and dedicated ops team. Acceptable operational burden.

**AWS ElastiCache for Redis**
- **Performance**: Identical Redis performance. AWS-optimized networking.
- **Scalability**: Push-button cluster scaling. Automatic shard rebalancing. Online resharding without downtime.
- **Durability**: Automated backups with configurable retention. Point-in-time recovery. Multi-AZ replication built-in.
- **High Availability**: Automatic failover in under 60 seconds. Multi-AZ with 99.99% SLA. Global Datastore for cross-region replication.
- **Cost**: cache.r7g.4xlarge ~$730/month (on-demand) or ~$440/month (reserved). 30-50 nodes total ~$13,200-22,000/month (reserved). 50% higher base cost than EC2 but zero ops overhead.
- **Operations**: AWS handles all infrastructure, patching, monitoring, backups, failover. You focus on data modeling and application integration. CloudWatch metrics included.
- **Best for**: Production systems requiring high SLA, minimal operational burden, and proven reliability at scale.

**Recommendation**: ElastiCache for this voting system. The 300M user scale and 250k RPS demand 99.99% uptime that self-managed Redis on EC2 cannot guarantee without significant ops investment. While EC2 saves ~$4,000-8,000/month in infrastructure costs, the operational burden (24/7 on-call, manual failover, backup management, security patching) requires dedicated engineering ($10k+/month). ElastiCache's automatic failover, Multi-AZ replication, and managed backups justify the premium for mission-critical vote deduplication.

## API Gateway

### NGINX vs. Kong

API gateways manage authentication and rate limiting at ingress.

**NGINX Plus**
- **Throughput**: 30k RPS at <30ms latency (p99.99). 50% higher max RPS than Kong.
- **Latency**: Consistent <13ms even at peaks. Real-time API standard performance.
- **Features**: Built-in rate limiting and JWT validation. Lighter resource footprint for AWS deployments.
- **Scalability**: Horizontal scaling with ALB integration. 40% less CPU usage than Kong.
- **Complexity**: Simpler configuration. Familiar syntax from web server background.
- **Best for**: High-performance APIs prioritizing speed and low latency over plugin ecosystem.

**Kong**
- **Throughput**: 137k RPS theoretical max but latency spikes (3x at p99.99). Lua plugins add overhead.
- **Latency**: Negligible up to p99, then exponential growth under load.
- **Features**: Rich plugin ecosystem for bot detection and custom routing. More extensible but resource-heavy.
- **Scalability**: Good clustering support. Higher latency at scale due to plugin processing.
- **Complexity**: Modular architecture. Plugin management increases operational complexity.
- **Best for**: Advanced routing requirements and custom middleware needs.

**Recommendation**: NGINX Plus for voting system. Superior performance for 250k RPS with consistent low latency. Tradeoff is fewer plugins vs Kong's flexibility, but speed and security prioritize for real-time voting.
