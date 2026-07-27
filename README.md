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
    │       ├─ ApprovedState   → invest   → INVESTED
    │       ├─ InvestedState   → disburse → DISBURSED
    │       └─ DisbursedState  → terminal
    │
    └─ LoanRepository     ← JdbcTemplate + handwritten SQL
            │
            └─ H2 (TCP server, MySQL compat mode)
```

---

## Domain Model

### Loan

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `id` | `Long` | — | Auto-increment PK |
| `gmtCreate` / `gmtModify` | `LocalDateTime` | — | Record timestamps |
| `borrowerId` | `Long` | Y | Borrower identifier |
| `borrowerName` | `String` | N | Borrower display name |
| `principalAmount` | `BigDecimal` | Y | Smallest currency unit (e.g. USD cents) |
| `interestRate` | `BigDecimal` | Y | Annual rate (0–100%) |
| `roi` | `BigDecimal` | Y | Return on investment (0–100%) |
| `currency` | `String` | Y | ISO 4217 (3-letter) |
| `currState` | `LoanStateEnum` | — | Current state, managed by state machine |
| `initDatetime` | `OffsetDateTime` | — | Loan creation time |
| `agreeLetterSendDatetime` | `OffsetDateTime` | — | When agreement letter was sent |
| `fundsReceivedDatetime` | `OffsetDateTime` | — | When all investments are confirmed RECEIVED |
| `agreeLetterUrl` | `String` | — | Generated agreement letter link |
| `approval` | `Approval` | — | Approval record |
| `investments` | `List<Investment>` | — | Investment records |
| `disbursement` | `Disbursement` | — | Disbursement record |

### LoanStateEnum

`PROPOSED → APPROVED → INVESTED → DISBURSED` (uni-directional).

### Approval

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `loanId` | `Long` | — | FK back to loan |
| `validatorEmployeeId` | `Long` | Y | Field validator employee ID |
| `validatorEmployeeName` | `String` | N | Employee display name |
| `approvalDatetime` | `OffsetDateTime` | Y | Approval timestamp |
| `validatorPhotoUrls` | `List<String>` | Y | ≥ 1 photo proof URLs |

### Investment

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `loanId` | `Long` | — | FK back to loan |
| `investorId` | `Long` | Y | Investor identifier |
| `investorName` | `String` | N | Investor display name |
| `amount` | `BigDecimal` | Y | Smallest currency unit, must be > 0 |
| `currency` | `String` | Y | ISO 4217 |
| `datetime` | `OffsetDateTime` | Y | Investment timestamp |
| `fundStatus` | `FundStatus` | — | `PENDING` or `RECEIVED` |

### Disbursement

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `loanId` | `Long` | — | FK back to loan |
| `signedAgreementUrl` | `String` | Y | Signed PDF/JPEG URL |
| `fieldOfficerEmployeeId` | `Long` | Y | Disbursing officer ID |
| `fieldOfficerEmployeeName` | `String` | N | Officer display name |
| `disbursementDatetime` | `OffsetDateTime` | Y | Disbursement timestamp (future = scheduled) |
| `disbursed` | `boolean` | — | Always `TRUE` once inserted |

### Investor (standalone entity, not yet wired)

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `investorId` | `String` | Y | Unique identifier |
| `name` | `String` | Y | Full name |
| `emailUrl` | `String` | N | Email for notifications |
| `registerDate` | `LocalDate` | N | Registration date |

---

## State Machine (State Pattern)

### Design

- **`LoanStateHandler`** — interface: `approve()`, `invest()`, `disburse()`
- **`AbstractLoanState`** — base class: all methods default to `IllegalStateException`
- Concrete states override only the method they permit:

| State | Allowed Operation | → Next State |
|-------|------------------|--------------|
| `ProposedState` | `approve()` | `APPROVED` |
| `ApprovedState` | `invest()` | `INVESTED` (when total = principal) |
| `InvestedState` | `disburse()` | `DISBURSED` |
| `DisbursedState` | (none) | terminal |

### Extensibility

- **Add a new operation**: add to `LoanStateHandler` → default throw in `AbstractLoanState` → override in states that support it.
- **Add a new state**: extend `AbstractLoanState`, override `stateName()` + the operation(s) it supports, register in `forState()`.

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

### Request Examples

**Create loan:**
```
POST /api/loans
```
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

**Approve:**
```
PATCH /api/loans/{id}/approve
```
```json
{
  "validatorEmployeeId": 2001,
  "validatorEmployeeName": "Chen Jie",
  "approvalDatetime": "2026-07-27T12:00:00+08:00",
  "validatorPhotoUrls": ["https://storage.example.com/photo.jpg"]
}
```

**Invest (with optional fundStatus):**
```
POST /api/loans/{id}/investments
```
```json
{
  "investorId": 3001,
  "investorName": "Alice",
  "amount": 300000,
  "currency": "USD",
  "datetime": "2026-07-27T12:00:00+08:00",
  "fundStatus": "RECEIVED"
}
```

**Confirm funds (for PENDING investments):**
```
PATCH /api/loans/{id}/investments/{investmentId}/receive
```

**Disburse (future datetime → Kafka-style log):**
```
PATCH /api/loans/{id}/disburse
```
```json
{
  "signedAgreementUrl": "https://storage.example.com/agreement.pdf",
  "fieldOfficerEmployeeId": 4001,
  "fieldOfficerEmployeeName": "Liu Yang",
  "disbursementDatetime": "2026-08-01T12:00:00+08:00"
}
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

