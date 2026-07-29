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
    │                 └─ MockEmailServiceImpl  → logs/email.log
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

## Key Assumptions Made

- **Amount precision**: `principalAmount`, `interestRate`, `roi`, and investment
  `amount` use `BigDecimal`. The schema stores amounts as `BIGINT` (integer minor
  units, e.g. cents); the RowMapper converts transparently. A production MySQL
  migration would use `DECIMAL` or keep `BIGINT` with `Long` in Java.

- **Role isolation**: `Approval.validatorEmployeeId` (field validator who visits the
  borrower) and `Disbursement.fieldOfficerEmployeeId` (field officer who hands over
  money) are intentionally separate — the problem describes them as distinct actors.

- **Agreement letter format**: The problem specifies *"link to agreement letter
  (pdf)"*. For demo purposes, the agreement letter is rendered as an HTML page via
  `GET /api/loans/agreement/{id}` using a Thymeleaf template. In production, this
  would be replaced with a PDF pipeline — `AgreementService` makes this a
  single-implementation swap.

- **Email delivery**: `MockEmailServiceImpl` writes to `logs/email.log` instead of
  connecting to an SMTP server. The `EmailService` interface allows a production
  `SmtpEmailServiceImpl` to be substituted without touching listener or outbox logic.

- **Currency**: Investments must match the loan currency. Multi-currency loans are not
  supported.

- **No foreign keys**: The schema omits FK constraints to keep the setup portable
  across H2, MySQL, and PostgreSQL with no migration friction.

- **Investor pre-registration**: Investors must be created via `POST /api/investors`
  before they can invest; the system looks up their email from the `investors` table
  at notification time.

- **Loan ID placement**: POST/PATCH endpoints accept `loanId` in the request body
  rather than the URL path. GET endpoints use standard REST path variables
  (`/api/loans/{id}`, `/api/loans/agreement/{id}`, `/api/loans/notifications/{id}`).

---

## State Machine

| State | Allowed Operation | → Next State | Validation Rules |
|-------|------------------|--------------|-------------------|
| `ProposedState` | `approve()` | `APPROVED` | loanId, employeeId, photo URLs, datetime |
| `ApprovedState` | `invest()` | `INVESTED` (total = principal) | loanId, amount > 0, currency match, total ≤ principal |
| `InvestedState` | `disburse()` | `DISBURSED` | loanId, signedAgreementUrl (pdf/jpeg), officerId, datetime |
| `DisbursedState` | (none) | terminal | — |

- `AbstractLoanState` defaults all operations to `IllegalStateException`,
  preventing skip-transitions (PROPOSED → INVESTED) and reverse transitions.
- `DisbursedState` overrides nothing — a true terminal state.
- **Concurrency**: `LoanRepository.findByIdForUpdate()` uses `SELECT ... FOR UPDATE`
  to serialize investments on the same loan row.

---

## REST API

### Endpoints

| Method | Path | Request Body | Response | Description |
|--------|------|-------------|----------|-------------|
| `POST` | `/api/loans` | `CreateLoanRequest` | `201` + `LoanResponse` | Create loan (→ PROPOSED) |
| `GET` | `/api/loans/{id}` | — | `200` + `LoanResponse` | Get loan details |
| `PATCH` | `/api/loans/approve` | `ApproveLoanRequest` | `200` + `LoanResponse` | Approve (→ APPROVED) |
| `POST` | `/api/loans/investments` | `InvestRequest` | `200` + `LoanResponse` | Invest (→ INVESTED when fully funded) |
| `PATCH` | `/api/loans/investments/receive` | `ReceiveFundsRequest` | `204` | Confirm funds received |
| `PATCH` | `/api/loans/disburse` | `DisburseRequest` | `200` + `LoanResponse` | Disburse (→ DISBURSED) |
| `GET` | `/api/loans/agreement/{id}` | — | `200` + HTML | View agreement letter |
| `GET` | `/api/loans/notifications/{id}` | — | `200` + `[NotificationResponse]` | Query email send status |
| `POST` | `/api/investors` | `CreateInvestorRequest` | `201` | Register investor profile |

### Request Bodies

