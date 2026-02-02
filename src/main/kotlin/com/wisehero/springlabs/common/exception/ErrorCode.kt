package com.wisehero.springlabs.common.exception

import org.springframework.http.HttpStatus

enum class ErrorCode(
    val status: HttpStatus,
    val code: String,
    val message: String
) {
    // Common
    INVALID_INPUT_VALUE(HttpStatus.BAD_REQUEST, "COMMON_001", "잘못된 입력값입니다."),
    INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "COMMON_002", "서버 내부 오류가 발생했습니다."),
    INVALID_TYPE_VALUE(HttpStatus.BAD_REQUEST, "COMMON_003", "잘못된 타입입니다."),
    METHOD_NOT_ALLOWED(HttpStatus.METHOD_NOT_ALLOWED, "COMMON_004", "지원하지 않는 HTTP 메서드입니다."),

    // Transaction
    TRANSACTION_NOT_FOUND(HttpStatus.NOT_FOUND, "TRANSACTION_001", "거래내역을 찾을 수 없습니다."),
    INVALID_DATE_RANGE(HttpStatus.BAD_REQUEST, "TRANSACTION_002", "시작일이 종료일보다 늦을 수 없습니다."),
    INVALID_AMOUNT_RANGE(HttpStatus.BAD_REQUEST, "TRANSACTION_003", "최소 금액이 최대 금액보다 클 수 없습니다."),
    INVALID_PAGE_SIZE(HttpStatus.BAD_REQUEST, "TRANSACTION_004", "페이지 크기는 1 이상 100 이하여야 합니다.")
}
