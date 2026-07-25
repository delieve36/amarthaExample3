-- ====================================================================
-- Loan Engine — Sample Data (development use only)
-- ====================================================================

-- Test investor
INSERT INTO investors (investor_id, name, email_url, register_date)
VALUES ('INV001', 'Alice Tan', 'alice@example.com', '2025-01-15');

INSERT INTO investors (investor_id, name, email_url, register_date)
VALUES ('INV002', 'Bob Lee', 'bob@example.com', '2025-03-01');

-- Test loan (PROPOSED)
INSERT INTO loans (borrower_id, borrower_name, principal_amount, interest_rate,
                   roi, currency, curr_state, init_datetime)
VALUES ('BORROWER001', 'Zhang Wei', 500000000,
        10.00, 8.00, 'CNY', 'PROPOSED', CURRENT_TIMESTAMP);

-- Test loan (APPROVED)
INSERT INTO loans (id, borrower_id, borrower_name, principal_amount, interest_rate,
                   roi, currency, curr_state, init_datetime)
VALUES (100, 'BORROWER002', 'Li Ming', 1000000000,
        12.50, 9.00, 'CNY', 'APPROVED', CURRENT_TIMESTAMP);

INSERT INTO approvals (loan_id, validator_employee_id, validator_employee_name,
                       approval_datetime)
VALUES (100, 'EMP001', 'Chen Jie', CURRENT_TIMESTAMP);

INSERT INTO approval_photos (approval_id, photo_url)
VALUES (1, 'https://storage.example.com/photos/proof1.jpg');

INSERT INTO approval_photos (approval_id, photo_url)
VALUES (1, 'https://storage.example.com/photos/proof2.jpg');

-- Test loan (INVESTED + DISBURSED)
INSERT INTO loans (id, borrower_id, borrower_name, principal_amount, interest_rate,
                   roi, currency, curr_state, init_datetime,
                   agree_letter_send_datetime, funds_received_datetime)
VALUES (200, 'BORROWER003', 'Wang Fang', 300000000,
        15.00, 10.00, 'CNY', 'DISBURSED', CURRENT_TIMESTAMP,
        CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

INSERT INTO investments (loan_id, investor_id, investor_name,
                         amount, currency, datetime, fund_received)
VALUES (200, 'INV001', 'Alice Tan', 200000000, 'CNY', CURRENT_TIMESTAMP, TRUE);

INSERT INTO investments (loan_id, investor_id, investor_name,
                         amount, currency, datetime, fund_received)
VALUES (200, 'INV002', 'Bob Lee', 100000000, 'CNY', CURRENT_TIMESTAMP, TRUE);

INSERT INTO disbursements (loan_id, signed_agreement_url,
                           field_officer_employee_id, field_officer_employee_name,
                           disbursement_datetime, success)
VALUES (200, 'https://storage.example.com/agreements/loan200.pdf',
        'EMP003', 'Liu Yang', CURRENT_TIMESTAMP, TRUE);
