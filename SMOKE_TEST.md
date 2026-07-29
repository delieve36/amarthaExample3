# Loan Engine — Smoke Test Manual

## Environment Setup

### 1. Check Java (version 21 required)

```bash
java -version
# Expected: openjdk version "21" ...
```

If not installed:
- **macOS**: `brew install openjdk@21`
- **Ubuntu**: `sudo apt install openjdk-21-jdk`
- **Windows**: download from https://adoptium.net

### 2. Check Maven

```bash
mvn --version
# Expected: Apache Maven 3.9+ (any 3.x is fine)
```

If not installed:
- **macOS**: `brew install maven`
- **Ubuntu**: `sudo apt install maven`
- **Windows**: download from https://maven.apache.org

### 3. Download dependencies + resolve H2 jar path

```bash
cd loan-engine/    # the project root
mvn dependency:resolve -q

# Find the H2 jar path (needed for TCP server startup):
find ~/.m2/repository/com/h2database/h2 -name "h2-*.jar" -not -name "*sources*" | tail -1
# Example output: /Users/you/.m2/repository/com/h2database/h2/2.3.232/h2-2.3.232.jar
# Copy this path → H2_JAR below
```

### 4. Check curl (optional, for pretty output)

```bash
curl --version
# Any version is fine. If missing: brew install curl (macOS) / apt install curl (Ubuntu)
```

> **If you don't have `jq`**, either install it (`brew install jq` / `apt install jq`) or remove `| jq` from the curl commands — the output will just be unformatted JSON.

---

## Run Unit Tests (66 tests, < 5 s)

```bash
mvn test
```

Expected: `Tests run: 66, Failures: 0, Errors: 0, Skipped: 0 — BUILD SUCCESS`

No database or external services needed.

---

## Start the Application

```bash
# --- Terminal 1: H2 database server ---
H2_JAR=$(find ~/.m2/repository/com/h2database/h2 -name "h2-*.jar" -not -name "*sources*" | tail -1)
java -cp "$H2_JAR" org.h2.tools.Server \
  -tcp -tcpAllowOthers -tcpPort 9092 -baseDir data -ifNotExists &

# Wait 2 seconds for H2 to start
sleep 2

# --- Terminal 2: Spring Boot app ---
mvn spring-boot:run
```

App starts on **http://localhost:8080**.  
Schema and test data are loaded automatically on first run.

> **Verify it's running**: open a third terminal and run `curl -s http://localhost:8080/api/loans/1 | jq '.currState'` → should print `"PROPOSED"`.

### Pre-loaded test data

| Type | ID | State | Details |
|------|----|-------|---------|
| Loan | 1 | PROPOSED | borrower Zhang Wei, $5,000, 10% rate, 8% ROI |
| Loan | 100 | APPROVED | borrower Li Ming, $10,000, 12.5% rate, 9% ROI |
| Investor | 3001 | — | Alice Wang, alice@example.com |
| Investor | 3002 | — | Bob Chen, bob@example.com |
| Investor | 3003 | — | Carol Liu, carol@example.com |

---

## Smoke Tests

Set the base URL once:

```bash
BASE=http://localhost:8080
```

---

## A. Happy Path — Full Lifecycle

### A1. Register a new investor

```bash
curl -s -X POST $BASE/api/investors \
  -H 'Content-Type: application/json' \
  -d '{"investorId": 3004, "name": "David Park", "emailUrl": "david@example.com", "registerDate": "2025-07-01"}'
```

*Expected: `201`. Response body: `"Investor created: 3004"`*

### A2. Create a new loan (→ PROPOSED)

```bash
curl -s -X POST $BASE/api/loans \
  -H 'Content-Type: application/json' \
  -d '{"borrowerId": 2001, "borrowerName": "Test Borrower", "principalAmount": 5000, "interestRate": 10.0, "roi": 8.0, "currency": "USD"}' | jq
```

*Expected: `201`, `currState: "PROPOSED"`, `principalAmount: 5000`. Copy the returned `id` below.*

```bash
LOAN_ID=2   # ← replace with the actual id from the response
```

### A3. Approve the loan (PROPOSED → APPROVED)

```bash
curl -s -X PATCH $BASE/api/loans/approve \
  -H 'Content-Type: application/json' \
  -d "{\"loanId\": $LOAN_ID, \"validatorEmployeeId\": 2001, \"validatorEmployeeName\": \"Inspector A\", \"approvalDatetime\": \"2026-07-27T12:00:00+08:00\", \"validatorPhotoUrls\": [\"https://storage.example.com/visit-proof.jpg\"]}" | jq
```

*Expected: `200`, `currState: "APPROVED"`, `approval.validatorEmployeeId: 2001`.*

### A4. Invest — partial ($3,000 of $5,000, stays APPROVED)

