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

## Run Unit Tests (70 tests, < 5 s)

```bash
mvn test
```

Expected: `Tests run: 70, Failures: 0, Errors: 0, Skipped: 0 — BUILD SUCCESS`

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
## E. Complex Scenario — Multi-Borrower × Multi-Bank Cross-Investment

```
Participants:
  Bank1 = investor 3001 (Alice)    Bank2 = investor 3002 (Bob)    Bank3 = investor 3003 (Carol)

Borrower A — Loan $10,000:                    Borrower B — Loan $8,000:
  Bank1: $3,000 + $2,000  (2笔)                Bank1: $2,000              (1笔)
  Bank2: $3,000            (1笔)                Bank2: $2,000+$1,000+$1,000 (3笔!)
  Bank3: $2,000            (1笔)                Bank3: $2,000              (1笔)
  ─────────────────────────────                 ─────────────────────────────
  Total: $10,000 → INVESTED                    Total: $8,000 → INVESTED

Funds received:
  Loan A: Bank1(2笔)✓ + Bank2✓ + Bank3✗ → fundsReceivedDatetime NOT set
  Loan B: ALL ✓ → fundsReceivedDatetime set
```

### E1. Create and approve both loans

```bash
# Borrower A: $10,000
LOAN_A=$(curl -s -X POST $BASE/api/loans \
  -H 'Content-Type: application/json' \
  -d '{"borrowerId": 8001, "borrowerName": "Borrower A", "principalAmount": 10000, "interestRate": 8.0, "roi": 6.0, "currency": "USD"}' | jq -r .id)
echo "Loan A: $LOAN_A"

# Borrower B: $8,000
LOAN_B=$(curl -s -X POST $BASE/api/loans \
  -H 'Content-Type: application/json' \
  -d '{"borrowerId": 8002, "borrowerName": "Borrower B", "principalAmount": 8000, "interestRate": 7.5, "roi": 5.5, "currency": "USD"}' | jq -r .id)
echo "Loan B: $LOAN_B"

# Approve both
curl -s -X PATCH $BASE/api/loans/approve \
  -H 'Content-Type: application/json' \
  -d "{\"loanId\": $LOAN_A, \"validatorEmployeeId\": 2001, \"approvalDatetime\": \"2026-07-29T09:00:00+08:00\", \"validatorPhotoUrls\": [\"https://example.com/photo-a.jpg\"]}" > /dev/null

curl -s -X PATCH $BASE/api/loans/approve \
  -H 'Content-Type: application/json' \
  -d "{\"loanId\": $LOAN_B, \"validatorEmployeeId\": 2001, \"approvalDatetime\": \"2026-07-29T09:05:00+08:00\", \"validatorPhotoUrls\": [\"https://example.com/photo-b.jpg\"]}" > /dev/null

echo "Both loans APPROVED"
```

### E2. Invest — Loan A (4 investments from 3 banks)

```bash
# Bank1 #1: $3,000
curl -s -X POST $BASE/api/loans/investments \
  -H 'Content-Type: application/json' \
  -d "{\"loanId\": $LOAN_A, \"investorId\": 3001, \"investorName\": \"Bank1\", \"amount\": 3000, \"currency\": \"USD\", \"datetime\": \"2026-07-29T09:10:00+08:00\"}" | jq .currState
# → APPROVED (3000/10000)

# Bank1 #2: $2,000
curl -s -X POST $BASE/api/loans/investments \
  -H 'Content-Type: application/json' \
  -d "{\"loanId\": $LOAN_A, \"investorId\": 3001, \"investorName\": \"Bank1\", \"amount\": 2000, \"currency\": \"USD\", \"datetime\": \"2026-07-29T09:11:00+08:00\"}" | jq .currState
# → APPROVED (5000/10000)

# Bank2: $3,000
curl -s -X POST $BASE/api/loans/investments \
  -H 'Content-Type: application/json' \
  -d "{\"loanId\": $LOAN_A, \"investorId\": 3002, \"investorName\": \"Bank2\", \"amount\": 3000, \"currency\": \"USD\", \"datetime\": \"2026-07-29T09:12:00+08:00\"}" | jq .currState
# → APPROVED (8000/10000)

# Bank3: $2,000 → triggers INVESTED
curl -s -X POST $BASE/api/loans/investments \
  -H 'Content-Type: application/json' \
  -d "{\"loanId\": $LOAN_A, \"investorId\": 3003, \"investorName\": \"Bank3\", \"amount\": 2000, \"currency\": \"USD\", \"datetime\": \"2026-07-29T09:13:00+08:00\"}" | jq '{currState, agreeLetterUrl, investmentsCount: (.investments | length)}'
```

