# Loan Engine — Amartha Code Challenge (Problem 3)

REST API for a loan lifecycle engine with strict uni-directional state machine:
`PROPOSED → APPROVED → INVESTED → DISBURSED`.

---

## Architecture

```
Controller (@Valid DTOs)
    │
LoanService (lifecycle orchestrator)
    │              │
    ├─ LoanStateHandler    ← State Pattern dispatch
    │   └─ AbstractLoanState (default: throw on illegal transitions)
    │       ├─ ProposedState   → approve  → APPROVED
    │       ├─ ApprovedState   → invest   → INVESTED (fully funded)
    │       ├─ InvestedState   → disburse → DISBURSED
    │       └─ DisbursedState  → terminal
    │
    ├─ AgreementService   ← generates URL + renders HTML
    │
    ├─ [when INVESTED] → publishEvent(LoanFullyInvestedEvent)
    │       │
    │       └─ InvestorNotificationListener (@Async)
    │            ├─ InvestorRepository  → lookup emails
    │            ├─ NotificationOutboxRepository  → insert PENDING
    │            └─ EmailService (interface)
    │                 └─ MockEmailServiceImpl  → email.log
    │
    └─ LoanRepository     ← JdbcTemplate + handwritten SQL
            │
            └─ H2 (TCP server, MySQL compat mode)
```

## Notification Flow (Outbox Pattern)

```
investLoan() [sync, transactional]
  ├─ state → INVESTED
  ├─ AgreementService.generateAgreementUrl()
  ├─ persist agree_letter_url + agree_letter_send_datetime
  └─ publishEvent(LoanFullyInvestedEvent)
         │
         ▼  [@Async, separate thread pool]
InvestorNotificationListener
  ├─ InvestorRepository.findByInvestorIds()  → email map
  ├─ for each investor:
  │    ├─ NotificationOutboxRepository.insert(PENDING)
  │    ├─ EmailService.sendAgreementEmail()
  │    ├─ markSent() / markFailed()
  │    └─ update status in DB
  │
  └─ NotificationRetryScheduler (@Scheduled 5 min)
       └─ retry FAILED records (up to 3 attempts)
```

---

## Domain Model

### Loan

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `id` | `Long` | — | Auto-increment PK |
| `borrowerId` | `Long` | Y | Borrower identifier |
| `borrowerName` | `String` | N | Borrower display name |
| `principalAmount` | `BigDecimal` | Y | Smallest currency unit (e.g. USD cents) |
| `interestRate` | `BigDecimal` | Y | Annual rate (0–100%) |
| `roi` | `BigDecimal` | Y | Return on investment (0–100%) |
| `currency` | `String` | Y | ISO 4217 (3-letter) |
| `currState` | `LoanStateEnum` | — | Current state, managed by state machine |
| `initDatetime` | `OffsetDateTime` | — | Loan creation time |
| `agreeLetterUrl` | `String` | — | Generated agreement letter link |
| `agreeLetterSendDatetime` | `OffsetDateTime` | — | When agreement letter URL was generated |
| `fundsReceivedDatetime` | `OffsetDateTime` | — | When all investments are confirmed RECEIVED |
| `approval` | `Approval` | — | Approval record |
| `investments` | `List<Investment>` | — | Investment records |
| `disbursement` | `Disbursement` | — | Disbursement record |

### NotificationOutbox

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `id` | `Long` | — | Auto-increment PK |
| `loanId` | `Long` | Y | FK to loan |
| `investorId` | `Long` | Y | FK to investor |
| `recipientEmail` | `String` | Y | Destination email address |
| `type` | `NotificationType` | — | Currently `AGREEMENT_LETTER` |
| `status` | `NotificationStatus` | — | `PENDING` → `SENT` / `FAILED` |
| `agreementUrl` | `String` | — | The link sent in the email |
| `sentDatetime` | `OffsetDateTime` | — | When email was actually sent |
| `errorMessage` | `String` | — | Failure reason (if FAILED) |
| `retryCount` | `int` | — | Number of retry attempts |

### Approval / Investment / Disbursement / Investor

See source: `org.example.amartha.loan.model.*`.

---

## State Machine (State Pattern)

| State | Allowed Operation | → Next State | Validation Rules |
|-------|------------------|--------------|-------------------|
| `ProposedState` | `approve()` | `APPROVED` | employeeId, photo URLs, datetime |
| `ApprovedState` | `invest()` | `INVESTED` (total = principal) | amount > 0, currency match, total ≤ principal |
| `InvestedState` | `disburse()` | `DISBURSED` | signedAgreementUrl (pdf/jpeg), officerId, datetime |
| `DisbursedState` | (none) | terminal | — |

**Concurrency**: `LoanRepository.findByIdForUpdate()` uses `SELECT ... FOR UPDATE` to serialize investments on the same loan.

---

## REST API

| Method | Path | Description |
|--------|------|-------------|
| `POST` | `/api/loans` | Create loan → 201 (PROPOSED) |
| `GET` | `/api/loans/{id}` | Get loan details → 200 |
| `PATCH` | `/api/loans/{id}/approve` | Approve → 200 (→ APPROVED) |
| `POST` | `/api/loans/{id}/investments` | Invest → 200 (→ INVESTED when fully funded) |
| `PATCH` | `/api/loans/{id}/investments/{iid}/receive` | Confirm funds → 204 |
| `PATCH` | `/api/loans/{id}/disburse` | Disburse → 200 (→ DISBURSED) |
| `GET` | `/api/loans/{id}/agreement` | View agreement letter (HTML) → 200 |
| `GET` | `/api/loans/{id}/notifications` | Query email send status → 200 |
| `POST` | `/api/investors` | Create investor profile → 201 |

