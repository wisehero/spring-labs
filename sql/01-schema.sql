-- 테이블 생성
CREATE TABLE IF NOT EXISTS transaction (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    approve_date_time DATETIME NOT NULL,
    amount DECIMAL(15, 2) NOT NULL,
    business_no VARCHAR(20) NOT NULL,
    pos_transaction_no VARCHAR(50) NOT NULL,
    payment_transaction_guid_no VARCHAR(100) NOT NULL,
    spare_transaction_guid_no VARCHAR(100) NOT NULL,
    transaction_state VARCHAR(20) NOT NULL,
    pos_cancel_transaction_no VARCHAR(50),
    cancel_date_time DATETIME,
    cancel_reason VARCHAR(200),
    cash_receipt_issue_yn BOOLEAN,
    cash_receipt_approve_no VARCHAR(50),
    cash_receipt_approve_date_time DATETIME,
    cash_receipt_issue_type VARCHAR(20),
    cash_receipt_auth_type VARCHAR(20),
    cash_receipt_issue_no VARCHAR(50),
    cash_receipt_cancel_approve_no VARCHAR(50),
    cash_receipt_cancel_date_time DATETIME,
    paper_receipt_print_yn BOOLEAN,
    INDEX idx_approve_date_time (approve_date_time),
    INDEX idx_business_no (business_no),
    INDEX idx_transaction_state (transaction_state)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;


show index from transaction;