**Create loan** — `POST /api/loans`
```json
{
  "borrowerId": 1001,
  "borrowerName": "Zhang Wei",
  "principalAmount": 500000,
  "interestRate": 10.00,
  "roi": 8.00,
  "currency": "USD"
}
```

**Approve** — `PATCH /api/loans/approve`
```json
{
  "loanId": 1,
  "validatorEmployeeId": 2001,
  "validatorEmployeeName": "Chen Jie",
  "approvalDatetime": "2026-07-27T12:00:00+08:00",
  "validatorPhotoUrls": ["https://storage.example.com/photo.jpg"]
}
```

**Invest** — `POST /api/loans/investments`
```json
{
  "loanId": 1,
  "investorId": 3001,
  "investorName": "Alice",
  "amount": 300000,
  "currency": "USD",
  "datetime": "2026-07-27T12:00:00+08:00",
  "fundStatus": "RECEIVED"
}
```

**Confirm funds** — `PATCH /api/loans/investments/receive`
```json
{
  "loanId": 1,
  "investmentId": 5
}
```

**Disburse** — `PATCH /api/loans/disburse`
```json
{
  "loanId": 1,
  "signedAgreementUrl": "https://storage.example.com/agreement.pdf",
  "fieldOfficerEmployeeId": 4001,
  "fieldOfficerEmployeeName": "Liu Yang",
  "disbursementDatetime": "2026-08-01T12:00:00+08:00"
}
```

**Create investor** — `POST /api/investors`
```json
{
  "investorId": 3001,
  "name": "Alice Wang",
  "emailUrl": "alice@example.com",
  "registerDate": "2025-01-15"
}
```

### Response Examples

**LoanResponse** — `GET /api/loans/{id}` / `POST /api/loans` / approve / invest / disburse
```json
{
  "id": 1,
  "borrowerId": 1001,
  "borrowerName": "Zhang Wei",
  "principalAmount": 500000,
  "interestRate": 10.00,
  "roi": 8.00,
  "currency": "USD",
  "currState": "INVESTED",
  "initDatetime": "2026-07-27T12:00:00+08:00",
  "agreeLetterUrl": "http://localhost:8080/api/loans/agreement/1",
  "agreeLetterSendDatetime": "2026-07-28T12:00:00+08:00",
  "fundsReceivedDatetime": "2026-07-28T13:00:00+08:00",
  "approval": {
    "validatorEmployeeId": 2001,
    "validatorEmployeeName": "Chen Jie",
    "approvalDatetime": "2026-07-27T12:00:00+08:00",
    "validatorPhotoUrls": ["https://storage.example.com/photo.jpg"]
  },
  "investments": [
    {
      "id": 1,
      "investorId": 3001,
      "investorName": "Alice",
      "amount": 300000,
      "currency": "USD",
      "datetime": "2026-07-27T13:00:00+08:00",
      "fundStatus": "RECEIVED"
    },
    {
      "id": 2,
      "investorId": 3002,
      "investorName": "Bob",
      "amount": 200000,
      "currency": "USD",
      "datetime": "2026-07-27T13:05:00+08:00",
      "fundStatus": "RECEIVED"
    }
  ],
  "disbursement": null
}
```

**NotificationResponse[]** — `GET /api/loans/notifications/{id}`
```json
[
  {
    "id": 1,
    "investorId": 3001,
    "recipientEmail": "alice@example.com",
    "status": "SENT",
    "agreementUrl": "http://localhost:8080/api/loans/agreement/1",
    "sentDatetime": "2026-07-28T12:00:01+08:00",
    "errorMessage": null,
    "retryCount": 0
  },
  {
    "id": 2,
    "investorId": 3002,
    "recipientEmail": "bob@example.com",
    "status": "SENT",
    "agreementUrl": "http://localhost:8080/api/loans/agreement/1",
    "sentDatetime": "2026-07-28T12:00:02+08:00",
    "errorMessage": null,
    "retryCount": 0
  }
]
```

### Error Responses

```json
{ "code": "STATE_CONFLICT", "message": "Cannot invest a loan in DISBURSED state (id=1)", "timestamp": "2026-07-28T12:00:00+08:00" }
```