### Request Examples

**Create loan:**
```
POST /api/loans
{ "borrowerId": 1001, "borrowerName": "Zhang Wei", "principalAmount": 500000,
  "interestRate": 10.00, "roi": 8.00, "currency": "USD" }
```

**Approve:**
```
PATCH /api/loans/{id}/approve
{ "validatorEmployeeId": 2001, "validatorEmployeeName": "Chen Jie",
  "approvalDatetime": "2026-07-27T12:00:00+08:00",
  "validatorPhotoUrls": ["https://storage.example.com/photo.jpg"] }
```

**Invest:**
```
POST /api/loans/{id}/investments
{ "investorId": 3001, "investorName": "Alice", "amount": 300000,
  "currency": "USD", "datetime": "2026-07-27T12:00:00+08:00",
  "fundStatus": "RECEIVED" }
```

**Disburse:**
```
PATCH /api/loans/{id}/disburse
{ "signedAgreementUrl": "https://storage.example.com/agreement.pdf",
  "fieldOfficerEmployeeId": 4001, "fieldOfficerEmployeeName": "Liu Yang",
  "disbursementDatetime": "2026-08-01T12:00:00+08:00" }
```

**Create investor:**
```
POST /api/investors
{ "investorId": 3001, "name": "Alice Wang",
  "emailUrl": "alice@example.com", "registerDate": "2025-01-15" }
```

**View agreement letter (browser):**
```
GET /api/loans/{id}/agreement
```

**Query notification status:**
```
GET /api/loans/{id}/notifications
→ [{ "id": 1, "investorId": 3001, "recipientEmail": "alice@example.com",
     "status": "SENT", "agreementUrl": "...", "sentDatetime": "...", "retryCount": 0 }]
```

### Error Response Format

```json
{ "code": "STATE_CONFLICT", "message": "...", "timestamp": "..." }
```

| HTTP | Code | Meaning |
|------|------|---------|
| 400 | `BAD_REQUEST` | Missing/invalid params, loan not found |
| 400 | `VALIDATION` | `@Valid` constraint violation |
| 409 | `STATE_CONFLICT` | Illegal state transition |
| 500 | `INTERNAL` | Unexpected error |

---

## Database

- **H2** file-based, MySQL compatibility mode
- **TCP server** on port 9092
- **8 tables**: `loans`, `approvals`, `approval_photos`, `investments`, `disbursements`, `investors`, `notification_outbox`
- No foreign key constraints (by design)
- Indexes on all FK columns + `loans.curr_state` + `loans.borrower_id` + `notification_outbox.status`
- Schema: `src/main/resources/schema.sql`
- Test data: `src/main/resources/test_data.sql`

### H2 Console

`http://localhost:8080/h2-console` | JDBC URL: `jdbc:h2:tcp://localhost:9092/./loanengine` | user: `sa`

---

## Email Notification

- **Interface**: `EmailService` — decouples notification from delivery mechanism
- **Mock**: `MockEmailServiceImpl` — writes to `email.log` (configurable via `app.notification.email-log-path`)
- **Outbox**: `notification_outbox` table tracks every email: `PENDING → SENT / FAILED`
- **Async**: `@EventListener` + `@Async` on `InvestorNotificationListener`, thread pool `notif-*` (2–5 threads, `LinkedBlockingQueue(50)`)
- **Retry**: `NotificationRetryScheduler` runs every 5 minutes, retries FAILED records (up to 3 attempts)
- **Decoupled**: email failure does NOT affect loan state — `LoanService` only publishes `LoanFullyInvestedEvent`

---

## Testing

- **43 pure JUnit 5 tests** — no Spring container, millisecond-level
- **6 test classes**:

| Class | Cases | What it covers |
|-------|-------|----------------|
| `LoanStateHandlerTest` | 29 | State transitions, validation rules (currency match, file type), edge cases |
| `NotificationOutboxTest` | 4 | Entity state machine: createPending, markSent, markFailed, equality |
| `AgreementServiceTest` | 2 | URL generation, Thymeleaf template delegation |
| `LoanServiceTest` | 4 | investLoan orchestration: partial, fullyFunded → URL+event, notFound |
| `InvestorNotificationListenerTest` | 4 | Event handling: each-investor email, skip-no-email, fail→markFailed, outbox fields |

- Run: `mvn test`

---

## Logging

- **Log4j2** (`log4j2-spring.xml`)
- `logs/app.log` — INFO+ for `org.example.amartha` (business audit trail)
- `logs/error.log` — WARN+ for all packages
- `email.log` — mock email delivery log (ISO 8601 timestamps + recipient details)

---

## Tech Stack

| Component | Choice |
|-----------|--------|
| Language | Java 21 |
| Framework | Spring Boot 3.5.x |
| Build | Maven |
| Database | H2 (file-based, MySQL mode, TCP) |
| Data access | `JdbcTemplate` + handwritten SQL |
| Templates | Thymeleaf (agreement letter HTML) |
| Async | `@Async` + `ThreadPoolTaskExecutor` + `@EnableScheduling` |
| Logging | Log4j2 |
| Boilerplate | Lombok |
| Validation | Jakarta Validation |
| Testing | JUnit 5 + Mockito |