```bash
curl -s -X POST $BASE/api/loans/investments \
  -H 'Content-Type: application/json' \
  -d "{\"loanId\": $LOAN_ID, \"investorId\": 3001, \"investorName\": \"Alice Wang\", \"amount\": 3000, \"currency\": \"USD\", \"datetime\": \"2026-07-27T13:00:00+08:00\"}" | jq
```

*Expected: `200`, `currState: "APPROVED"`, `investments` has 1 entry.*

### A5. Invest — remaining $2,000 triggers INVESTED

```bash
curl -s -X POST $BASE/api/loans/investments \
  -H 'Content-Type: application/json' \
  -d "{\"loanId\": $LOAN_ID, \"investorId\": 3002, \"investorName\": \"Bob Chen\", \"amount\": 2000, \"currency\": \"USD\", \"datetime\": \"2026-07-27T13:05:00+08:00\"}" | jq
```

*Expected: `200`, `currState: "INVESTED"`, `agreeLetterUrl` populated, `agreeLetterSendDatetime` set.*

### A6. View agreement letter

```bash
curl -s $BASE/api/loans/agreement/$LOAN_ID
```

*Expected: HTML page with loan details, approval info, investor table.*

### A7. Check notification status (outbox)

```bash
curl -s $BASE/api/loans/notifications/$LOAN_ID | jq
```

*Expected: array with one entry per investor, each `status: "SENT"`.*

### A8. Verify email.log

```bash
cat logs/email.log
```

*Expected: lines like `[2026-...] TO=alice@example.com | INVESTOR=Alice Wang | LOAN=... | AGREEMENT=http://...`*

### A9. Confirm all funds received

```bash
# Get investment IDs
INV1=$(curl -s $BASE/api/loans/$LOAN_ID | jq '.investments[0].id')
INV2=$(curl -s $BASE/api/loans/$LOAN_ID | jq '.investments[1].id')

# Mark both as received
curl -s -X PATCH $BASE/api/loans/investments/receive \
  -H 'Content-Type: application/json' \
  -d "{\"loanId\": $LOAN_ID, \"investmentId\": $INV1}" -w "\nHTTP %{http_code}\n"
curl -s -X PATCH $BASE/api/loans/investments/receive \
  -H 'Content-Type: application/json' \
  -d "{\"loanId\": $LOAN_ID, \"investmentId\": $INV2}" -w "\nHTTP %{http_code}\n"
```

*Expected: `HTTP 204` for each.*

### A10. Disburse the loan (INVESTED → DISBURSED)

```bash
curl -s -X PATCH $BASE/api/loans/disburse \
  -H 'Content-Type: application/json' \
  -d "{\"loanId\": $LOAN_ID, \"signedAgreementUrl\": \"https://storage.example.com/signed-agreement.pdf\", \"fieldOfficerEmployeeId\": 4001, \"fieldOfficerEmployeeName\": \"Officer B\", \"disbursementDatetime\": \"2026-08-01T10:00:00+08:00\"}" | jq
```

*Expected: `200`, `currState: "DISBURSED"`.*

### A11. Confirm final state

```bash
curl -s $BASE/api/loans/$LOAN_ID | jq '{id, currState, agreeLetterUrl, disbursement: .disbursement.signedAgreementUrl}'
```

*Expected: `currState: "DISBURSED"`, agreement URL and disbursement info present.*

---

## B. State Machine — Illegal Transition Tests

### B1. DISBURSED → invest (should reject)

```bash
curl -s -X POST $BASE/api/loans/investments \
  -H 'Content-Type: application/json' \
  -d "{\"loanId\": $LOAN_ID, \"investorId\": 3003, \"amount\": 1000, \"currency\": \"USD\", \"datetime\": \"2026-08-02T00:00:00+08:00\"}"
```

*Expected: `409 STATE_CONFLICT` — `"Cannot invest a loan in DISBURSED state"`.*

### B2. DISBURSED → approve (should reject)

```bash
curl -s -X PATCH $BASE/api/loans/approve \
  -H 'Content-Type: application/json' \
  -d "{\"loanId\": $LOAN_ID, \"validatorEmployeeId\": 2001, \"approvalDatetime\": \"2026-08-02T00:00:00+08:00\", \"validatorPhotoUrls\": [\"http://x.jpg\"]}"
```

*Expected: `409 STATE_CONFLICT`.*

### B3. DISBURSED → disburse (double-disburse, should reject)

```bash
curl -s -X PATCH $BASE/api/loans/disburse \
  -H 'Content-Type: application/json' \
  -d "{\"loanId\": $LOAN_ID, \"signedAgreementUrl\": \"https://example.com/another.pdf\", \"fieldOfficerEmployeeId\": 4002, \"disbursementDatetime\": \"2026-08-03T00:00:00+08:00\"}"
```

