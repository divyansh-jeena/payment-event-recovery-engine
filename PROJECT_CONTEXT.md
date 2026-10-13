# PROJECT_CONTEXT.md

## Scope and evidence

This handoff describes the repository as inspected on 2026-09-26. It is based on files under `src/`, the root build/configuration/documentation files, and the checked-in workspace layout. `target/` contains generated class files and is excluded from the source inventory. No source files were changed to prepare this document. Where behavior depends on active Spring configuration, schema generation, or runtime state, uncertainty is called out.

## 1. Project overview

**Project name:** Payment Event Recovery Engine (Maven artifact `Payment-Event-Recovery-Engine`, version `0.0.1-SNAPSHOT`).

**Purpose as implemented:** a Spring MVC service accepts payment-like webhook payloads, stores each event in PostgreSQL through Spring Data JPA, and makes a simulated downstream delivery attempt. Failures get a scheduled retry time and may eventually reach `DEAD`; REST endpoints expose event lookup/filtering, manual retry, audit history, and aggregate counts.

**Problem addressed:** demonstrate persistence, delivery attempt tracking, retry scheduling, and simple business audit history for payment events. Despite the name/README, this implementation is an in-process demonstration rather than a durable distributed event-processing system.

**Implemented:** REST ingress and query endpoints; bean validation on ingress; duplicate check; JPA persistence; random success/failure simulator; retry scheduling with exponential-minute delays and a five-attempt terminal threshold; manual retry of `FAILED`/`DEAD`; audit rows; pagination/status filter; metrics counts; global handlers for missing and duplicate payments; console/file logging configuration.

**Not implemented:** a real payment gateway or downstream Order Service client; Kafka/message broker; separate DLQ; authentication/authorization; frontend/dashboard; OpenAPI/Swagger; Docker/deployment manifests; database migration scripts; robust distributed delivery guarantees/outbox/idempotency beyond payment ID uniqueness; configurable retry policy; metrics instrumentation beyond a REST count DTO.

**Maturity:** early prototype/demo. Several routes and core paths exist, but automated coverage, configuration externalization, real downstream integration, security, and production concurrency/operations features are absent. README claims should not be treated as authoritative where they conflict with source.

## 2. Complete technology stack

### Backend

- Java 21 configured via `pom.xml` (`java.version`). README says Java 17, stale relative to build.
- Spring Boot 4.0.6 parent; Spring MVC/WebMVC, Spring Data JPA, Spring scheduling, Bean Validation, and Actuator starter dependencies.
- Hibernate/JPA annotations map entities. Spring Data derives database queries from repository method names.
- Lombok (`@Getter`, `@Setter`, `@Slf4j`, `@RequiredArgsConstructor`) with Maven annotation processor.

### Database

- PostgreSQL JDBC driver at runtime; default URL `jdbc:postgresql://localhost:5432/PaymentRecovery`.
- Flyway Spring Boot starter and PostgreSQL Flyway module are dependencies, but no migration files are present. `spring.jpa.hibernate.ddl-auto=update` is configured. Migration execution/state is not represented by repository scripts; assess Spring Boot/Flyway startup interaction in the chosen runtime.

### Frontend

- None found; no frontend manifests or source directories.

### Security

- None found. No Spring Security dependency, filters, auth configuration, JWT, roles, or endpoint protection code.

### Messaging

- None. No Kafka, RabbitMQ, Redis, JMS, or other broker dependency/code.

### Testing

- JUnit Jupiter and Spring Boot test starter modules arrive through test-scoped Boot starters (`actuator-test`, `data-jpa-test`, `flyway-test`, `validation-test`, `webmvc-test`). Sole visible test is a Spring `@SpringBootTest` context smoke test.

### Build tools

- Maven and Maven Wrapper scripts (`mvnw`, `mvnw.cmd`); Spring Boot Maven plugin; Maven compiler plugin; Java 21.

### Infrastructure

- No Dockerfile, Compose file, Kubernetes manifests, CI workflow, or deployment scripts found. PostgreSQL must be provided externally/local to the app.

### Documentation

- `README.md`, generated-template `HELP.md`, and this handoff. No Swagger/OpenAPI configuration or API schema found.

### Other libraries

- SLF4J API/Logback via Spring Boot logging; `java.util.Random` drives simulated delivery. Actuator is a declared dependency, but management endpoint exposure/customization is not configured in source.

## 3. Complete project structure

