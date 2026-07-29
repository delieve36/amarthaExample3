-- ====================================================================
-- Loan Engine — Self-test Data (auto-executed by Spring Boot sql.init)
-- ====================================================================

-- Test loan (PROPOSED) — $5,000.00 = 500000 cents
INSERT INTO loans (borrower_id, borrower_name, principal_amount, interest_rate,
                   roi, currency, curr_state, init_datetime)
VALUES (1001, 'Zhang Wei', 500000,
        10.00, 8.00, 'USD', 'PROPOSED', CURRENT_TIMESTAMP);

-- Test loan (APPROVED) — $10,000.00 = 1000000 cents
INSERT INTO loans (id, borrower_id, borrower_name, principal_amount, interest_rate,
                   roi, currency, curr_state, init_datetime)
VALUES (100, 1002, 'Li Ming', 1000000,
        12.50, 9.00, 'USD', 'APPROVED', CURRENT_TIMESTAMP);

INSERT INTO approvals (loan_id, validator_employee_id, validator_employee_name,
                       approval_datetime)
VALUES (100, 2001, 'Chen Jie', CURRENT_TIMESTAMP);

INSERT INTO approval_photos (approval_id, photo_url)
VALUES (1, 'https://storage.example.com/photos/proof1.jpg');

INSERT INTO approval_photos (approval_id, photo_url)
VALUES (1, 'https://storage.example.com/photos/proof2.jpg');

-- Test investors
INSERT INTO investors (investor_id, name, email_url, register_date)
VALUES (3001, 'Alice Wang', 'alice@example.com', '2025-01-15');

INSERT INTO investors (investor_id, name, email_url, register_date)
VALUES (3002, 'Bob Chen', 'bob@example.com', '2025-03-20');

INSERT INTO investors (investor_id, name, email_url, register_date)
VALUES (3003, 'Carol Liu', 'carol@example.com', '2025-06-10');

-- ====================================================================
-- Pre-loaded DISBURSED loan (full lifecycle coverage)
-- Loan 200: $5,000, funded by Alice ($3,000) + Bob ($2,000), disbursed
-- ====================================================================
INSERT INTO loans (id, borrower_id, borrower_name, principal_amount, interest_rate,
                   roi, currency, curr_state, init_datetime,
                   agree_letter_url, agree_letter_send_datetime,
                   funds_received_datetime)
VALUES (200, 1003, 'Wang Fang', 500000,
        9.00, 7.00, 'USD', 'DISBURSED', CURRENT_TIMESTAMP,
        'http://localhost:8080/api/loans/agreement/200', CURRENT_TIMESTAMP,
        CURRENT_TIMESTAMP);

INSERT INTO approvals (loan_id, validator_employee_id, validator_employee_name,
                       approval_datetime)
VALUES (200, 2002, 'Zhao Lei', CURRENT_TIMESTAMP);

INSERT INTO approval_photos (approval_id, photo_url)
VALUES (2, 'https://storage.example.com/photos/loan200-proof.jpg');

INSERT INTO investments (loan_id, investor_id, investor_name, amount, currency,
                         datetime, fund_status)
VALUES (200, 3001, 'Alice Wang', 300000, 'USD', CURRENT_TIMESTAMP, 'RECEIVED');

INSERT INTO investments (loan_id, investor_id, investor_name, amount, currency,
                         datetime, fund_status)
VALUES (200, 3002, 'Bob Chen', 200000, 'USD', CURRENT_TIMESTAMP, 'RECEIVED');

INSERT INTO disbursements (loan_id, signed_agreement_url,
                           field_officer_employee_id, field_officer_employee_name,
                           disbursement_datetime, disbursed)
VALUES (200, 'https://storage.example.com/loan200-signed.pdf',
        4002, 'Officer Liu', CURRENT_TIMESTAMP, TRUE);

INSERT INTO notification_outbox (loan_id, investor_id, recipient_email, type,
                                 status, agreement_url, sent_datetime, retry_count)
VALUES (200, 3001, 'alice@example.com', 'AGREEMENT_LETTER',
        'SENT', 'http://localhost:8080/api/loans/agreement/200', CURRENT_TIMESTAMP, 0);

INSERT INTO notification_outbox (loan_id, investor_id, recipient_email, type,
                                 status, agreement_url, sent_datetime, retry_count)
VALUES (200, 3002, 'bob@example.com', 'AGREEMENT_LETTER',
        'SENT', 'http://localhost:8080/api/loans/agreement/200', CURRENT_TIMESTAMP, 0);
