# Machine Factory Backend

## Overview
Backend service for ingesting machine events and serving production statistics.

## Architecture
- **Controllers**: REST endpoints for ingest and stats.
- **Services**: Validation, dedupe/update logic, and business calculations.
- **Repository (JPA)**: Database access and aggregation queries.
- **Database**: PostgreSQL with a single `machine_events` table.

## Data Model
**Table**: `machine_events`
- `event_id` (PK)
- `event_time`
- `received_time`
- `machine_id`
- `duration_ms`
- `defect_count` (nullable; `null` means unknown)
- `line_id`
- `factory_id`

## Dedupe & Update Logic
- **Dedup**: Same `eventId` + identical payload → ignore (counted as `deduped`).
- **Update**: Same `eventId` + different payload → update if the incoming `receivedTime` is newer.
- **Older `receivedTime`**: If incoming `receivedTime` is older than stored, the update is ignored.
- `receivedTime` from the request is ignored and set by the service on ingest.

## Validation Rules
- Reject if `durationMs < 0` or `durationMs > 6 hours`.
- Reject if `eventTime` is more than 15 minutes in the future.
- `defectCount = -1` → stored as `null` and ignored for defect calculations.

## Stats Calculations
- **Window**: `start` inclusive, `end` exclusive.
- `eventsCount`: total valid events in window.
- `defectsCount`: sum of defects (ignoring `null`).
- `avgDefectRate = defectsCount / windowHours`.
- **status**: `Healthy` if avg defect rate < 2.0, else `Warning`.

## Thread Safety
- `eventId` is the primary key, ensuring uniqueness at the DB level.
- Batch ingestion runs in a transaction to keep dedupe/update behavior consistent.
- Concurrent requests are safe because inserts/updates target the same primary key rows.

## Performance Strategy
- Fast, in-memory validation before any DB access.
- Dedupe with in-memory map per batch.
- Batch writes via `saveAll` with Hibernate batch settings.
- Batch configuration details are documented in [BENCHMARK.md](BENCHMARK.md) and set in [src/main/resources/application.properties](src/main/resources/application.properties).

## Edge Cases & Assumptions
- `receivedTime` in requests is ignored and replaced by server time.
- `defectCount = -1` is stored as `null` and excluded from defect totals.
- If two events with the same `eventId` arrive, the newer `receivedTime` wins.

## Setup & Run
1. Ensure PostgreSQL is running.
2. Update DB config in [src/main/resources/application.properties](src/main/resources/application.properties).
3. Run the app:
   - `./mvnw spring-boot:run`

## Tests
Run all tests:
- `./mvnw test`

Key tests include:
- Deduplication
- Updates by newer `receivedTime`
- Rejections for invalid duration and future event times
- Defect count handling for `-1`
- Boundary correctness for time windows
- Concurrency safety
- 1000-event performance benchmark

Test suite: [src/test/java/com/example/machine_factory/service/MachineEventServiceTest.java](src/test/java/com/example/machine_factory/service/MachineEventServiceTest.java)

## Improvements (If More Time)
- Move dedupe/update to a single UPSERT query for stricter concurrency guarantees.
- Add pagination and filtering for stats endpoints.
- Add structured logging and request tracing.
- Add API schema validation (e.g., OpenAPI + validation annotations).
