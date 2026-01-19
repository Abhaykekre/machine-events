# Machine Events Backend System

## Overview
This is a backend system built with **Spring Boot** and **H2 database** to handle machine events from a factory environment. The system can ingest hundreds of events concurrently, deduplicate them, handle updates, and provide statistical analytics.

---

## Table of Contents
- [Technology Stack](#technology-stack)
- [Architecture](#architecture)
- [Features](#features)
- [Setup & Run Instructions](#setup--run-instructions)
- [API Endpoints](#api-endpoints)
- [Deduplication & Update Logic](#deduplication--update-logic)
- [Thread Safety](#thread-safety)
- [Data Model](#data-model)
- [Performance Strategy](#performance-strategy)
- [Testing](#testing)
- [Edge Cases & Assumptions](#edge-cases--assumptions)
- [Future Improvements](#future-improvements)

---

## Technology Stack

- **Language**: Java 17
- **Framework**: Spring Boot 3.2.1
- **Database**: H2 (in-memory)
- **ORM**: Spring Data JPA with Hibernate
- **Build Tool**: Maven
- **Testing**: JUnit 5

---

## Architecture

The application follows a **layered architecture** with clear separation of concerns:

```
┌─────────────────────────────────────────┐
│         Controllers Layer               │  ← REST API endpoints
│   (EventController, StatsController)    │
└──────────────┬──────────────────────────┘
               │
┌──────────────▼──────────────────────────┐
│          Services Layer                 │  ← Business logic, validation
│    (EventService, StatsService)         │
└──────────────┬──────────────────────────┘
               │
┌──────────────▼──────────────────────────┐
│        Repository Layer                 │  ← Data access
│       (EventRepository)                 │
└──────────────┬──────────────────────────┘
               │
┌──────────────▼──────────────────────────┐
│           Database                      │  ← H2 in-memory
│        (machine_events table)           │
└─────────────────────────────────────────┘
```

### Component Responsibilities

1. **Controllers**: Handle HTTP requests, parameter validation, response formatting
2. **Services**: Business logic, event validation, deduplication, statistics calculation
3. **Repository**: Database queries using Spring Data JPA
4. **Model/Entity**: Database entity mapping with JPA annotations
5. **DTOs**: Data transfer objects for request/response

---

## Features

✅ **Batch Event Ingestion**: Process hundreds of events in a single request  
✅ **Deduplication**: Detect and ignore duplicate events based on eventId  
✅ **Update Logic**: Update existing events when payload differs  
✅ **Validation**: Reject invalid events (negative duration, future timestamps, etc.)  
✅ **Thread-Safe**: Handles concurrent requests without data corruption  
✅ **Statistics**: Query event counts, defect rates, and health status  
✅ **Top Defect Lines**: Identify production lines with highest defect rates  
✅ **Performance**: Process 1000 events in under 1 second

---

## Setup & Run Instructions

### Prerequisites
- Java 17 or higher
- Maven 3.6+
- No external database needed (H2 is embedded)

### Steps

1. **Clone/Download the project**
   ```bash
   cd machine-events
   ```

2. **Build the project**
   ```bash
   mvn clean install
   ```

3. **Run the application**
   ```bash
   mvn spring-boot:run
   ```

4. **Verify it's running**
    - Application starts on: `http://localhost:8080`
    - H2 Console: `http://localhost:8080/h2-console`
        - JDBC URL: `jdbc:h2:mem:factorydb`
        - Username: `sa`
        - Password: (leave empty)

### Run Tests
```bash
mvn test
```

---

## API Endpoints

### 1. Batch Event Ingestion

**Endpoint**: `POST /events/batch`

**Request Body**:
```json
[
  {
    "eventId": "E-1",
    "eventTime": "2026-01-15T10:12:03.123Z",
    "machineId": "M-001",
    "durationMs": 1000,
    "defectCount": 5,
    "lineId": "LINE-1",
    "factoryId": "F01"
  }
]
```

**Response**:
```json
{
  "accepted": 1,
  "deduped": 0,
  "updated": 0,
  "rejected": 0,
  "rejections": []
}
```

### 2. Query Statistics

**Endpoint**: `GET /stats`

**Parameters**:
- `machineId`: Machine identifier (e.g., M-001)
- `start`: Start time (inclusive) - ISO 8601 format
- `end`: End time (exclusive) - ISO 8601 format

**Example**:
```
GET /stats?machineId=M-001&start=2026-01-15T00:00:00Z&end=2026-01-15T23:59:59Z
```

**Response**:
```json
{
  "machineId": "M-001",
  "start": "2026-01-15T00:00:00Z",
  "end": "2026-01-15T23:59:59Z",
  "eventsCount": 100,
  "defectsCount": 15,
  "avgDefectRate": 0.63,
  "status": "Healthy"
}
```

### 3. Top Defect Lines

**Endpoint**: `GET /stats/top-defect-lines`

**Parameters**:
- `factoryId`: Factory identifier
- `from`: Start time (inclusive)
- `to`: End time (exclusive)
- `limit`: Number of results (default: 10)

**Example**:
```
GET /stats/top-defect-lines?factoryId=F01&from=2026-01-15T00:00:00Z&to=2026-01-16T00:00:00Z&limit=5
```

**Response**:
```json
[
  {
    "lineId": "LINE-1",
    "totalDefects": 45,
    "eventCount": 150,
    "defectsPercent": 30.0
  }
]
```

---

## Deduplication & Update Logic

### How It Works

1. **Payload Comparison**: Events with the same `eventId` are compared using all fields:
    - eventTime, machineId, durationMs, defectCount, lineId, factoryId

2. **Decision Logic**:
    - **Same eventId + Identical payload** → **Deduplicate** (ignore)
    - **Same eventId + Different payload + newer receivedTime** → **Update** existing record
    - **Same eventId + Different payload + older receivedTime** → **Ignore** (keep existing)

3. **Implementation Details**:
   ```java
   // In EventService.java
   
   // Fetch existing events in bulk
   Map<String, MachineEvent> existingEvents = 
       eventRepository.findByEventIdIn(eventIds)
           .stream()
           .collect(Collectors.toMap(MachineEvent::getEventId, e -> e));
   
   // For each incoming event
   if (existing.hasSamePayload(incoming)) {
       // Exact duplicate - ignore
       deduped++;
   } else {
       // Different payload - check receivedTime
       if (incoming.getReceivedTime().isAfter(existing.getReceivedTime())) {
           updateEvent(existing, incoming);
           updated++;
       } else {
           deduped++; // Older update - ignore
       }
   }
   ```

### Key Points
- `receivedTime` is set by the server (not trusted from client)
- Last-write-wins strategy based on server timestamp
- Bulk fetch to avoid N+1 query problem

---

## Thread Safety

### Mechanisms Used

1. **ConcurrentHashMap for Event-Level Locks**
   ```java
   private final ConcurrentHashMap<String, Object> eventLocks = new ConcurrentHashMap<>();
   
   Object lock = eventLocks.computeIfAbsent(eventId, k -> new Object());
   synchronized (lock) {
       // Critical section - only one thread per eventId
   }
   ```
    - Each `eventId` gets its own lock
    - Prevents race conditions during duplicate checking
    - Better performance than global locking

2. **Optimistic Locking with @Version**
   ```java
   @Entity
   public class MachineEvent {
       @Version
       private Long version;
   }
   ```
    - Database-level concurrency control
    - Prevents lost updates in concurrent scenarios
    - Automatic retry on version conflicts

3. **Transaction Management**
   ```java
   @Transactional
   public BatchResponse processBatch(List<EventRequest> requests) {
       // All operations are atomic
   }
   ```
    - ACID properties guaranteed
    - Rollback on failures

4. **Bulk Operations**
    - Events processed in batches to reduce lock contention
    - Single database transaction per batch

### Why This Approach?

- **Scalability**: Per-event locks allow parallel processing of different events
- **Correctness**: Synchronized blocks ensure no race conditions
- **Performance**: Bulk operations reduce overhead
- **Safety**: Optimistic locking catches any missed edge cases

---

## Data Model

### Database Schema

```sql
CREATE TABLE machine_events (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    event_id VARCHAR(100) UNIQUE NOT NULL,
    event_time TIMESTAMP NOT NULL,
    received_time TIMESTAMP NOT NULL,
    machine_id VARCHAR(50) NOT NULL,
    duration_ms BIGINT NOT NULL,
    defect_count INTEGER NOT NULL,
    line_id VARCHAR(50),
    factory_id VARCHAR(50),
    version BIGINT  -- for optimistic locking
);

-- Indexes for performance
CREATE UNIQUE INDEX idx_event_id ON machine_events(event_id);
CREATE INDEX idx_machine_time ON machine_events(machine_id, event_time);
CREATE INDEX idx_line_time ON machine_events(line_id, event_time);
```

### Design Decisions

- **Unique constraint on eventId**: Enforces deduplication at database level
- **Composite indexes**: Optimizes time-range queries for stats
- **Version field**: Enables optimistic locking
- **Nullable lineId/factoryId**: Supports optional fields for extensibility

---

## Performance Strategy

### Optimizations Implemented

1. **Hibernate Batch Processing**
   ```properties
   spring.jpa.properties.hibernate.jdbc.batch_size=50
   spring.jpa.properties.hibernate.order_inserts=true
   spring.jpa.properties.hibernate.order_updates=true
   ```
    - Groups multiple inserts/updates into single SQL statement
    - Reduces database round trips by ~80%

2. **Bulk Queries**
   ```java
   // Fetch all relevant events in one query
   List<MachineEvent> existing = eventRepository.findByEventIdIn(eventIds);
   ```
    - Eliminates N+1 query problem
    - Single SELECT with IN clause instead of N individual SELECTs

3. **In-Memory Deduplication**
    - Payload comparison happens in application memory
    - Only validated events are written to database
    - Reduces unnecessary DB operations

4. **Strategic Indexing**
    - Indexes on frequently queried columns
    - Covers both point lookups (eventId) and range scans (time queries)

5. **Connection Pooling**
    - HikariCP (Spring Boot default) provides efficient connection management
    - Reuses connections instead of creating new ones

### Performance Results

- **Target**: 1000 events in < 1 second
- **Achieved**: ~450ms for 1000 events
- **Throughput**: ~2,200 events/second
- See [BENCHMARK.md](BENCHMARK.md) for detailed results

---

## Testing

### Test Coverage

The project includes **8 comprehensive tests** covering all requirements:

1. **testIdenticalDuplicateIsDeduped**: Verifies duplicate detection
2. **testDifferentPayloadNewerReceivedTimeUpdates**: Tests update with newer timestamp
3. **testDifferentPayloadOlderReceivedTimeIgnored**: Tests update rejection with older timestamp
4. **testInvalidDurationRejected**: Validates duration constraints
5. **testFutureEventTimeRejected**: Validates time constraints
6. **testDefectCountMinusOneIgnoredInStats**: Tests special defect handling
7. **testStartEndBoundaryCorrectness**: Tests inclusive/exclusive boundaries
8. **testConcurrentIngestionThreadSafety**: Tests thread safety with concurrent requests

### Running Tests

```bash
# Run all tests
mvn test

# Run specific test
mvn test -Dtest=EventServiceTest#testInvalidDurationRejected

# Run with coverage
mvn test jacoco:report
```

---

## Edge Cases & Assumptions

### Handled Edge Cases

1. **Concurrent updates to same event**: Last received (by server time) wins
2. **Empty batch**: Returns all zeros in response
3. **All duplicates**: Returns deduped count, no DB writes
4. **Mixed valid/invalid**: Processes valid, rejects invalid with reasons
5. **Boundary conditions**: start inclusive, end exclusive (as per requirement)
6. **defectCount = -1**: Stored but excluded from calculations

### Assumptions

1. **receivedTime**: Always set by server, client value ignored/overridden
2. **Time zones**: All timestamps in UTC (ISO-8601 format)
3. **eventId uniqueness**: Globally unique across all machines/factories
4. **lineId/factoryId**: Optional fields for future extensibility
5. **Health threshold**: avgDefectRate < 2.0 per hour = "Healthy"

### Validation Rules

- `durationMs`: Must be >= 0 and <= 6 hours (21,600,000 ms)
- `eventTime`: Cannot be more than 15 minutes in the future
- `eventId`, `machineId`, `eventTime`: Required fields
- `defectCount`: -1 means "unknown", 0+ means actual count

---

## Future Improvements

Given more time, I would implement:

1. **Production Database**
    - Switch from H2 to PostgreSQL/MySQL
    - Add connection pooling configuration
    - Database migration with Flyway/Liquibase

2. **Caching Layer**
    - Redis for frequently accessed stats
    - Cache invalidation on new events
    - Reduce database load for repeated queries

3. **Asynchronous Processing**
    - Message queue (Kafka/RabbitMQ) for event ingestion
    - Decouple API from processing
    - Better handling of spikes in traffic

4. **API Documentation**
    - Swagger/OpenAPI specification
    - Interactive API explorer
    - Request/response examples

5. **Monitoring & Observability**
    - Metrics with Micrometer/Prometheus
    - Distributed tracing with Zipkin
    - Health check endpoints
    - Performance dashboards

6. **Security**
    - API authentication (JWT/OAuth2)
    - Rate limiting per client
    - Input sanitization
    - HTTPS enforcement

7. **Enhanced Features**
    - Pagination for large result sets
    - Filtering and sorting options
    - Data export (CSV/Excel)
    - Scheduled reports

8. **Data Management**
    - Soft deletes with audit trail
    - Data archival strategy
    - Backup and recovery procedures
    - Data retention policies

---

## Known Limitations

1. **In-memory database**: Data is lost on restart (intentional for assignment)
2. **Single instance**: No distributed deployment support
3. **No authentication**: Public endpoints (would add Spring Security in production)
4. **No rate limiting**: Could be overwhelmed by excessive requests
5. **Limited error recovery**: Failed events are not automatically retried

---

## Project Structure

```
machine-events/
├── src/
│   ├── main/
│   │   ├── java/com/factory/machineevents/
│   │   │   ├── MachineEventsApplication.java
│   │   │   ├── controller/
│   │   │   │   ├── EventController.java
│   │   │   │   └── StatsController.java
│   │   │   ├── service/
│   │   │   │   ├── EventService.java
│   │   │   │   └── StatsService.java
│   │   │   ├── repository/
│   │   │   │   └── EventRepository.java
│   │   │   ├── model/
│   │   │   │   └── MachineEvent.java
│   │   │   └── dto/
│   │   │       ├── EventRequest.java
│   │   │       ├── RejectionDetail.java
│   │   │       ├── BatchResponse.java
│   │   │       ├── StatsResponse.java
│   │   │       └── TopDefectLineResponse.java
│   │   └── resources/
│   │       └── application.properties
│   └── test/
│       └── java/com/factory/machineevents/
│           └── EventServiceTest.java
├── pom.xml
├── README.md
└── BENCHMARK.md
```

---

## Contributing

This is an assignment project. For questions or issues, please contact the developer.

---

## License

This project is submitted as part of a technical assignment.

---

### Hardware
- **CPU**: Ryzen 7 4th Gen
- **RAM**: 8 GB
- **OS**: Windows 11

## Contact

**Developer**: Abhay Kekre  
**Email**: abhaykekre9@gmai.com  
**Date**: January 2026
