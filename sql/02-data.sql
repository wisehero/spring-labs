-- 100만건 데이터 삽입 프로시저
DELIMITER //

DROP PROCEDURE IF EXISTS insert_transactions//

CREATE PROCEDURE insert_transactions()
BEGIN
    DECLARE i INT DEFAULT 0;
    DECLARE batch_size INT DEFAULT 10000;
    DECLARE total_records INT DEFAULT 1000000;
    DECLARE random_state INT;
    DECLARE random_amount DECIMAL(15,2);
    DECLARE random_date DATETIME;
    DECLARE is_cancelled BOOLEAN;
    DECLARE has_cash_receipt BOOLEAN;
    DECLARE business_no_val VARCHAR(20);
    DECLARE pos_no VARCHAR(50);
    DECLARE payment_guid VARCHAR(100);
    DECLARE spare_guid VARCHAR(100);
    DECLARE state_val VARCHAR(20);

    -- 트랜잭션 상태 배열 역할
    SET @states = '거래승인,거래취소';
    SET @issue_types = '소득공제,지출증빙';
    SET @auth_types = '휴대폰,사업자번호,현금영수증카드';

    WHILE i < total_records DO
        START TRANSACTION;

        SET @batch_count = 0;
        WHILE @batch_count < batch_size AND i < total_records DO
            -- 랜덤 값 생성
            SET random_amount = ROUND(RAND() * 100000 + 1000, 2);
            SET random_date = DATE_SUB(NOW(), INTERVAL FLOOR(RAND() * 365) DAY);
            SET random_date = DATE_ADD(random_date, INTERVAL FLOOR(RAND() * 86400) SECOND);
            SET random_state = FLOOR(RAND() * 10);
            SET is_cancelled = (random_state = 0); -- 10% 취소
            SET has_cash_receipt = (FLOOR(RAND() * 3) = 0); -- 33% 현금영수증

            -- 고유 값 생성
            SET business_no_val = LPAD(FLOOR(RAND() * 10000000000), 10, '0');
            SET pos_no = CONCAT('POS', DATE_FORMAT(random_date, '%Y%m%d%H%i%s'), LPAD(i, 10, '0'));
            SET payment_guid = CONCAT('KB0FRBEBM4TMP', DATE_FORMAT(random_date, '%Y%m%d%H%i%s'), LPAD(i, 10, '0'));
            SET spare_guid = CONCAT('KB0FRBEBQ4ZV2', DATE_FORMAT(random_date, '%Y%m%d%H%i%s'), LPAD(i, 10, '0'));

            IF is_cancelled THEN
                SET state_val = '거래취소';
            ELSE
                SET state_val = '거래승인';
            END IF;

            INSERT INTO transaction (
                approve_date_time,
                amount,
                business_no,
                pos_transaction_no,
                payment_transaction_guid_no,
                spare_transaction_guid_no,
                transaction_state,
                pos_cancel_transaction_no,
                cancel_date_time,
                cancel_reason,
                cash_receipt_issue_yn,
                cash_receipt_approve_no,
                cash_receipt_approve_date_time,
                cash_receipt_issue_type,
                cash_receipt_auth_type,
                cash_receipt_issue_no,
                cash_receipt_cancel_approve_no,
                cash_receipt_cancel_date_time,
                paper_receipt_print_yn
            ) VALUES (
                random_date,
                random_amount,
                business_no_val,
                pos_no,
                payment_guid,
                spare_guid,
                state_val,
                IF(is_cancelled, CONCAT('CANCEL', pos_no), NULL),
                IF(is_cancelled, DATE_ADD(random_date, INTERVAL FLOOR(RAND() * 3600) SECOND), NULL),
                IF(is_cancelled, ELT(FLOOR(RAND() * 3) + 1, '고객 요청', '상품 불량', '단순 변심'), NULL),
                has_cash_receipt,
                IF(has_cash_receipt, CONCAT('CR', DATE_FORMAT(random_date, '%Y%m%d'), LPAD(i, 6, '0')), NULL),
                IF(has_cash_receipt, DATE_ADD(random_date, INTERVAL 5 SECOND), NULL),
                IF(has_cash_receipt, ELT(FLOOR(RAND() * 2) + 1, '소득공제', '지출증빙'), NULL),
                IF(has_cash_receipt, ELT(FLOOR(RAND() * 3) + 1, '휴대폰', '사업자번호', '현금영수증카드'), NULL),
                IF(has_cash_receipt, CONCAT('010-', LPAD(FLOOR(RAND() * 10000), 4, '0'), '-', LPAD(FLOOR(RAND() * 10000), 4, '0')), NULL),
                NULL,
                NULL,
                (FLOOR(RAND() * 2) = 0)
            );

            SET i = i + 1;
            SET @batch_count = @batch_count + 1;
        END WHILE;

        COMMIT;

        -- 진행 상황 로그 (10만건마다)
        IF i % 100000 = 0 THEN
            SELECT CONCAT('Inserted ', i, ' records...') AS progress;
        END IF;
    END WHILE;

    SELECT CONCAT('Completed! Total records: ', i) AS result;
END//

DELIMITER ;

-- 프로시저 실행
CALL insert_transactions();

-- 프로시저 삭제
DROP PROCEDURE IF EXISTS insert_transactions;