```text
.
├── .gitignore
├── .gitattributes
├── .mvn/                         Maven Wrapper support
├── mvnw / mvnw.cmd               Maven Wrapper launchers
├── pom.xml
├── README.md
├── HELP.md                       generated Spring reference links
├── src/
│   ├── main/
│   │   ├── java/com/example/paymenteventrecoveryengine/
│   │   │   ├── PaymentEventRecoveryEngineApplication.java
│   │   │   ├── Service/          business services; capital S is literal package
│   │   │   ├── controller/       REST controllers
│   │   │   ├── dto/              request/response records
│   │   │   ├── entity/           JPA entities
│   │   │   ├── enums/            payment/audit constants
│   │   │   ├── exception/        exceptions, error record, advice
│   │   │   └── repository/       Spring Data repositories
│   │   └── resources/
│   │       ├── application.properties
│   │       └── application.yml
│   └── test/java/com/example/paymenteventrecoveryengine/
│       └── PaymentEventRecoveryEngineApplicationTests.java
├── logs/                         configured runtime log destination (ignored)
├── target/                       generated build output (ignored)
└── .idea/                        IDE metadata (ignored)
```

No `schema.sql`, Flyway `db/migration`, static frontend, test fixtures, or additional Java packages are present. Root Java package scan is `com.example.paymenteventrecoveryengine`; the services package is unusually capitalized as `Service` and imports must preserve it.

## 4. Architecture

```text
HTTP client
  ├─ POST /api/webhooks/payments → WebhookController → WebhookService
  │                                      ├─ PaymentRepository (duplicate + insert)
  │                                      ├─ AuditLogRepository (received audit)
  │                                      └─ DeliveryService → random local simulator
  │                                           ├─ PaymentRepository (state updates)
  │                                           └─ AuditService → AuditLogRepository
  └─ /api/payments/** → PaymentController → PaymentService → repositories/services

RetryScheduler (every minute) → PaymentRepository due FAILED query → DeliveryService
PostgreSQL ← Spring Data JPA/Hibernate entities
```

This is a synchronous, single-process Spring application. Ingress saves the payment and initial audit, then calls delivery before returning. `DeliveryService.deliver` is `@Transactional`; repository operations and audit writes in that call participate in its transaction when invoked through the Spring proxy. `WebhookService.processPayment` has no transaction annotation, so initial payment and received-audit saves are separate repository transactions before delivery begins. No lock/claim step is visible for scheduled rows, and no asynchronous executor or message broker is used.

`@EnableScheduling` enables the scheduled method. No explicit scheduler pool, timezone, transaction manager, or retry configuration is set.

## 5. Complete request and execution flows

### Ingress: `POST /api/webhooks/payments`

1. Spring MVC binds JSON into `PaymentRequest`; `@Valid` invokes Jakarta constraints: payment/order IDs and currency must be nonblank; amount non-null and at least `0.01`.
2. `WebhookService.processPayment` logs the ID and calls `existsByPaymentId`.
3. If present, throws `DuplicatePaymentException`; advice returns 409 `ErrorResponse`.
4. Otherwise maps four request fields to a new `PaymentEvent`, explicitly sets `PENDING`, retry count 0, null failure reason; saves it.
5. Directly creates/saves `AuditLog` action string `PAYMENT_RECEIVED` and message `Payment webhook received successfully`.
6. Calls `DeliveryService.deliver` synchronously. It writes `DELIVERY_ATTEMPTED`, chooses success/failure randomly, then updates payment and audit as detailed in Sections 13–15.
7. If execution completes, controller returns HTTP 200 text `Payment accepted successfully`, even if simulated delivery failed or became DEAD. Validation errors use Spring's default MVC handling; no custom validation handler exists. Database/runtime failures are not mapped by custom advice.

### Payment query routes

`PaymentController` delegates to `PaymentService`. List route defaults page 0/size 10; optional `status` binds to `PaymentStatus`. `PageRequest.of` rejects negative page or nonpositive size (framework exception; custom error mapping absent). Data maps to `PaymentResponse`; default Spring `Page` JSON structure is returned. Single-item and audit routes first find by payment ID; absence raises `PaymentNotFoundException` and maps to 404.

### Manual retry

`POST /api/payments/{paymentId}/retry` finds the event. Only `FAILED`/`DEAD` accepted. It writes `MANUAL_RETRY_STARTED`; `DEAD` is set to `FAILED` in memory; then synchronous `deliver` runs. It returns HTTP 200 text when that call completes, including if delivery results in another failure/DEAD. For other states `IllegalArgumentException` is thrown and has no explicit handler, so default framework 500 behavior is expected (exact body depends on Boot configuration).

### Scheduled retry

Every minute at cron `0 * * * * *`, `RetryScheduler` obtains current local time and queries status `FAILED` with `nextRetryAt <= now`; each result is delivered synchronously, catching/logging exceptions per item. See Section 14 for retry details.

### Metrics and dead list

Metrics perform five repository count queries, one total and one per state. Dead listing is ordinary paginated `findByStatus(DEAD, pageable)`.

## 6. Database

There are two entity mappings; no checked-in DDL/migration establishes exact vendor-specific types or indexes. Hibernate naming strategy determines physical column names from Java field names. Explicit table names are `payment_events` and `audit_logs`.

