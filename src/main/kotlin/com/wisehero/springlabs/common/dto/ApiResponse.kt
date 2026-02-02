package com.wisehero.springlabs.common.dto

import java.time.LocalDateTime

data class ApiResponse<T>(
    val success: Boolean,
    val code: String,
    val message: String,
    val data: T? = null,
    val timestamp: LocalDateTime = LocalDateTime.now()
) {
    companion object {
        fun <T> success(data: T, message: String = "요청이 성공적으로 처리되었습니다."): ApiResponse<T> {
            return ApiResponse(
                success = true,
                code = "SUCCESS",
                message = message,
                data = data
            )
        }

        fun <T> success(message: String = "요청이 성공적으로 처리되었습니다."): ApiResponse<T> {
            return ApiResponse(
                success = true,
                code = "SUCCESS",
                message = message,
                data = null
            )
        }

        fun <T> error(code: String, message: String): ApiResponse<T> {
            return ApiResponse(
                success = false,
                code = code,
                message = message,
                data = null
            )
        }
    }
}
