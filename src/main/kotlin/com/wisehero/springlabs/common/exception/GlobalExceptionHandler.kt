package com.wisehero.springlabs.common.exception

import com.wisehero.springlabs.common.dto.ApiResponse
import org.slf4j.LoggerFactory
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException
import org.springframework.web.servlet.resource.NoResourceFoundException

@RestControllerAdvice
class GlobalExceptionHandler {

    private val log = LoggerFactory.getLogger(javaClass)

    @ExceptionHandler(BusinessException::class)
    fun handleBusinessException(e: BusinessException): ResponseEntity<ApiResponse<Nothing>> {
        log.warn("BusinessException: {}", e.errorCode.message)
        return ResponseEntity
            .status(e.errorCode.status)
            .body(ApiResponse.error(e.errorCode.code, e.errorCode.message))
    }

    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun handleMethodArgumentNotValidException(e: MethodArgumentNotValidException): ResponseEntity<ApiResponse<Nothing>> {
        val message = e.bindingResult.fieldErrors
            .joinToString(", ") { "${it.field}: ${it.defaultMessage}" }
        log.warn("Validation failed: {}", message)
        return ResponseEntity
            .badRequest()
            .body(ApiResponse.error(ErrorCode.INVALID_INPUT_VALUE.code, message))
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException::class)
    fun handleMethodArgumentTypeMismatchException(e: MethodArgumentTypeMismatchException): ResponseEntity<ApiResponse<Nothing>> {
        val message = "${e.name}: 잘못된 값 '${e.value}'"
        log.warn("Type mismatch: {}", message)
        return ResponseEntity
            .badRequest()
            .body(ApiResponse.error(ErrorCode.INVALID_TYPE_VALUE.code, message))
    }

    @ExceptionHandler(NoResourceFoundException::class)
    fun handleNoResourceFoundException(e: NoResourceFoundException): ResponseEntity<ApiResponse<Nothing>> {
        log.warn("Resource not found: {}", e.message)
        return ResponseEntity
            .notFound()
            .build()
    }

    @ExceptionHandler(Exception::class)
    fun handleException(e: Exception): ResponseEntity<ApiResponse<Nothing>> {
        log.error("Unexpected error occurred", e)
        return ResponseEntity
            .internalServerError()
            .body(ApiResponse.error(ErrorCode.INTERNAL_SERVER_ERROR.code, ErrorCode.INTERNAL_SERVER_ERROR.message))
    }
}
