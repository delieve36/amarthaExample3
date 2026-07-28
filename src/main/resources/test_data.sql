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