| Entity/table | Columns and mapping | Constraints/relations |
|---|---|---|
| `PaymentEvent` / `payment_events` | `id` Long identity PK; `paymentId` String; `orderId` String; `amount` BigDecimal; `currency` String length 3; `retryCount` Integer initialized 0; `status` `PaymentStatus` stored as string and initialized PENDING; `failureReason` String; `nextRetryAt`, `createdAt`, `updatedAt` LocalDateTime. Creation/update timestamps use Hibernate annotations. | `paymentId` non-null and unique; `orderId`, `amount`, `currency` non-null. No explicit relationship to audit entity. No explicit indexes apart from unique payment ID. Other nullability/precision/length and generated DDL details are UNKNOWN without runtime schema inspection. No cascade behavior. |
| `AuditLog` / `audit_logs` | `id` Long identity PK; `paymentId`, `action`, `message` Strings; `createdAt` LocalDateTime with `@CreationTimestamp`. | No explicit not-null, length, foreign-key, index, or relationship mapping. `paymentId` is a plain string, not a JPA association. |

Retry query filters status and due timestamp; audit retrieval filters payment ID and orders ascending by creation timestamp. Derived count/status queries support dashboard counts and filter pages. No explicit index for these access paths is declared. No DB-level foreign key connects audit rows to payment rows in entity code.

## 7. Entities

### `entity.PaymentEvent`

JPA `@Entity`, `@Table(name="payment_events")`, Lombok getters/setters. Fields/mappings are listed above. Defaults initialize retry count and status on Java construction. `@CreationTimestamp` and `@UpdateTimestamp` supply lifecycle times at Hibernate persistence/update. No callbacks, validation annotations, custom methods, version column, or explicit relationship. There is an unused `lombok.Builder` import; no builder annotation exists.

### `entity.AuditLog`

JPA `@Entity`, `@Table(name="audit_logs")`, Lombok getters/setters. Fields/mapping above; creation timestamp Hibernate-generated. No entity association, action enum mapping, validation, update timestamp, or custom method.

## 8. Enums and state transitions

`PaymentStatus` values: `PENDING` (new event), `PROCESSING` (declared but never set in current code), `DELIVERED` (simulated delivery success), `FAILED` (retryable after a failed attempt), `DEAD` (attempt threshold reached). Stored as enum names via `EnumType.STRING`.

Observed transitions: creation → PENDING → DELIVERED or FAILED/DEAD on first delivery; FAILED → DELIVERED or FAILED/DEAD on due retry; DEAD → FAILED in manual retry before delivery, then success/failure; FAILED may be manually retried without reset. Success resets retry count to zero. Retry failure increments count. No explicit transition validation/state machine exists.

`AuditAction` values: `PAYMENT_RECEIVED`, `DELIVERY_ATTEMPTED`, `DELIVERY_SUCCESSFUL`, `DELIVERY_FAILED`, `RETRY_STARTED`, `RETRY_SUCCESSFUL`, `RETRY_FAILED`, `MOVED_TO_DLQ`, `MANUAL_RETRY_STARTED`. It is not stored as an enum field. Code uses only PAYMENT_RECEIVED, DELIVERY_ATTEMPTED, DELIVERY_SUCCESSFUL, DELIVERY_FAILED, MOVED_TO_DLQ, MANUAL_RETRY_STARTED. The `RETRY_*` values are currently unused.

## 9. DTOs

- `PaymentRequest` record: `paymentId`, `orderId`, `amount`, `currency`; validation as in Section 5. Enters at webhook endpoint. No currency format/case/ISO validation or upper bounds.
- `PaymentResponse` record: paymentId, orderId, amount, currency, status String, retryCount, failureReason. Built by private `PaymentService.toResponse`; excludes database ID/timestamps/next retry timestamp.
- `AuditLogResponse` record: action, message, createdAt. Produced from ordered audit entities.
- `MetricsResponse` record: totalPayments, pending, failed, delivered, dead (longs); does not include PROCESSING count.
- `ErrorResponse` record (exception package): message, numeric status, timestamp. Used only by two explicit exception handlers.

No mapper library; mapping is hand-coded in `PaymentService` and `WebhookService`.

## 10. Repositories

- `PaymentRepository extends JpaRepository<PaymentEvent, Long>`: `findByPaymentId`, `existsByPaymentId`, paged `findByStatus`, due `findByStatusAndNextRetryAtBefore`, due `findByStatusAndNextRetryAtLessThanEqual`, `countByStatus`; inherited `findAll(Pageable)` and `count()`. Scheduler uses the LessThanEqual method; the Before variant is unused. No explicit `@Query` or index declaration.
- `AuditLogRepository extends JpaRepository<AuditLog, Long>`: `findByPaymentIdOrderByCreatedAtAsc`; used for history. No explicit query/index.
- `WebhookRepository extends JpaRepository<PaymentEvent, Long>`: no custom methods; unused by current services/controllers. Its name suggests webhook-specific storage but it targets the payment entity.