*Expected: `currState: "INVESTED"`, `agreeLetterUrl` populated, `investmentsCount: 4`.*

### E3. Invest — Loan B (5 investments from 3 banks, Bank2 does 3)

```bash
# Bank1: $2,000
curl -s -X POST $BASE/api/loans/investments \
  -H 'Content-Type: application/json' \
  -d "{\"loanId\": $LOAN_B, \"investorId\": 3001, \"investorName\": \"Bank1\", \"amount\": 2000, \"currency\": \"USD\", \"datetime\": \"2026-07-29T09:14:00+08:00\"}" | jq .currState
# → APPROVED (2000/8000)

# Bank2 #1: $2,000
curl -s -X POST $BASE/api/loans/investments \
  -H 'Content-Type: application/json' \
  -d "{\"loanId\": $LOAN_B, \"investorId\": 3002, \"investorName\": \"Bank2\", \"amount\": 2000, \"currency\": \"USD\", \"datetime\": \"2026-07-29T09:15:00+08:00\"}" | jq .currState
# → APPROVED (4000/8000)

# Bank2 #2: $1,000
curl -s -X POST $BASE/api/loans/investments \
  -H 'Content-Type: application/json' \
  -d "{\"loanId\": $LOAN_B, \"investorId\": 3002, \"investorName\": \"Bank2\", \"amount\": 1000, \"currency\": \"USD\", \"datetime\": \"2026-07-29T09:16:00+08:00\"}" | jq .currState
# → APPROVED (5000/8000)

# Bank2 #3: $1,000
curl -s -X POST $BASE/api/loans/investments \
  -H 'Content-Type: application/json' \
  -d "{\"loanId\": $LOAN_B, \"investorId\": 3002, \"investorName\": \"Bank2\", \"amount\": 1000, \"currency\": \"USD\", \"datetime\": \"2026-07-29T09:17:00+08:00\"}" | jq .currState
# → APPROVED (6000/8000)

# Bank3: $2,000 → triggers INVESTED
curl -s -X POST $BASE/api/loans/investments \
  -H 'Content-Type: application/json' \
  -d "{\"loanId\": $LOAN_B, \"investorId\": 3003, \"investorName\": \"Bank3\", \"amount\": 2000, \"currency\": \"USD\", \"datetime\": \"2026-07-29T09:18:00+08:00\"}" | jq '{currState, agreeLetterUrl, investmentsCount: (.investments | length)}'
```

*Expected: `currState: "INVESTED"`, `agreeLetterUrl` populated, `investmentsCount: 5`.*

### E4. Confirm funds — Loan A: Bank1 (both) + Bank2 received, Bank3 NOT received

