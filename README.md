# Search Typeahead System

A highly scalable, production-ready Search Typeahead (Autocomplete) Engine engineered for extremely low latency and high throughput. 

This project demonstrates distributed system patterns including in-memory caching, stream processing, batch aggregation, consistent hashing, and eventual consistency to serve real-time search suggestions.

---

## 1. Project Overview

A **Typeahead System** (or autocomplete) predicts a user's search query as they type, significantly improving user experience by reducing keystrokes and guiding them toward popular or relevant content.

**The Problem:**
Autocomplete APIs face extreme read-heavy workloads. A single user typing "google" triggers 6 separate requests (`g`, `go`, `goo`, `goog`, `googl`, `google`). Querying a relational database for prefix matches on millions of records for every keystroke would quickly exhaust connection pools and crush database performance.

**Why Low Latency Matters:**
To feel instantaneous, autocomplete suggestions must render within 100 milliseconds. This requires serving reads entirely from memory, bypassing the database on the critical path.

---

## 2. Assignment Requirements

The system successfully implements all core and advanced requirements:

* **Typeahead Suggestions:** Real-time prefix completion.
* **Top 10 Suggestions:** Results are aggressively bounded to the top 10 relevant matches.
* **Prefix Matching:** Optimized character-by-character search.
* **Trending Searches:** Real-time dashboard showing hot queries.
* **Batch Writes:** Asynchronous DB write aggregation to protect database I/O.
* **Distributed Cache:** Redis Sorted Sets handling the read path.
* **Consistent Hashing:** Algorithm implemented to shard keys across cache nodes.
* **System Metrics:** Real-time observability dashboard for hit rates and flush sizes.
* **Debouncing:** 300ms frontend delay to reduce network noise.
* **Eventual Consistency:** Decoupled write path via Redis Streams.

---

## 3. Dataset

The system is seeded with a sanitized subset of the **AOL Search Query Dataset**.

* **Rows loaded:** ~128,810 unique queries.
* **Preprocessing Pipeline:**
  1. **Lowercasing:** All queries normalized to ensure case-insensitive matching.
  2. **Deduplication:** Identical queries collapsed.
  3. **Aggregation:** Historical search volumes calculated to seed the `total_count`.
  4. **Trend Score Generation:** Initial `trend_score` seeded to mirror historical volumes before decay cycles begin.

---

## 4. System Architecture

The architecture decouples the high-volume read path from the bursty write path, using Redis as the central nervous system.

### Components
* **Frontend:** React SPA handling debouncing and UI states.
* **Spring Boot API:** Stateless backend orchestrating logic.
* **Redis Cache:** In-memory Sorted Sets serving the read path.
* **Redis Streams:** Append-only log decoupling the write path.
* **Aggregator (Consumer):** In-memory map batching identical searches.
* **PostgreSQL:** Source of truth for long-term storage.
* **Trend Decay Scheduler:** Nightly cron job applying exponential decay.
* **Metrics Service:** Atomic counters tracking system health.

### Architecture Diagram

```text
       ┌──────────────┐
       │   Frontend   │
       └──────┬───────┘
              │ (1) User types
              ▼
       ┌──────────────┐
       │ Spring Boot  │
       └──────┬───────┘
              │ (2) Read Path (GET /suggest)
      ┌───────┴────────┐
      │                │ (Miss)
(Hit) ▼                ▼
┌──────────────┐ ┌──────────────┐
│ Redis Cache  │ │  PostgreSQL  │
└──────────────┘ └──────┬───────┘
                        │ (3) Populate Cache
                        ▼
                 ┌──────────────┐
                 │ Redis Cache  │
                 └──────────────┘

---------------------------------------------------

              (Write Path)
              POST /search
                   │
                   ▼
           ┌──────────────┐
           │ Redis Stream │ (Fire and forget)
           └──────┬───────┘
                  │
                  ▼
          ┌───────────────┐
          │  Aggregator   │ (Consumer loop)
          └──────┬────────┘
                 │ (Flush threshold reached)
                 ▼
          ┌───────────────┐
          │  PostgreSQL   │ (Batch UPSERT)
          └──────┬────────┘
                 │
                 ▼
          ┌───────────────┐
          │ Cache Refresh │ (Update ZSETs)
          └───────────────┘
```

---

## 5. Read Path

The critical read path executes when a user queries `GET /suggest?q=<prefix>`.