*Expected: `409 STATE_CONFLICT`.*

### B4. PROPOSED → invest (skip approval)

> Uses pre-loaded Loan 1 (PROPOSED).

```bash
curl -s -X POST $BASE/api/loans/investments \
  -H 'Content-Type: application/json' \
  -d '{"loanId": 1, "investorId": 3001, "amount": 1000, "currency": "USD", "datetime": "2026-07-28T00:00:00+08:00"}'
```

*Expected: `409 STATE_CONFLICT` — `"Cannot invest a loan in PROPOSED state"`.*

### B5. PROPOSED → disburse (skip both approve and invest)

```bash
curl -s -X PATCH $BASE/api/loans/disburse \
  -H 'Content-Type: application/json' \
  -d '{"loanId": 1, "signedAgreementUrl": "https://example.com/x.pdf", "fieldOfficerEmployeeId": 4001, "disbursementDatetime": "2026-07-28T00:00:00+08:00"}'
```

*Expected: `409 STATE_CONFLICT`.*

### B6. APPROVED → disburse (skip invest)

> Uses pre-loaded Loan 100 (APPROVED, $10,000 not yet invested).

```bash
curl -s -X PATCH $BASE/api/loans/disburse \
  -H 'Content-Type: application/json' \
  -d '{"loanId": 100, "signedAgreementUrl": "https://example.com/x.pdf", "fieldOfficerEmployeeId": 4001, "disbursementDatetime": "2026-07-28T00:00:00+08:00"}'
```

*Expected: `409 STATE_CONFLICT` — `"Cannot disburse — loan is not fully invested"`.*

### B7. APPROVED → approve (double-approve)

```bash
curl -s -X PATCH $BASE/api/loans/approve \
  -H 'Content-Type: application/json' \
  -d '{"loanId": 100, "validatorEmployeeId": 2001, "approvalDatetime": "2026-07-28T00:00:00+08:00", "validatorPhotoUrls": ["http://x.jpg"]}'
```

*Expected: `409 STATE_CONFLICT`.*

---

## C. Business Logic — Validation Tests

### C1. Investment exceeds principal

> Loan 100 has principal 1,000,000. Try investing 1,100,000.

```bash
curl -s -X POST $BASE/api/loans/investments \
  -H 'Content-Type: application/json' \
  -d '{"loanId": 100, "investorId": 3001, "amount": 1100000, "currency": "USD", "datetime": "2026-07-28T00:00:00+08:00"}'
```

*Expected: `400 BAD_REQUEST` — `"Total investment cannot exceed principal"`.*

### C2. Currency mismatch

```bash
curl -s -X POST $BASE/api/loans/investments \
  -H 'Content-Type: application/json' \
  -d '{"loanId": 100, "investorId": 3001, "amount": 1000, "currency": "IDR", "datetime": "2026-07-28T00:00:00+08:00"}'
```

*Expected: `400 BAD_REQUEST` — `"Investment currency (IDR) must match loan currency (USD)"`.*

### C3. Zero-amount investment

```bash
curl -s -X POST $BASE/api/loans/investments \
  -H 'Content-Type: application/json' \
  -d '{"loanId": 100, "investorId": 3001, "amount": 0, "currency": "USD", "datetime": "2026-07-28T00:00:00+08:00"}'
```

*Expected: `400 VALIDATION`.*

### C4. Approval without validator photos

```bash
# Create a fresh loan
L2=$(curl -s -X POST $BASE/api/loans \
  -H 'Content-Type: application/json' \
  -d '{"borrowerId": 3001, "principalAmount": 1000, "interestRate": 5.0, "roi": 3.0, "currency": "USD"}' | jq -r .id)

# Try to approve with empty photos
curl -s -X PATCH $BASE/api/loans/approve \
  -H 'Content-Type: application/json' \
  -d "{\"loanId\": $L2, \"validatorEmployeeId\": 2001, \"approvalDatetime\": \"2026-07-28T00:00:00+08:00\", \"validatorPhotoUrls\": []}"
```

*Expected: `400 BAD_REQUEST` — `"Approval must include at least one validator photo URL"`.*

### C5. Disburse without signed agreement URL

```bash
curl -s -X PATCH $BASE/api/loans/disburse \
  -H 'Content-Type: application/json' \
  -d '{"loanId": 100, "fieldOfficerEmployeeId": 4001, "disbursementDatetime": "2026-07-28T00:00:00+08:00"}'
```

*Expected: `400 VALIDATION` — `"signedAgreementUrl is required"`.*

### C6. Disburse with non-pdf/jpeg signed agreement