## Validation Rules (Jakarta Validation)

| DTO | Field | Constraint |
|-----|-------|-----------|
| All | ID fields | `@NotNull` `@Positive` |
| `CreateLoanRequest` | `interestRate`, `roi` | `@DecimalMin(0)` `@DecimalMax(100)` |
| `CreateLoanRequest` | `currency` | `@NotBlank` |
| `ApproveLoanRequest` | `validatorPhotoUrls` | `@NotEmpty` `@Size(max=20)` |
| `InvestRequest` | `currency` | `@Size(min=3,max=3)` |
| `DisburseRequest` | `signedAgreementUrl` | `@NotBlank` `@Size(max=2000)` |

---

## Database

- **H2** file-based, MySQL compatibility mode
- **TCP server** on port 9092 (shared by app + CLI/browser)
- **6 tables**: `loans`, `approvals`, `approval_photos`, `investments`, `disbursements`, `investors`
- No foreign key constraints (by design — production anti-pattern)
- Indexes on all FK columns + `loans.curr_state` + `loans.borrower_id`
- Schema: `src/main/resources/schema.sql`
- Test data (auto-executed): `src/main/resources/test_data.sql`

### H2 Console

`http://localhost:8080/h2-console` | JDBC URL: `jdbc:h2:tcp://localhost:9092/./loanengine` | user: `sa`

---

## Logging

- **Log4j2** (`log4j2-spring.xml`)
- `logs/app.log` — INFO+ for `org.example.amartha` (business audit trail)
- `logs/error.log` — WARN+ for all packages (alert aggregation)
- Controller logs full request body on entry
- State handlers log transitions at INFO, illegal attempts at WARN

---

## Testing

- **26 pure JUnit 5 tests** (`LoanStateHandlerTest`) — no Spring container
  - Success paths, validation failures, illegal transitions
  - Edge cases: zero amount, overflow, exact remaining, null fields
- Run: `mvn test`

---

## Tech Stack

| Component | Choice |
|-----------|--------|
| Language | Java 21 |
| Framework | Spring Boot 3.5.x |
| Build | Maven |
| Database | H2 (file-based, MySQL mode, TCP) |
| Data access | `JdbcTemplate` + handwritten SQL (no ORM) |
| Logging | Log4j2 |
| Boilerplate | Lombok (`@Getter`, `@Setter`, `@Slf4j`, …) |
| Validation | Jakarta Validation |
| Testing | JUnit 5 + Mockito |

---

## TODO

- [ ] **Agreement letter generation** — when loan transitions to INVESTED, generate a PDF agreement letter URL and notify all investors (currently stubbed; `LoanService.investLoan` has the TODO).
- [ ] `Investor` entity wired to investments (currently standalone model only).
- [ ] Service layer integration tests with real H2.
