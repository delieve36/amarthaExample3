-- ====================================================================
-- Loan Engine — Database Schema
-- Target:   H2 (MySQL compatibility mode)
-- Author:   developer
-- ====================================================================

DROP TABLE IF EXISTS disbursements;
DROP TABLE IF EXISTS investments;
DROP TABLE IF EXISTS approval_photos;
DROP TABLE IF EXISTS approvals;
DROP TABLE IF EXISTS investors;
DROP TABLE IF EXISTS loans;

-- -------------------------------------------------------------------
-- loans — 贷款主表
-- -------------------------------------------------------------------
CREATE TABLE loans (
    id                          BIGINT AUTO_INCREMENT PRIMARY KEY,
    borrower_id                 VARCHAR(50)    NOT NULL,
    borrower_name               VARCHAR(100),
    principal_amount            BIGINT         NOT NULL COMMENT 'smallest currency unit',
    interest_rate               DECIMAL(5, 2)  NOT NULL,
    roi                         DECIMAL(5, 2)  NOT NULL,
    currency                    VARCHAR(3)     NOT NULL DEFAULT 'CNY',
    curr_state                  VARCHAR(20)    NOT NULL DEFAULT 'PROPOSED',
    init_datetime               TIMESTAMP WITH TIME ZONE,
    agree_letter_send_datetime  TIMESTAMP WITH TIME ZONE,
    funds_received_datetime     TIMESTAMP WITH TIME ZONE,
    agree_letter_url            VARCHAR(500)
);

-- -------------------------------------------------------------------
-- investors — 投资者档案
-- -------------------------------------------------------------------
CREATE TABLE investors (
    investor_id   VARCHAR(50)  PRIMARY KEY,
    name          VARCHAR(100) NOT NULL,
    email_url     VARCHAR(200),
    register_date DATE
);

-- -------------------------------------------------------------------
-- investments — 投资记录
-- -------------------------------------------------------------------
CREATE TABLE investments (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    loan_id       BIGINT        NOT NULL,
    investor_id   VARCHAR(50)   NOT NULL,
    investor_name VARCHAR(100),
    amount        BIGINT        NOT NULL COMMENT 'smallest currency unit',
    currency      VARCHAR(3)    NOT NULL,
    datetime      TIMESTAMP WITH TIME ZONE,
    fund_received  BOOLEAN       NOT NULL DEFAULT FALSE,
    FOREIGN KEY (loan_id)     REFERENCES loans(id),
    FOREIGN KEY (investor_id) REFERENCES investors(investor_id)
);

-- -------------------------------------------------------------------
-- approvals — 批准记录
-- -------------------------------------------------------------------
CREATE TABLE approvals (
    id                      BIGINT AUTO_INCREMENT PRIMARY KEY,
    loan_id                 BIGINT        NOT NULL,
    validator_employee_id   VARCHAR(50)   NOT NULL,
    validator_employee_name VARCHAR(100),
    approval_datetime       TIMESTAMP WITH TIME ZONE,
    FOREIGN KEY (loan_id) REFERENCES loans(id)
);

-- -------------------------------------------------------------------
-- approval_photos — 批准证明照片（一对多）
-- -------------------------------------------------------------------
CREATE TABLE approval_photos (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    approval_id BIGINT       NOT NULL,
    photo_url   VARCHAR(500) NOT NULL,
    FOREIGN KEY (approval_id) REFERENCES approvals(id)
);

-- -------------------------------------------------------------------
-- disbursements — 放款记录
-- -------------------------------------------------------------------
CREATE TABLE disbursements (
    id                          BIGINT AUTO_INCREMENT PRIMARY KEY,
    loan_id                     BIGINT        NOT NULL,
    signed_agreement_url        VARCHAR(500),
    field_officer_employee_id   VARCHAR(50)   NOT NULL,
    field_officer_employee_name VARCHAR(100),
    disbursement_datetime       TIMESTAMP WITH TIME ZONE,
    success                    BOOLEAN       NOT NULL DEFAULT TRUE,
    FOREIGN KEY (loan_id) REFERENCES loans(id)
);