Useful index candidates based on actual query shape would be status/nextRetryAt and audit paymentId/createdAt; these are not declared in code and no migration exists.

## 11. Services

- `Service.WebhookService`: duplicate existence check; maps and saves event; writes initial audit directly through `AuditLogRepository`; calls delivery synchronously. It does not return created event and is not transaction-annotated.
- `Service.DeliveryService`: `@Transactional deliver`; writes attempt audit; calls private simulator (`Random.nextBoolean()`); success sets DELIVERED, clears failure and next retry, resets retry count 0, saves, audits success. Failure increments retry count and records literal `Order Service Timeout`; at count >= 5 sets DEAD, clears next retry, saves, logs `MOVED_TO_DLQ` with maximum-limit message (no physical queue); otherwise sets FAILED, calculates delay `2^(retryCount-1)` minutes, sets next retry, saves and audits delivery failure. `MAX_RETRY_COUNT=5` is hard-coded. No external call or configurable timeout.
- `Service.RetryScheduler`: scheduled every minute; fetches eligible rows and invokes delivery one by one. Exceptions are caught/logged; no explicit claim/lock, batch limit, or schedule property.
- `Service.PaymentService`: fetches one/list/status/dead, hand-maps DTOs, retrieves audit records, enforces manual retry statuses, writes manual-retry audit through `AuditService`, calls delivery, calculates counts. `getPaymentLogs` verifies event existence before audit query.
- `Service.AuditService`: creates and saves audit row with payment ID/action/message and logs it. No transaction annotation; when called from transactional DeliveryService, Spring repository transaction participation applies. It does not validate action or enforce an associated payment.

## 12. Controllers and APIs

All routes have no visible authentication requirements; no security layer is present. Most successful routes return 200 via `ResponseEntity.ok`.

| Method | Endpoint | Controller | Purpose | Request | Response | Errors |
|---|---|---|---|---|---|---|
| POST | `/api/webhooks/payments` | WebhookController | Accept event and immediately attempt simulated delivery | JSON `PaymentRequest`; validation | 200 text `Payment accepted successfully` | Duplicate 409 ErrorResponse; invalid body default 400; other errors framework default |
| GET | `/api/payments` | PaymentController | List all or filter by status | optional `status`; `page=0`, `size=10` defaults | `Page<PaymentResponse>` | Invalid enum/page values default framework errors |
| GET | `/api/payments/{paymentId}` | PaymentController | Fetch one event | path ID | `PaymentResponse` | 404 ErrorResponse when absent |
| GET | `/api/payments/{paymentId}/logs` | PaymentController | Get audit history oldest first | path ID | list of `AuditLogResponse` | 404 if event absent |
| POST | `/api/payments/{paymentId}/retry` | PaymentController | Retry FAILED/DEAD event | path ID; no body | 200 text `Manual retry initiated.` | 404 if absent; IllegalArgumentException for other status is not custom mapped |
| GET | `/api/payments/dead` | PaymentController | Paged DEAD events | page/size defaults | `Page<PaymentResponse>` | invalid paging default framework errors |
| GET | `/api/payments/metrics` | PaymentController | Count totals by selected statuses | none | `MetricsResponse` | repository/database errors default handling |

Static-looking routes (`/dead`, `/metrics`) coexist with `/{paymentId}`; Spring's route specificity handles literal routes. No endpoint exposes `PROCESSING` metrics or next retry timestamp.

## 13. Payment event lifecycle

```text
POST JSON → validation → existsByPaymentId → save PENDING + received audit
          → synchronous random delivery attempt + attempt audit
              ├─ success → DELIVERED, clear reason/time, retryCount=0, success audit
              └─ failure → retryCount++, failureReason
                    ├─ count < 5 → FAILED, nextRetryAt = now + 2^(count−1) minutes, failure audit
                    └─ count >= 5 → DEAD, nextRetryAt null, DLQ audit (database state only)

FAILED due event → minute scheduler → delivery attempt → same outcome logic
FAILED/DEAD manual endpoint → manual audit; DEAD first becomes FAILED → delivery attempt
```

No payment processor, real downstream service, durable broker, separate DLQ, or manual recovery UI exists. The request response is not a delivery result and can say accepted even when delivery failed.

## 14. Retry system

