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


-- ==========================================
-- Lab 07: N+1 Problem 실험용 테이블
-- ==========================================

CREATE TABLE IF NOT EXISTS team (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_team_name (name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS member (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    role VARCHAR(50) NOT NULL,
    team_id BIGINT NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_member_name (name),
    INDEX idx_member_team_id (team_id),
    CONSTRAINT fk_member_team FOREIGN KEY (team_id) REFERENCES team (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS team_tag (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    tag_name VARCHAR(100) NOT NULL,
    team_id BIGINT NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_team_tag_name (tag_name),
    INDEX idx_team_tag_team_id (team_id),
    CONSTRAINT fk_team_tag_team FOREIGN KEY (team_id) REFERENCES team (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;