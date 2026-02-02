package com.wisehero.springlabs.common.exception

class BusinessException(
    val errorCode: ErrorCode
) : RuntimeException(errorCode.message)
