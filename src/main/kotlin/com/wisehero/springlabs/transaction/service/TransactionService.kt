package com.wisehero.springlabs.transaction.service

import com.wisehero.springlabs.common.dto.CursorPageResponse
import com.wisehero.springlabs.common.dto.PageResponse
import com.wisehero.springlabs.common.exception.BusinessException
import com.wisehero.springlabs.common.exception.ErrorCode
import com.wisehero.springlabs.repository.TransactionRepository
import com.wisehero.springlabs.transaction.dto.TransactionDetailResponse
import com.wisehero.springlabs.transaction.dto.TransactionListResponse
import com.wisehero.springlabs.transaction.dto.TransactionSearchRequest
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

    fun searchTransactionsWithCursor(
        request: TransactionSearchRequest,
        cursorId: Long?,
        size: Int
    ): CursorPageResponse<TransactionListResponse> {
        validateSearchRequest(request)
        val validatedSize = size.coerceIn(1, 100)
        val results = transactionRepository.searchWithCursor(request, cursorId, validatedSize)
        return CursorPageResponse.from(
            content = results,
            size = validatedSize,
            cursorExtractor = { it.id!! },
            transform = { TransactionListResponse.from(it) }
        )
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
    }
}