**Flow:**
1. Frontend debounces input and sends a request.
2. Spring Boot queries **Redis Cache** for the prefix.
3. **Cache Hit:** If found, Redis immediately returns the pre-sorted list in `O(log N)` time.
4. **Cache Miss:** 
   - Fall back to **PostgreSQL**.
   - Calculate ranking scores.
   - Serve the user.
   - Asynchronously populate the Redis Cache so subsequent requests hit memory.

This is a classic **Cache Aside** pattern, ensuring the database is protected from thundering herds while remaining the source of truth.

---

## 6. Write Path

Every search submitted by a user triggers `POST /search`.

**Flow:**
1. The API immediately publishes the event to **Redis Streams** (`search_events`) and returns a `200 OK` to the user in `O(1)` time.
2. An asynchronous **Consumer** continuously polls the stream, aggregating identical queries in a `ConcurrentHashMap`.
3. **Flush Conditions:** The map is flushed to PostgreSQL when either:
   - **1,000 events** have been collected.
   - **30 seconds** have elapsed since the last flush.
4. The batch is written to PostgreSQL via a single transaction using `INSERT ... ON CONFLICT DO UPDATE` (Batch UPSERT).
5. Finally, the Cache Refresh service updates the affected Redis Sorted Sets with the new counts.

**Why Batching?**
If 1,000 users search for "google" within 5 seconds, direct DB writes would trigger 1,000 separate `UPDATE` statements, exhausting disk I/O. By aggregating in memory, we execute exactly **1 write** mapping "google" to `+1000`.

---

## 7. Ranking Algorithm

Ranking relies on a blended logarithmic formula:

```text
score = LN(total_count + 1) + LN(trend_score + 1)
```

**Why Logarithmic Scaling?**
Without logarithms, historically massive queries would permanently dominate the top spots. If "google" has 1,000,000 historical searches, a newly trending query with 5,000 searches could never surpass it linearly. Logarithms compress the scale, allowing highly-trending recent queries to effectively compete with historical giants.

**Example:**
* A historical titan: `total_count` = 1,000,000, `trend_score` = 50 
  * `score = LN(1000001) + LN(51) = 13.8 + 3.9 = 17.7`
* A viral trend: `total_count` = 10,000, `trend_score` = 8,000
  * `score = LN(10001) + LN(8001) = 9.2 + 8.9 = 18.1`

The viral trend successfully outranks the historical titan.

---

## 8. Trend Decay

A scheduled Cron job executes the following native SQL query nightly:

```sql
UPDATE search_queries SET trend_score = trend_score * 0.9
```

**Purpose:**
Without decay, `trend_score` would grow indefinitely and mirror `total_count`. By applying a 10% exponential decay factor, older searches lose their "trendiness" over time, allowing fresh queries to bubble up to the surface.

---

## 9. Redis Design

The cache heavily utilizes **Redis Sorted Sets (ZSET)**.

**Key Format:**
Keys are namespaced by the exact prefix, e.g., `prefix:goo`.

**Members and Scores:**
* Member: The query string (e.g., `google`, `google maps`)
* Score: The pre-calculated floating-point ranking score (e.g., `19.62`).

**Operations:**
* **`ZADD`**: Atomic insertion or score-update in `O(log N)`.
* **`ZREVRANGE`**: Extremely fast retrieval of the top 10 elements in `O(log N + K)`.
* **`ZREMRANGEBYRANK`**: Used during cache refresh (`0 -(TOP_K+1)`) to effortlessly trim the set down to a bounded size, preventing unlimited memory growth.

Sorted Sets perfectly align with typeahead requirements: they maintain order natively, meaning reads require zero sorting overhead.

---

## 10. Consistent Hashing

A demonstration of distributed caching is implemented via `ConsistentHashRing`.

**Why Consistent Hashing?**
In a multi-node Redis cluster, standard modulo hashing (`hash(key) % N`) fails catastrophically during scaling. If a node is added or removed, the denominator changes, causing nearly 100% of keys to map to wrong nodes, triggering a massive cache stampede against PostgreSQL.

**The Solution:**
Consistent hashing maps both Cache Nodes (e.g., `redis-node-1`, `redis-node-2`) and Keys onto a 64-bit circular ring. A key simply walks clockwise to find its assigned node. 
* **Minimal Key Movement:** If a node is removed, only the keys assigned to that specific node are reassigned. All other keys remain intact.
* **Virtual Nodes:** We map each physical node to 150 "virtual" positions on the ring. This guarantees an even distribution of data, preventing hot-spots.

---

## 11. Metrics

Atomic, lock-free counters (`AtomicLong`) track system performance in real-time, exposed to the frontend dashboard:

