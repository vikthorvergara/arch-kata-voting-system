# TODO

## Tradeoffs

### Stream
Kafka vs SQS (infra em aws)

### Cache
Redis vs memory cache aws vs elastic cache aws

## Tradeoff Comparison for Realtime Voting System Stack

For a realtime voting system handling 300M users and 250k RPS peaks, the best choices prioritize horizontal scalability, low latency, and write durability while avoiding disallowed technologies. Key tradeoffs center on throughput vs. complexity, consistency vs. availability, and simplicity vs. feature richness. Below, I compare top options for critical components, selecting based on your Spring Boot/Kotlin/AWS background for easier adoption.

### Event Streaming: Kafka vs. RabbitMQ

#### TODO: With APACHE FLINK

Event streaming decouples vote ingestion from processing, essential for handling bursts without data loss. Kafka excels in high-throughput scenarios like this system, but RabbitMQ offers simpler setup for initial development.

| Aspect | Apache Kafka [1][2] | RabbitMQ [2][3][4] |
|--------|--------------------------------|-------------------------------------|
| **Throughput** | 15x faster writes than RabbitMQ; handles millions of messages/sec on multi-broker clusters (e.g., 7T messages/day at scale). Ideal for 250k RPS vote events. | 4k-10k messages/sec; sufficient for moderate loads but bottlenecks at peaks without careful tuning. |
| **Latency** | Low end-to-end latency at high throughputs (sub-ms at p99.9); pull-based model batches for efficiency. | Lower latency for low-volume real-time tasks (immediate push); degrades quickly under high load. |
| **Scalability** | Horizontal partitioning across brokers; linear scaling for 300M user events with replayability for audits. | Vertical scaling preferred; clustering adds delays, limiting to smaller deployments. |
| **Durability & Fault Tolerance** | Built-in replication (factor 3) and log retention; no data loss even on failures. | Optional persistence; requires config for durability, risking loss during spikes. |
| **Complexity** | High setup (Zookeeper dependency, partitioning strategy); steeper learning curve but mature for AWS MSK. | Moderate; easier routing patterns and operations, better for prototypes. |
| **Best Fit Here** | Recommended for voting: High write volume and replay needs outweigh RabbitMQ's simplicity. Tradeoff: More operational overhead but proven at LinkedIn/Uber scale. | Use if team prefers quick starts; hybrid with Kafka for analytics. |

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

### Primary Database: PostgreSQL vs. Cassandra

#### TODO: search keyspace - datamax

For storing 300M votes with uniqueness guarantees, databases must handle write-heavy loads while ensuring ACID properties. PostgreSQL fits relational needs but Cassandra offers better write distribution.

| Aspect | PostgreSQL (Sharded) [5][6] | Apache Cassandra [5][6][7] |
|--------|---------------------------------------|---------------------------------------------|
| **Write Throughput** | 10k+ writes/sec per shard with optimization (e.g., batching, WAL); handles 250k RPS via 6-10 shards but adds coordination overhead. | 10-15x higher writes on clusters; excels at distributed append-only votes without single-node bottlenecks. |
| **Read Performance** | Superior for complex queries (e.g., analytics on voter data); read replicas offload traffic. | Optimized for simple key-value reads; slower for joins but fast for vote tallies. |
| **Scalability** | Horizontal via sharding (Citus extension); linear but requires custom partitioning by user ID/region. | Native distributed model; add nodes for seamless scaling to 300M records across data centers. |
| **Consistency & Durability** | Full ACID transactions for vote once enforcement; WAL ensures no data loss. | Tunable consistency (eventual for writes); eventual model trades strict ACID for availability in peaks. |
| **Complexity** | Familiar SQL for your Spring Boot apps; easier integration with JPA/Hibernate. | NoSQL learning curve; query limits (no joins) need denormalization. |
| **Best Fit Here** | Recommended: Balances your relational expertise with sharding for scale. Tradeoff: More tuning for writes vs. Cassandra's out-of-box distribution, but better for audit queries. | Switch if writes dominate (>80% load); use for pure vote logs, with Postgres for user metadata. |

### In-Memory Store: Redis vs. Memcached

Redis handles vote deduplication and real-time counters at 250k ops/sec. It trades simplicity for versatility compared to Memcached.