```bash
curl -s -X PATCH $BASE/api/loans/disburse \
  -H 'Content-Type: application/json' \
  -d '{"loanId": 100, "signedAgreementUrl": "https://example.com/doc.txt", "fieldOfficerEmployeeId": 4001, "disbursementDatetime": "2026-07-28T00:00:00+08:00"}'
```

*Expected: `400 BAD_REQUEST` — `"signedAgreementUrl must be a pdf or jpeg file"`.*

### C7. Investment with negative amount

```bash
curl -s -X POST $BASE/api/loans/investments \
  -H 'Content-Type: application/json' \
  -d '{"loanId": 100, "investorId": 3001, "amount": -100, "currency": "USD", "datetime": "2026-07-28T00:00:00+08:00"}'
```

*Expected: `400 VALIDATION` — Jakarta `@Positive` constraint violation.*

---
## D. Bug Regression — Security & Data Integrity

### D1. Duplicate investor_id → 409 CONFLICT (Bug #10)

```bash
# Alice (3001) is pre-loaded. Try creating again:
curl -s -X POST $BASE/api/investors \
  -H 'Content-Type: application/json' \
  -d '{"investorId": 3001, "name": "Alice Duplicate", "emailUrl": "dupe@example.com", "registerDate": "2025-01-01"}' \
  -w "\nHTTP %{http_code}\n"
```

*Expected: `HTTP 409`, `code: "DATA_CONFLICT"` — NOT 500.*

### D2. Invest with fundStatus=RECEIVED → ignored, stays PENDING (Bug #9)

```bash
# Create a fresh loan for this test
L3=$(curl -s -X POST $BASE/api/loans \
  -H 'Content-Type: application/json' \
  -d '{"borrowerId": 4001, "principalAmount": 3000, "interestRate": 5.0, "roi": 3.0, "currency": "USD"}' | jq -r .id)

# Approve it
curl -s -X PATCH $BASE/api/loans/approve \
  -H 'Content-Type: application/json' \
  -d "{\"loanId\": $L3, \"validatorEmployeeId\": 2001, \"approvalDatetime\": \"2026-07-28T00:00:00+08:00\", \"validatorPhotoUrls\": [\"http://x.jpg\"]}" > /dev/null

# Try to invest with fundStatus=RECEIVED (attempting to bypass receiveFunds)
curl -s -X POST $BASE/api/loans/investments \
  -H 'Content-Type: application/json' \
  -d "{\"loanId\": $L3, \"investorId\": 3001, \"investorName\": \"Alice\", \"amount\": 3000, \"currency\": \"USD\", \"fundStatus\": \"RECEIVED\", \"datetime\": \"2026-07-28T01:00:00+08:00\"}" | jq '.investments[0].fundStatus'
```

*Expected: `"PENDING"` — user-supplied `RECEIVED` is ignored; funds must be confirmed via `receiveFunds`.*

### D3. receiveFunds with non-existent investmentId → 400 (Bug #8)

```bash
curl -s -X PATCH $BASE/api/loans/investments/receive \
  -H 'Content-Type: application/json' \
  -d "{\"loanId\": $L3, \"investmentId\": 99999}" \
  -w "\nHTTP %{http_code}\n"
```

*Expected: `HTTP 400` — `"Investment not found or does not belong to loan"`.*

### D4. receiveFunds with investment not belonging to loan → 400 (Bug #2)

```bash
# Get an investment from another loan (Loan 100 has investments from test data)
INV_LOAN100=$(curl -s $BASE/api/loans/100 | jq -r '.investments[0].id // empty')
if [ -n "$INV_LOAN100" ] && [ "$INV_LOAN100" != "null" ]; then
  curl -s -X PATCH $BASE/api/loans/investments/receive \
    -H 'Content-Type: application/json' \
    -d "{\"loanId\": $L3, \"investmentId\": $INV_LOAN100}" \
    -w "\nHTTP %{http_code}\n"
else
  echo "Skipped — Loan 100 has no investments yet. Run A4+A5 first."
fi
```

*Expected: `HTTP 400` — `"Investment not found or does not belong to loan"` — cross-loan fund marking is rejected.*

---

## Troubleshooting

| Problem | Solution |
|---------|----------|
| `mvn: command not found` | Install Maven (see Setup step 2) |
| `Connection refused` on port 9092 | Start the H2 TCP server (Terminal 1) |
| `Connection refused` on port 8080 | Start the Spring Boot app (Terminal 2) |
| `Table "LOANS" not found` | H2 TCP server was restarted — data dir was wiped. Restart the Spring Boot app to re-run schema.sql |
| `jq: command not found` | Remove `| jq` from curl commands, or `brew install jq` |
| H2 jar not found | Run `mvn dependency:resolve` first, then `find` again |
| Port 8080 already in use | `lsof -i :8080` to find the process, then `kill <PID>` |