* **Cache Hits:** Direct Redis serves.
* **Cache Misses:** Fallbacks to PostgreSQL.
* **Cache Hit Rate:** Percentage of requests avoiding DB I/O.
* **DB Reads:** PostgreSQL `SELECT` fallback executions.
* **DB Writes:** Flush operations executed.
* **Stream Events Published:** Searches logged to Redis.
* **Stream Events Consumed:** Searches processed by the aggregator.
* **Batch Flush Count:** Number of aggregation windows committed.
* **Average Flush Size:** Helps visualize the efficiency of batching.

---

## 12. API Documentation

### Autocomplete API
**`GET /suggest?q=<prefix>`**
Returns up to 10 suggestions.
```json
[
  { "query": "google", "score": 19.62 },
  { "query": "google docs", "score": 14.10 }
]
```

### Search Event API
**`POST /search`**
Registers a search. Returns immediately.
```json
// Request
{ "query": "google" }

// Response
{ "message": "Searched" }
```

### Trending API
**`GET /trending`**
Returns top 5 global trending queries.
```json
[
  { "query": "google", "score": 19.62, "totalCount": 32532, "trendScore": 10224 }
]
```

### Diagnostics API
* **`GET /metrics`**: Snapshots all atomic performance counters.
* **`GET /cache/debug?prefix=goo`**: Exposes the assigned consistent hash node and ZSET size.
* **`GET /cache/ring`**: Visualizes the hashing ring load distribution.
* **`GET /health/db`** & **`GET /health/redis`**: Dependency probes.

---

## 13. Frontend Features

The frontend is a modern React/Vite application utilizing a premium "glassmorphism" aesthetic.

* **Debounced Input:** 300ms pause required before network calls, massively reducing useless requests for intermediate typing (e.g., "g", "go", "goo").
* **Keyboard Navigation:** Full support for `ArrowUp`, `ArrowDown`, and `Enter` selection.
* **Loading & Error States:** Animated skeleton rows prevent layout shift. Network failures present graceful fallback UI with retry buttons.
* **Trending Dashboard:** Automatically polls the backend every 30s to show real-time hot queries.
* **System Metrics Dashboard:** Polls every 10s to visualize live infrastructure load.

---

## 14. Design Tradeoffs

* **Cache Aside vs Write-Through:** Cache Aside was chosen over Write-Through. Autocomplete requires prefix generation (e.g., `google` -> `goo`, `goog`). Updating 10 different prefix keys synchronously on every search would drastically slow down writes. Cache Aside lazily generates these keys only when they are read.
* **Redis Streams vs RabbitMQ/Kafka:** Redis Streams provides the perfect balance of durability and low operational overhead since Redis is already present in the stack for caching.
* **Batch Writes vs Immediate Consistency:** We traded immediate consistency for high availability. User searches take up to 30 seconds to reflect in suggestions, which is an entirely acceptable business tradeoff to ensure the database survives traffic spikes.
* **Sorted Sets vs JSON blobs:** Storing an entire JSON array string per prefix would require fetching the whole string, parsing, updating, and re-serializing it on every cache refresh. Sorted Sets allow `O(log N)` granular updates to single suggestions.

---

## 15. Running the Project

### Prerequisites
* Java 21+
* Maven 3.8+
* Node.js 18+
* PostgreSQL 15+
* Redis 7+

### 1. Start Redis
Using Docker is the easiest approach:
```bash
docker run -d --name redis -p 6379:6379 redis
```
Alternatively, install Redis locally via Homebrew (`brew install redis`) or apt.

### 2. Configure PostgreSQL
Ensure PostgreSQL is running on port `5432`. Create the database and user configured in `application.yaml`:
```sql
CREATE DATABASE typeahead;
CREATE USER postgres WITH PASSWORD 'mn@h_2006';
GRANT ALL PRIVILEGES ON DATABASE typeahead TO postgres;
```

### 3. Start the Backend
Navigate to the `typeahead` directory:
```bash
cd typeahead
mvn spring-boot:run
```
Hibernate will automatically validate the schema on startup.

### 4. Start the Frontend
Navigate to the `frontend` directory:
```bash
cd frontend
npm install
npm run dev
```
Visit `http://localhost:5173` in your browser.

---

## 16. Conclusion

This project successfully demonstrates a highly scalable autocomplete architecture. By combining PostgreSQL as a reliable source of truth, Redis for in-memory reads, Redis Streams for write decoupling, batched aggregation for I/O safety, and a mathematical logarithmic approach to trend-aware ranking, the system is designed to handle immense scale gracefully.