- Trigger: scheduled method `retryFailedPayments` on cron `0 * * * * *` (second zero of every minute); manual endpoint for FAILED/DEAD; initial synchronous delivery is also the first attempt.
- Eligibility: `PaymentStatus.FAILED` and `nextRetryAt <= LocalDateTime.now()` using `findByStatusAndNextRetryAtLessThanEqual`.
- Count: failure increments count. On fifth failed delivery count reaches threshold and status becomes DEAD. Initial delivery is counted as retry count 1 on failure; naming therefore counts failed attempts, not only retries after the first attempt.
- Delay after failures 1–4: 1, 2, 4, 8 minutes respectively (`2^(retryCount-1)`). There is no 16-minute scheduled retry after count 5 because the event goes DEAD immediately.
- Success resets count to zero and clears failure reason and retry time.
- Scheduler query excludes DEAD and PENDING. It processes all due records without page limit/claiming. `findByStatusAndNextRetryAtBefore` is a duplicate unused query.
- Manual retry of FAILED leaves its count intact until success or another failure. Manual retry of DEAD changes it to FAILED and retains count (normally >=5) before delivery. If that delivery fails, count increments again and immediately meets/exceeds the DEAD threshold. Success resets to zero.
- Concurrency risk: no row lock, optimistic version, or atomic status claim is visible. Multiple application instances or overlapping scheduler execution could select the same due event. A default single scheduler execution is normally serial, but request-triggered manual retry can overlap scheduled processing. Runtime behavior is not verified.
- `LocalDateTime` is used without explicit timezone policy. Scheduler catches exceptions around each event, but a transaction failure may leave no persisted attempt/audit depending on rollback.

## 15. Failure simulation

Implemented inside `DeliveryService.simulateOrderService()`: `new Random().nextBoolean()` yields a 50/50 boolean decision. No downstream service, URL, HTTP client, response code, failure taxonomy, or timeout is actually used. Every false outcome is labeled `Order Service Timeout`, but no timeout occurs. The simulator has no deterministic controls/configuration.

## 16. External services

No external/downstream service client exists. The “Order Service” is only mentioned in audit/log text and in the simulated failure reason. URL, protocol, payload, authentication, timeout, response format, and network retry are UNKNOWN — not determinable from the current repository because no actual integration is present.

## 17. Audit logging

Business audit is represented by `AuditLog` rows and `AuditLogRepository`, written via `AuditService` except the initial ingress audit, which `WebhookService` creates directly. Stored fields: generated ID, paymentId, action string, message, Hibernate creation time. History endpoint verifies the payment then returns records sorted ascending by createdAt.

Observed records: PAYMENT_RECEIVED; DELIVERY_ATTEMPTED; DELIVERY_SUCCESSFUL; DELIVERY_FAILED; MOVED_TO_DLQ; MANUAL_RETRY_STARTED. Retry enum constants exist but no retry-specific audit rows are written by scheduler. No audit retention, actor identity, transaction correlation, foreign key, or append-only enforcement exists.

Technical logs are SLF4J calls emitted by services/advice and configured to console and `logs/payment-recovery.log`; they are operational text records, separate from queryable business audit rows. Log format is timestamp/thread/level/logger/message. Root level INFO and application package DEBUG.

## 18. Security

No Spring Security dependency/configuration or authentication/authorization code exists. All endpoints are unprotected at application-code level. No JWT, roles, password hashing/handling, filters, CORS policy, CSRF policy, or security exception handlers are configured. Network-level protections are outside repository evidence and UNKNOWN — not determinable from the current repository.

## 19. Error handling

`PaymentNotFoundException` and `DuplicatePaymentException` are runtime exceptions. `GlobalExceptionHandler` (`@RestControllerAdvice`) maps them to `ErrorResponse(message,status,timestamp)` and HTTP 404 / 409 respectively, with log entries. Validation failures, `IllegalArgumentException`, malformed enum values, paging errors, database integrity errors, and unexpected runtime errors have no explicit custom mapping here and fall through to Spring Boot MVC defaults. Exact default error payload/configuration is not customized in repository code.

## 20. Frontend

Not currently implemented. No React or other UI source, package manifest, routes, browser API client, dashboard, or frontend authentication exists. Metrics/dashboard functionality is a backend count endpoint only.

## 21. Testing

One test: `PaymentEventRecoveryEngineApplicationTests.contextLoads`, annotated `@SpringBootTest`, with an empty test body. It intends to check application context startup. No unit tests, controller/repository integration tests, scheduler tests, retry behavior assertions, simulator determinism, test database/Testcontainers configuration, mocks, or coverage report are present. Whether the context test can pass on a clean machine depends on PostgreSQL/configuration and runtime setup; it was not executed for this read-only documentation task. Important untested behavior includes duplicate races, fifth-failure terminal transition, due-time boundary, transaction rollback, invalid DTOs, endpoint response contracts, and concurrent retries.

## 22. Docker / deployment

No Dockerfile, Compose, Kubernetes, or deployment assets exist. App defaults to port 8080 by Spring convention (no explicit server port). Database must be available at configured PostgreSQL URL. No container networking, health-check dependency, volume, or environment-variable contract is defined in project files.

## 23. Configuration

