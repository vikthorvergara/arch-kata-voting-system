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

[1](https://www.confluent.io/blog/kafka-fastest-messaging-system/)
[2](https://www.automq.com/blog/apache-kafka-vs-rabbitmq-differences-comparison)
[3](https://latitude-blog.ghost.io/blog/rabbitmq-vs-kafka-latency-comparison-for-ai-systems/)
[4](https://www.cloudthat.com/resources/blog/decoding-kafka-vs-rabbitmq-for-modern-real-time-applications/)
[5](https://levitation.in/posts/cassandra-outperforms-postgresql-under-heavy-load-conditions)
[6](https://www.knowi.com/blog/postgresql-vs-cassandra-key-differences-use-cases-performance/)
[7](https://dev.to/wallaceespindola/cassandra-vs-postgresql-a-developers-guide-to-choose-the-right-database-3nhi)
[8](https://www.wildnetedge.com/blogs/redis-vs-memcached-which-caching-tool-is-better)
[9](https://dzone.com/articles/performance-and-scalability-analysis-of-redis-memcached)
[10](https://stackoverflow.com/questions/10558465/memcached-vs-redis)
[11](https://dev.to/jottyjohn/spring-mvc-vs-spring-webflux-choosing-the-right-framework-for-your-project-4cd2)
[12](https://ojs.cuadernoseducacion.com/ojs/index.php/ced/article/download/9049/6180)
[13](https://www.linkedin.com/pulse/building-high-performance-real-time-applications-java-geison-flores-244yf)
[14](https://www.f5.com/company/blog/nginx/benchmarking-api-management-solutions-nginx-kong-amazon-real-time-apis)
[15](https://daily.dev/blog/top-6-open-source-api-gateway-frameworks)
[16](https://www.buoyant.io/linkerd-vs-istio)
[17](https://linkerd.io/2021/11/29/linkerd-vs-istio-benchmarks-2021/)
[18](https://www.bladepipe.com/blog/data_insights/kafka_vs_rabbitmq_vs_rocketmq_pulsar)
[19](https://www.designgurus.io/blog/rabbitmq-kafka-activemq-system-design)
[20](https://www.pubnub.com/blog/kafka-vs-rabbitmq-choosing-the-right-messaging-broker/)
