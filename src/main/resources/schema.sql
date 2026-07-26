-- ====================================================================
-- Loan Engine — Database Schema
-- Target:   H2 (MySQL compatibility mode)
-- ====================================================================

DROP TABLE IF EXISTS disbursements;
DROP TABLE IF EXISTS approval_photos;
DROP TABLE IF EXISTS investments;
DROP TABLE IF EXISTS approvals;
DROP TABLE IF EXISTS loans;

-- -------------------------------------------------------------------
-- loans — 贷款主表
-- -------------------------------------------------------------------
CREATE TABLE loans (
    id                          BIGINT AUTO_INCREMENT PRIMARY KEY,
    gmt_create                  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    gmt_modify                  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    borrower_id                 BIGINT       NOT NULL,
    borrower_name               VARCHAR(255),
    principal_amount            BIGINT       NOT NULL,
    interest_rate               DECIMAL(5,2) NOT NULL,
    roi                         DECIMAL(5,2) NOT NULL,
    currency                    VARCHAR(3)   NOT NULL DEFAULT 'USD',
    curr_state                  VARCHAR(20)  NOT NULL DEFAULT 'PROPOSED',
    init_datetime               TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    agree_letter_send_datetime  TIMESTAMP WITH TIME ZONE,
    funds_received_datetime     TIMESTAMP WITH TIME ZONE,
    agree_letter_url            VARCHAR(2000)
);

CREATE INDEX idx_loans_borrower ON loans(borrower_id);
CREATE INDEX idx_loans_state    ON loans(curr_state);

-- -------------------------------------------------------------------
-- approvals — 批准记录
-- -------------------------------------------------------------------
CREATE TABLE approvals (
    id                      BIGINT AUTO_INCREMENT PRIMARY KEY,
    gmt_create              TIMESTAMP  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    gmt_modify              TIMESTAMP  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    loan_id                 BIGINT     NOT NULL COMMENT 'FK → loans.id',
    validator_employee_id   BIGINT     NOT NULL,
    validator_employee_name VARCHAR(255),
    approval_datetime       TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX idx_approvals_loan ON approvals(loan_id);

-- -------------------------------------------------------------------
-- approval_photos — 批准证明照片（一对多）
-- -------------------------------------------------------------------
CREATE TABLE approval_photos (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    gmt_create  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    gmt_modify  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    approval_id BIGINT       NOT NULL COMMENT 'FK → approvals.id',
    photo_url   VARCHAR(2000) NOT NULL
);

CREATE INDEX idx_photos_approval ON approval_photos(approval_id);

-- -------------------------------------------------------------------
-- investments — 投资记录
-- -------------------------------------------------------------------
CREATE TABLE investments (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    gmt_create    TIMESTAMP  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    gmt_modify    TIMESTAMP  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    loan_id       BIGINT     NOT NULL COMMENT 'FK → loans.id',
    investor_id   BIGINT     NOT NULL,
    investor_name VARCHAR(255),
    amount        BIGINT     NOT NULL,
    currency      VARCHAR(3) NOT NULL DEFAULT 'USD',
    datetime      TIMESTAMP WITH TIME ZONE NOT NULL,
    fund_received BOOLEAN    NOT NULL DEFAULT FALSE
);

CREATE INDEX idx_investments_loan     ON investments(loan_id);
CREATE INDEX idx_investments_investor ON investments(investor_id);

-- -------------------------------------------------------------------
-- disbursements — 放款记录
-- -------------------------------------------------------------------
CREATE TABLE disbursements (
    id                          BIGINT AUTO_INCREMENT PRIMARY KEY,
    gmt_create                  TIMESTAMP  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    gmt_modify                  TIMESTAMP  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    loan_id                     BIGINT     NOT NULL COMMENT 'FK → loans.id',
    signed_agreement_url        VARCHAR(2000),
    field_officer_employee_id   BIGINT     NOT NULL,
    field_officer_employee_name VARCHAR(255),
    disbursement_datetime       TIMESTAMP WITH TIME ZONE NOT NULL,
    success                     BOOLEAN    NOT NULL DEFAULT FALSE
);

CREATE INDEX idx_disbursements_loan ON disbursements(loan_id);
