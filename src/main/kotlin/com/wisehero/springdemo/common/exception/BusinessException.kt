package com.wisehero.springdemo.common.exception

class BusinessException(
    val errorCode: ErrorCode
) : RuntimeException(errorCode.message)