```bash
# Fetch Loan A investment IDs
echo "Loan A investments:"
curl -s $BASE/api/loans/$LOAN_A | jq '.investments[] | {id, investorName, amount, fundStatus}'

# Bank1 investments → both RECEIVED
INV_A1=$(curl -s $BASE/api/loans/$LOAN_A | jq '.investments[] | select(.investorName=="Bank1" and .amount==3000) | .id')
INV_A2=$(curl -s $BASE/api/loans/$LOAN_A | jq '.investments[] | select(.investorName=="Bank1" and .amount==2000) | .id')
# Bank2 investment → RECEIVED
INV_A3=$(curl -s $BASE/api/loans/$LOAN_A | jq '.investments[] | select(.investorName=="Bank2") | .id')
# Bank3 investment → NOT received (skip marking it)

curl -s -X PATCH $BASE/api/loans/investments/receive \
  -H 'Content-Type: application/json' \
  -d "{\"loanId\": $LOAN_A, \"investmentId\": $INV_A1}" -w "Bank1-$3000: HTTP %{http_code}\n"
curl -s -X PATCH $BASE/api/loans/investments/receive \
  -H 'Content-Type: application/json' \
  -d "{\"loanId\": $LOAN_A, \"investmentId\": $INV_A2}" -w "Bank1-$2000: HTTP %{http_code}\n"
curl -s -X PATCH $BASE/api/loans/investments/receive \
  -H 'Content-Type: application/json' \
  -d "{\"loanId\": $LOAN_A, \"investmentId\": $INV_A3}" -w "Bank2:      HTTP %{http_code}\n"
```

*Expected: all `HTTP 204`.*

### E5. Confirm funds — Loan B: ALL received

```bash
# Fetch Loan B investment IDs and mark ALL as received
INV_B_IDS=$(curl -s $BASE/api/loans/$LOAN_B | jq -r '.investments[].id')
for id in $INV_B_IDS; do
  curl -s -X PATCH $BASE/api/loans/investments/receive \
    -H 'Content-Type: application/json' \
    -d "{\"loanId\": $LOAN_B, \"investmentId\": $id}" -w "Inv#$id: HTTP %{http_code}\n"
done
```

*Expected: all `HTTP 204`.*

### E6. Verify Loan A — INVESTED, fundsReceivedDatetime NOT set (Bank3 unpaid)

```bash
curl -s $BASE/api/loans/$LOAN_A | jq '{
  currState,
  fundsReceivedDatetime,
  agreeLetterUrl,
  investmentSummary: [.investments[]? | {name: .investorName, amount, fundStatus}]
}'
```

*Expected: `currState: "INVESTED"`, `fundsReceivedDatetime: null` (Bank3 is still PENDING), `agreeLetterUrl` populated.*

### E7. Verify Loan B — INVESTED, fundsReceivedDatetime IS set (all paid)

```bash
curl -s $BASE/api/loans/$LOAN_B | jq '{
  currState,
  fundsReceivedDatetime,
  agreeLetterUrl,
  investmentSummary: [.investments[]? | {name: .investorName, amount, fundStatus}]
}'
```

*Expected: `currState: "INVESTED"`, `fundsReceivedDatetime` populated (not null), all `fundStatus: "RECEIVED"`.*

### E8. Verify outbox — both loans have notification records

```bash
echo "=== Loan A notifications ==="
curl -s $BASE/api/loans/notifications/$LOAN_A | jq '[.[] | {investorId, status, recipientEmail}]'

echo "=== Loan B notifications ==="
curl -s $BASE/api/loans/notifications/$LOAN_B | jq '[.[] | {investorId, status, recipientEmail}]'
```

*Expected: each loan has 3 notification records (one per unique investor), all `status: "SENT"`.*

### E9. Cross-loan guard — mark Loan B's investment with Loan A's ID

```bash
# Pick any investment from Loan B
INV_B1=$(curl -s $BASE/api/loans/$LOAN_B | jq -r '.investments[0].id')

# Try to mark it as received under Loan A
curl -s -X PATCH $BASE/api/loans/investments/receive \
  -H 'Content-Type: application/json' \
  -d "{\"loanId\": $LOAN_A, \"investmentId\": $INV_B1}" \
  -w "\nHTTP %{http_code}\n"
```

*Expected: `HTTP 400` — `"Investment not found or does not belong to loan"`.*

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