| Property | Purpose | Default/source | Used by |
|---|---|---|---|
| `spring.application.name` | App name | `Payment Event Recovery Engine` in properties | Spring Boot |
| `spring.datasource.url` | PostgreSQL connection | `jdbc:postgresql://localhost:5432/PaymentRecovery` | DataSource |
| `spring.datasource.username` | DB user | `postgres` | DataSource |
| `spring.datasource.password` | DB password | Configured in repository; value deliberately omitted (`<REDACTED>`) | DataSource |
| `spring.datasource.driver-class-name` | JDBC driver | `org.postgresql.Driver` | DataSource |
| `spring.jpa.show-sql` | Print SQL | `true` | Hibernate |
| `spring.jpa.hibernate.ddl-auto` | Schema update policy | `update` | Hibernate |
| `logging.level.root` | Root log threshold | `INFO` in YAML | Boot logging |
| `logging.level.com.example.paymenteventrecoveryengine` | Application threshold | `DEBUG` in YAML | Boot logging |
| `logging.file.name` | File output | `logs/payment-recovery.log` | Boot logging |
| `logging.pattern.console` / `.file` | Log format | timestamp/thread/level/logger/message patterns in YAML | Boot logging |
| Retry cron | Retry scan frequency | hard-coded `0 * * * * *` | `RetryScheduler` |
| Max retry count | Terminal threshold | hard-coded 5 | `DeliveryService` |

Both `application.properties` and `application.yml` are present. Spring Boot normally loads both and merges distinct keys; properties are the sole source for DB settings while YAML holds logging. They do not currently duplicate keys. No environment-variable placeholders or profile-specific config are declared. The committed database credential should be externalized/rotated operationally; it is intentionally redacted here.

## 24. Important business rules

- `paymentId` is duplicate-checked in application code and unique at the JPA column mapping. A race between check and insert can still cause a database constraint error; that exception has no custom mapping.
- Required ingress values: nonblank paymentId/orderId/currency; amount >= 0.01. Currency length/format is not validated at DTO level; DB mapping says max length 3.
- New event begins PENDING with count 0, then immediately enters synchronous simulated delivery.
- Each failed delivery increments retryCount; failure reason is currently always `Order Service Timeout`.
- Count >= 5 sets DEAD, clears nextRetryAt, and records a DLQ-named audit. There is no distinct DLQ store.
- Counts below five remain FAILED and schedule delays 1/2/4/8 minutes after failure counts 1–4.
- Successful delivery sets DELIVERED and clears failure reason/nextRetryAt, resetting retryCount to zero.
- Manual retry is permitted only for FAILED or DEAD. DEAD is first changed to FAILED; FAILED count is retained. Other statuses cause IllegalArgumentException.
- Audit history requires an existing payment and sorts by creation time ascending.
- Status filter binds enum names through Spring enum conversion; no custom case normalization exists.

## 25. Current limitations supported by source

- Random local boolean replaces real delivery; success/failure is nondeterministic and the timeout reason is fictional.
- Ingress and delivery are synchronous; caller waits for attempt and receives generic accepted text, not outcome.
- No outbox/broker or transaction joining initial payment plus first delivery; crashes between steps may leave an event without attempt history.
- No database lock/claim/version for retry selection; multiple instances/manual retries can race.
- Due-query and audit access paths have no explicit indexes in source.
- No auth, UI, migration scripts, deployment manifests, or meaningful automated tests.
- Retry limits/backoff/cron are constants; no configurable policy or jitter.
- Duplicate check is not atomic with insert; unique constraint violation is not translated to 409.
- Audit records are not linked by FK, and free-form action strings can drift from enum values.
- Metrics use multiple count queries and omit PROCESSING; no telemetry exporter or operational dashboard config.
- Database credential is committed in application properties; exact value excluded here.
- `spring.jpa.hibernate.ddl-auto=update` is used with Flyway dependencies but no migrations, leaving schema ownership unclear.

## 26. Technical debt (separate from behavior defects)

| Problem | Location | Impact | Suggested direction |
|---|---|---|---|
| Service package uses uppercase `Service` | Java package/imports | Unusual convention; careless case-sensitive refactoring breaks imports | Rename package consistently in a dedicated refactor |
| Unused repository, query, enum values/import | `WebhookRepository`; `findByStatusAndNextRetryAtBefore`; RETRY_* values; Builder import | Misleads maintainers about actual design | Remove only after confirming no external use |
| Hard-coded policy | `DeliveryService`, `RetryScheduler` | Cannot tune runtime without code changes | Bind validated configuration properties |
| Direct initial audit construction differs from service path | `WebhookService` | Duplicated audit creation conventions | Route through one audit API |
| README stack/features diverge from build/source | `README.md` | Onboarding and operational commands can be wrong | Update docs from source of truth |
| Secret/config in checked-in properties | `application.properties` | Poor environment portability and credential hygiene | Externalize and rotate credentials |
| No explicit schema migrations | resources/build setup | Reproducible production schema evolution is unclear | Add versioned migrations and choose one schema authority |

## 27. Future enhancements (proposals, not implemented)

