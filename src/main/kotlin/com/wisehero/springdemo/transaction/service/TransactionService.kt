package com.wisehero.springdemo.transaction.service

import com.wisehero.springdemo.common.dto.PageResponse
import com.wisehero.springdemo.common.exception.BusinessException
import com.wisehero.springdemo.common.exception.ErrorCode
import com.wisehero.springdemo.repository.TransactionRepository
import com.wisehero.springdemo.transaction.dto.TransactionDetailResponse
import com.wisehero.springdemo.transaction.dto.TransactionListResponse
import com.wisehero.springdemo.transaction.dto.TransactionSearchRequest
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional(readOnly = true)
class TransactionService(
    private val transactionRepository: TransactionRepository
) {

    fun searchTransactions(request: TransactionSearchRequest): PageResponse<TransactionListResponse> {
        validateSearchRequest(request)

        val page = transactionRepository.search(request)
        return PageResponse.from(page) { TransactionListResponse.from(it) }
    }

    fun getTransaction(id: Long): TransactionDetailResponse {
        val transaction = transactionRepository.findById(id)
            .orElseThrow { BusinessException(ErrorCode.TRANSACTION_NOT_FOUND) }
        return TransactionDetailResponse.from(transaction)
    }

    private fun validateSearchRequest(request: TransactionSearchRequest) {
        if (request.startDateTime != null && request.endDateTime != null) {
            if (request.startDateTime.isAfter(request.endDateTime)) {
                throw BusinessException(ErrorCode.INVALID_DATE_RANGE)
            }
        }

        if (request.minAmount != null && request.maxAmount != null) {
            if (request.minAmount > request.maxAmount) {
                throw BusinessException(ErrorCode.INVALID_AMOUNT_RANGE)
            }
        }

        if (request.size !in 1..100) {
            throw BusinessException(ErrorCode.INVALID_PAGE_SIZE)
        }
    }
}