| HTTP | Code | Meaning |
|------|------|---------|
| 400 | `BAD_REQUEST` | Missing/invalid params, loan not found |
| 400 | `VALIDATION` | Jakarta `@Valid` constraint violation |
| 409 | `STATE_CONFLICT` | Illegal state transition |
| 500 | `INTERNAL` | Unexpected error |

---

## Database

- **H2** file-based, MySQL compatibility mode
- **TCP server** on port 9092 (shared by app + browser console)
- **8 tables**: `loans`, `approvals`, `approval_photos`, `investments`, `disbursements`, `investors`, `notification_outbox`
- No foreign key constraints (by design — portable across DB engines)
- Indexes on all FK columns + `loans.curr_state` + `loans.borrower_id` + `notification_outbox.status`
- Schema: `src/main/resources/schema.sql`
- Test data (auto-executed): `src/main/resources/test_data.sql`

### H2 Console

`http://localhost:8080/h2-console` | JDBC URL: `jdbc:h2:tcp://localhost:9092/./loanengine` | user: `sa`

---

## Email Notification

- **Interface**: `EmailService` — decouples notification from delivery mechanism
- **Mock**: `MockEmailServiceImpl` — writes to `logs/email.log` (configurable via `app.notification.email-log-path`)
- **Outbox**: `notification_outbox` table tracks every email: `PENDING → SENT / FAILED`
- **Async**: `@TransactionalEventListener(AFTER_COMMIT)` + `@Async` on `InvestorNotificationListener`, thread pool `notif-*` (2–5 threads, `LinkedBlockingQueue(50)`, discard-on-overflow — rejected tasks are logged and dropped to avoid blocking the main loan flow)
- **Retry**: `NotificationRetryScheduler` runs every 5 minutes, retries FAILED records (up to 3 attempts)
- **Decoupled**: email failure does NOT affect loan state — `LoanService` only publishes `LoanFullyInvestedEvent`

---

## Testing

- **66 tests** — 57 pure JUnit 5 + 9 integration/unit (MockEmailServiceImpl, NotificationIntegration, LoanRepository)
- **11 test classes**:

| Class | Cases | What it covers |
|-------|-------|----------------|
| `LoanStateHandlerTest` | 29 | State transitions, validation (currency match, file type), edge cases |
| `NotificationOutboxTest` | 4 | Entity state machine: createPending, markSent, markFailed, equality |
| `AgreementServiceTest` | 2 | URL generation, Thymeleaf template delegation |
| `LoanServiceTest` | 12 | investLoan orchestration: partial, fullyFunded → URL+event, notFound; **approveLoan:** forUpdate lock, notFound; **disburseLoan:** forUpdate lock, notFound; **receiveFunds:** allReceived, alreadySet, notFound, partial |
| `InvestorNotificationListenerTest` | 4 | Event handling: each-investor email, skip-no-email, fail→markFailed, outbox fields |
| `MockEmailServiceImplTest` | 3 | email.log write format, append, parent-dir creation |
| `NotificationIntegrationTest` | 2 | End-to-end: investor → outbox → email.log → DB status; FAILED retry eligibility |
| `LoanRepositoryTest` | 4 | **Integration:** saveDisbursement ID, updateInvestmentFundStatus rows/wrongLoan, investorSave ID |
| `GlobalExceptionHandlerTest` | 4 | DataIntegrityViolationException→409, IllegalArgumentException→400, IllegalStateException→409, Exception→500 |
| `LoanControllerTest` | 2 | invest fundStatus forced PENDING (user RECEIVED ignored + null default) |

- Run: `mvn test`

---

## Logging

- **Log4j2** (`log4j2-spring.xml`)
- `logs/app.log` — INFO+ for `org.example.amartha` (business audit trail)
- `logs/error.log` — WARN+ for all packages
- `logs/email.log` — mock email delivery log (ISO 8601 timestamps + recipient details)

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

---

## How to Run & Test

See **[SMOKE_TEST.md](SMOKE_TEST.md)** for:
- Prerequisites and startup commands (`mvn test`, H2 TCP, `mvn spring-boot:run`)
- 25 curl-based smoke tests covering the full lifecycle, state machine rules, and business logic validations
