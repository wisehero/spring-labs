package com.wisehero.springdemo.repository

import com.querydsl.core.types.OrderSpecifier
import com.querydsl.core.types.dsl.BooleanExpression
import com.querydsl.jpa.impl.JPAQueryFactory
import com.wisehero.springdemo.entity.QTransaction
import com.wisehero.springdemo.entity.Transaction
import com.wisehero.springdemo.transaction.dto.TransactionSearchRequest
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Repository
import java.math.BigDecimal
import java.time.LocalDateTime

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
        val conditions = mutableListOf<BooleanExpression>()

        request.startDateTime?.let {
            conditions.add(approveDateTimeGoe(it))
        }

        request.endDateTime?.let {
            conditions.add(approveDateTimeLoe(it))
        }

        request.transactionState?.let {
            conditions.add(transactionStateEq(it))
        }

        request.businessNo?.let {
            conditions.add(businessNoLike(it))
        }

        request.minAmount?.let {
            conditions.add(amountGoe(it))
        }

        request.maxAmount?.let {
            conditions.add(amountLoe(it))
        }

        request.posTransactionNo?.let {
            conditions.add(posTransactionNoLike(it))
        }

        request.cashReceiptIssueYn?.let {
            conditions.add(cashReceiptIssueYnEq(it))
        }

        return conditions
    }

    private fun approveDateTimeGoe(startDateTime: LocalDateTime): BooleanExpression {
        return transaction.approveDateTime.goe(startDateTime)
    }

    private fun approveDateTimeLoe(endDateTime: LocalDateTime): BooleanExpression {
        return transaction.approveDateTime.loe(endDateTime)
    }

    private fun transactionStateEq(state: String): BooleanExpression {
        return transaction.transactionState.eq(state)
    }

    private fun businessNoLike(businessNo: String): BooleanExpression {
        return transaction.businessNo.contains(businessNo)
    }

    private fun amountGoe(minAmount: BigDecimal): BooleanExpression {
        return transaction.amount.goe(minAmount)
    }

    private fun amountLoe(maxAmount: BigDecimal): BooleanExpression {
        return transaction.amount.loe(maxAmount)
    }

    private fun posTransactionNoLike(posTransactionNo: String): BooleanExpression {
        return transaction.posTransactionNo.contains(posTransactionNo)
    }

    private fun cashReceiptIssueYnEq(cashReceiptIssueYn: Boolean): BooleanExpression {
        return transaction.cashReceiptIssueYn.eq(cashReceiptIssueYn)
    }

    private fun buildOrderSpecifier(sortBy: String, sortDirection: String): OrderSpecifier<*> {
        val isAsc = sortDirection.equals("asc", ignoreCase = true)

        return when (sortBy) {
            "approveDateTime" -> if (isAsc) transaction.approveDateTime.asc() else transaction.approveDateTime.desc()
            "amount" -> if (isAsc) transaction.amount.asc() else transaction.amount.desc()
            "businessNo" -> if (isAsc) transaction.businessNo.asc() else transaction.businessNo.desc()
            "transactionState" -> if (isAsc) transaction.transactionState.asc() else transaction.transactionState.desc()
            "id" -> if (isAsc) transaction.id.asc() else transaction.id.desc()
            else -> transaction.approveDateTime.desc()
        }
    }
}