| Enhancement | Current problem and proposal | Impact / complexity | Worthwhile? |
|---|---|---|---|
| Real downstream HTTP client with configured timeout and typed errors | Simulator does not deliver anywhere; add a client adapter and contract/error mapping | Changes delivery boundary/config/tests; medium | Yes before claiming real recovery behavior |
| Idempotent ingress and atomic uniqueness handling | Check-then-insert race and no replay response semantics; make unique conflict deterministic and define idempotency contract | API/database behavior; medium | Yes for payment-like webhooks |
| Transactional outbox / broker | Persistence and delivery are separate synchronous operations; outbox makes intent durable and separates latency | New table/worker/broker; high | Worthwhile if reliable asynchronous delivery or scale is required; premature for demo |
| Locking/versioned claims | Scheduler/manual retries can process same due event concurrently; use optimistic version or row claim/lease | Persistence and retry flow; medium | Yes for multiple instances |
| Configurable retry policy and jitter | Fixed 1/2/4/8 min and no jitter create synchronized retry waves | Configuration and deterministic policy tests; low/medium | Yes if retry fleet grows |
| Flyway migrations | Schema currently auto-updated and no migration files | Versioned SQL and deployment ordering; medium | Yes for shared/production DB |
| Audit FK/indexes and typed action storage | Plain strings and unindexed retrieval have integrity/performance risks | Schema/API migration; medium | Yes as data volume grows |
| Automated tests/Testcontainers | Only context smoke test; retry and controller rules unverified | Build/test setup; medium | Yes immediately for business correctness |
| Authentication/authorization | Admin retry and read APIs are open | Security dependency/config/identity integration; medium/high | Yes before exposure outside trusted local environment |
| Observability (Micrometer/Actuator exposure, traces, metrics) | Current metrics are counts via application route; no delivery-latency/error telemetry | Instrumentation/exporter/dashboard; medium | Useful operationally after real integration |
| Frontend dashboard | No UI exists despite README “dashboard” phrasing | Separate web app/API integration; medium | Optional; only if operator workflow needs it |
| Kafka/Redis/Kubernetes | No current need is demonstrated by this small single-process source | Large architecture/ops cost | Not worthwhile merely as stack additions; choose only for explicit throughput/availability requirements |

## 28. Important files

| File | Purpose | Importance |
|---|---|---|
| `pom.xml` | Java level, Boot version, dependencies/plugins | Build and stack source of truth |
| `src/main/resources/application.properties` | DB/JPA settings; includes a credential (redacted in this document) | Runtime connection/schema behavior |
| `src/main/resources/application.yml` | Logging levels/file/pattern | Operational logging |
| `PaymentEventRecoveryEngineApplication.java` | Boot entry and scheduling enablement | Application startup |
| `Service/WebhookService.java` | Ingress business flow and duplicate check | Payment creation path |
| `Service/DeliveryService.java` | Delivery simulation, states, retry count/backoff/dead transition | Core business rules |
| `Service/RetryScheduler.java` | Due-event polling cadence | Automatic retry behavior |
| `Service/PaymentService.java` | Query, manual retry, mapping, metrics | Operator-facing API behavior |
| `entity/PaymentEvent.java`, `entity/AuditLog.java` | Persistence mappings | Database model |
| `repository/PaymentRepository.java`, `AuditLogRepository.java` | Derived queries | Persistence access paths |
| `controller/PaymentController.java`, `WebhookController.java` | REST contract | Public API surface |
| `dto/PaymentRequest.java` | Ingress validation | Input contract |
| `exception/GlobalExceptionHandler.java` | Explicit HTTP error mapping | Error contract |
| `README.md` | Project narrative, partly stale | Read with source cross-check |
| `src/test/.../PaymentEventRecoveryEngineApplicationTests.java` | Sole visible test | Current verification coverage |

## 29. Change impact map

```text
Add/change payment field
  → PaymentEvent mapping
  → schema migration (none currently; add if adopting Flyway)
  → PaymentRequest/PaymentResponse as appropriate
  → WebhookService mapping / PaymentService mapping
  → controller contract and docs
  → tests

Change retry policy
  → DeliveryService count/backoff/terminal logic
  → RetryScheduler eligibility/cadence
  → PaymentRepository due query/index/migration
  → configuration properties (if extracted)
  → audit actions/messages and manual retry semantics
  → deterministic service + scheduler tests

Change external delivery
  → DeliveryService simulator boundary/client adapter
  → endpoint/config/timeout/auth/error classification
  → transaction/outbox decision
  → failure reason and retry semantics
  → tests and operational telemetry

Change audit contract
  → AuditLog mapping/action type and schema
  → AuditService + direct WebhookService audit creation
  → AuditLogRepository ordering/query/index
  → AuditLogResponse and logs endpoint
  → tests

Change API behavior
  → Controller mapping/parameters/status
  → DTO validation/shape
  → service logic and exceptions
  → GlobalExceptionHandler
  → README/API docs and tests
```

## 30. Known inconsistencies

