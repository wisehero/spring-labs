package com.wisehero.springlabs.repository

import com.querydsl.core.types.OrderSpecifier
import com.querydsl.core.types.dsl.BooleanExpression
import com.querydsl.jpa.impl.JPAQueryFactory
import com.wisehero.springlabs.entity.QTransaction
import com.wisehero.springlabs.entity.Transaction
import com.wisehero.springlabs.transaction.dto.TransactionSearchRequest
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Repository

@Repository
class TransactionRepositoryImpl(
    private val queryFactory: JPAQueryFactory
) : TransactionRepositoryCustom {

    private val transaction = QTransaction.transaction

    override fun search(request: TransactionSearchRequest): Page<Transaction> {
        val pageable = PageRequest.of(request.getValidatedPage(), request.getValidatedSize())

        val whereConditions = buildWhereConditions(request)
        val orderSpecifier = buildOrderSpecifier(request.sortBy, request.sortDirection)

        val content = queryFactory
            .selectFrom(transaction)
            .where(*whereConditions.toTypedArray())
            .orderBy(orderSpecifier)
            .offset(pageable.offset)
            .limit(pageable.pageSize.toLong())
            .fetch()

        val total = if (request.needsCountQuery()) {
            queryFactory
                .select(transaction.count())
                .from(transaction)
                .where(*whereConditions.toTypedArray())
                .fetchOne() ?: 0L
        } else {
            request.totalElements!!
        }

        return PageImpl(content, pageable, total)
    }

    private fun buildWhereConditions(request: TransactionSearchRequest): List<BooleanExpression> {
        return listOfNotNull(
            request.startDateTime?.let { transaction.approveDateTime.goe(it) },
            request.endDateTime?.let { transaction.approveDateTime.loe(it) },
            request.transactionState?.let { transaction.transactionState.eq(it) },
            request.businessNo?.let { transaction.businessNo.contains(it) },
            request.minAmount?.let { transaction.amount.goe(it) },
            request.maxAmount?.let { transaction.amount.loe(it) },
            request.posTransactionNo?.let { transaction.posTransactionNo.contains(it) },
            request.cashReceiptIssueYn?.let { transaction.cashReceiptIssueYn.eq(it) }
        )
    }

    private fun buildOrderSpecifier(sortBy: String, sortDirection: String): OrderSpecifier<*> {
        val path = when (sortBy) {
            "approveDateTime" -> transaction.approveDateTime
            "amount" -> transaction.amount
            "businessNo" -> transaction.businessNo
            "transactionState" -> transaction.transactionState
            "id" -> transaction.id
            else -> transaction.approveDateTime
        }

        return if (sortDirection.equals("asc", ignoreCase = true)) path.asc() else path.desc()
    }
}