| Aspect | Redis Enterprise [8][9][10] | Memcached [8][9][10] |
|--------|---------------------------------------------|--------------------------------------|
| **Performance** | 200M ops/sec cluster-wide (5M/node); sub-ms latency for sets/gets, but higher tail latency under heavy writes. | Higher throughput/low latency for simple key-value (multi-threaded); excels in read-heavy but drops under contention. |
| **Data Structures** | Rich support (sets, hashes, pub/sub) for rate limiting and counters; persistence (AOF/RDB) prevents loss. | Basic strings only; no persistence, risking eviction during peaks. |
| **Scalability** | Clustering for horizontal growth; handles 300M sessions with sharding. | Easy horizontal (client-side); but no built-in replication, leading to hotspots. |
| **Durability** | Configurable persistence for never lose data; TTL for vote tokens. | Volatile; data loss on restarts—unsuitable for critical dedup. |
| **Complexity** | More features increase config time; integrates well with Spring Data Redis. | Simpler/faster setup; lighter resource use but limited extensibility. |
| **Best Fit Here** | Recommended: Versatility for bot prevention and real-time updates. Tradeoff: Slightly higher resource use vs. Memcached's speed, but essential persistence wins for security. | Use for pure caching; pair with Redis for sessions. |

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

### Application Framework: Rust & Go vs Spring Boot

| Framework                       | Throughput (RPS) | Use Case Fit                                                          |
| ------------------------------- | ---------------- | --------------------------------------------------------------------- |
| Go (Gin/Fiber)                  | 800k–1M+         | Exceeds 250k by 3-4x; ideal for high-concurrency APIs                 |
| Rust (Actix/Axum)               | 100k+ per core   | Maximizes per-core efficiency; best for compute-bound vote validation |
| Spring Boot 4 (Virtual Threads) | 250k-300k        | Meets target but less margin; virtual threads narrow gap for I/O      |

### API Gateway: NGINX vs. Kong

Gateways manage auth/rate limiting at ingress.

| Aspect | NGINX Plus [14][15] | Kong [14][15] |
|--------|------------------------------|------------------------|
| **Throughput** | 30k RPS at <30ms latency (p99.99); 50% higher max RPS than Kong. | 137k RPS but latency spikes (3x at p99.99); Lua plugins add overhead. |
| **Latency** | Consistent <13ms even at peaks; real-time API standard. | Negligible up to p99, then exponential growth under load. |
| **Features** | Built-in rate limiting/JWT; lighter for your AWS setup. | Plugin ecosystem for bots; more extensible but resource-heavy. |
| **Scalability** | Horizontal with ALB integration; low CPU (40% less than Kong). | Good clustering; but higher latency at scale. |
| **Complexity** | Simpler config; familiar from web servers. | Modular but plugin management increases ops. |
| **Best Fit Here** | Recommended: Superior performance for 250k RPS. Tradeoff: Fewer plugins vs. Kong's flexibility, but speed/security prioritize here. | Use for advanced routing if needed. |

## References

[1] Confluent - Kafka Fastest Messaging System: https://www.confluent.io/blog/kafka-fastest-messaging-system/

[2] AutoMQ - Apache Kafka vs RabbitMQ Differences Comparison: https://www.automq.com/blog/apache-kafka-vs-rabbitmq-differences-comparison

[3] Latitude - RabbitMQ vs Kafka Latency Comparison: https://latitude-blog.ghost.io/blog/rabbitmq-vs-kafka-latency-comparison-for-ai-systems/

[4] CloudThat - Decoding Kafka vs RabbitMQ for Modern Real-Time Applications: https://www.cloudthat.com/resources/blog/decoding-kafka-vs-rabbitmq-for-modern-real-time-applications/

[5] Levitation - Cassandra Outperforms PostgreSQL Under Heavy Load: https://levitation.in/posts/cassandra-outperforms-postgresql-under-heavy-load-conditions

[6] Knowi - PostgreSQL vs Cassandra Key Differences and Performance: https://www.knowi.com/blog/postgresql-vs-cassandra-key-differences-use-cases-performance/

[7] Dev.to - Cassandra vs PostgreSQL Developer's Guide: https://dev.to/wallaceespindola/cassandra-vs-postgresql-a-developers-guide-to-choose-the-right-database-3nhi

[8] WildNet Edge - Redis vs Memcached Caching Tool Comparison: https://www.wildnetedge.com/blogs/redis-vs-memcached-which-caching-tool-is-better

[9] DZone - Performance and Scalability Analysis of Redis and Memcached: https://dzone.com/articles/performance-and-scalability-analysis-of-redis-memcached

[10] Stack Overflow - Memcached vs Redis Discussion: https://stackoverflow.com/questions/10558465/memcached-vs-redis

[14] F5 - Benchmarking API Management Solutions NGINX Kong: https://www.f5.com/company/blog/nginx/benchmarking-api-management-solutions-nginx-kong-amazon-real-time-apis

[15] Daily.dev - Top 6 Open Source API Gateway Frameworks: https://daily.dev/blog/top-6-open-source-api-gateway-frameworks