- README says Java 17 / Spring Boot 3; POM configures Java 21 and Boot 4.0.6.
- README depicts a dedicated scheduler package; scheduler class actually lives in uppercase `Service` package.
- README describes a DLQ; implementation only sets status DEAD and writes an audit row, with no queue/table.
- README claims “complete audit trail for every payment event” and retry-start/success/failure events; several declared `AuditAction` values are never written, and there is no manual-retry completion-specific record.
- README says Payment metrics dashboard; source has only a metrics JSON API and no dashboard UI.
- README lists structured logging; configured output is a plain pattern string, not JSON structured logging.
- README says Swagger/OpenAPI, Docker and React are future enhancements; this agrees with their absence, but other README feature/architecture text implies broader implementation than exists.
- `PaymentStatus.PROCESSING` is declared but unused; `MetricsResponse` has no processing count.
- `WebhookRepository` duplicates base access to PaymentEvent and is unused; webhook service uses `PaymentRepository`.
- Repository has two similar due retry methods; scheduler calls LessThanEqual variant, Before variant unused.
- Entity unique constraint catches duplicates beyond initial check, but handler only maps `DuplicatePaymentException`; concurrent duplicate DB violation is not mapped to 409.
- Manual retry allows FAILED without clearing `nextRetryAt` first; direct delivery still happens, and failure recalculates schedule.

## 31. How to run

Repository-supported assumptions: Java 21 and PostgreSQL reachable at configured local URL/database/user. The checked-in properties file supplies username/password; do not publish the password. No frontend or Docker commands are available.

From repository root on Windows:

```powershell
.\mvnw.cmd spring-boot:run
.\mvnw.cmd test
.\mvnw.cmd clean package
```

On a Unix-like shell:

```sh
./mvnw spring-boot:run
./mvnw test
./mvnw clean package
```

The wrapper scripts are present; the wrapper JAR is ignored and may need downloading by Maven Wrapper depending on local cache/network. No database creation command or environment variable setup is prescribed by repository; provision database `PaymentRecovery` and configure credentials in Spring properties/environment externally. Local default HTTP port is Spring Boot's 8080. README's plain `mvn` commands require Maven installed and its stated Java 17 is inconsistent with current POM.

## 32. AI QUICK CONTEXT

Payment Event Recovery Engine is a Java 21 / Spring Boot 4.0.6 Maven backend. It exposes webhook ingress at `POST /api/webhooks/payments` and payment query/admin-like operations under `/api/payments`. PostgreSQL persistence uses Spring Data JPA entities `PaymentEvent` and `AuditLog`; `PaymentRepository` and `AuditLogRepository` define derived queries. The service package is literally `com.example.paymenteventrecoveryengine.Service` with uppercase S.

Ingress validates paymentId/orderId/currency as nonblank and amount >= 0.01, checks for duplicate paymentId, saves a PENDING event and PAYMENT_RECEIVED audit, then synchronously calls `DeliveryService`. Delivery is not an HTTP call: `Random.nextBoolean()` simulates success/failure. Success marks DELIVERED, clears failure and retry time, resets retry count. Failure sets reason `Order Service Timeout` (despite no real timeout), increments retryCount, and either schedules FAILED at now + 1/2/4/8 minutes for counts 1–4 or marks DEAD at count 5. DEAD is a status plus audit, not a separate DLQ. `RetryScheduler` runs every minute and selects FAILED events with `nextRetryAt <= now`. Manual retry accepts FAILED or DEAD; DEAD becomes FAILED before synchronous delivery. Manual retry of FAILED retains its count.

The two entity tables have no explicit association: `AuditLog.paymentId` is a plain string. Payment ID is unique and required; payment order, amount, and currency are also required. Status is stored as enum string. `createdAt`/`updatedAt` are Hibernate-managed on payment; audit has creation timestamp. There are no migrations despite Flyway dependencies; JPA `ddl-auto=update` is configured. Database URL is local PostgreSQL; a password exists in the properties file and must be treated as secret.

Important classes: `WebhookService` creates payment and initial audit; `DeliveryService` owns state/retry rules; `RetryScheduler` scans due events; `PaymentService` handles query, audit retrieval, metrics and manual retry; the two controllers define seven routes; `GlobalExceptionHandler` maps only missing payment (404) and duplicate (409). DTO mapping is manual. Metrics are counts only. Logging goes to console and `logs/payment-recovery.log`.

Do not assume README claims are implemented. There is no frontend, real downstream service, auth, broker, physical DLQ, OpenAPI config, Docker deployment, or meaningful test suite. Only an empty-body Spring context-load test exists. No explicit concurrency claim/locking, outbox, retry configuration, or migration scripts exist. Avoid changing retry thresholds/transitions or endpoint contracts without updating the coupled service, scheduler, repository, entity/schema, DTO, audit, docs, and tests. Treat runtime details not specified above as UNKNOWN — not determinable from the current repository.
