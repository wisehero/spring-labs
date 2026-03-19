package com.wisehero.springlabs.common.dto

data class CursorPageResponse<T : Any>(
    val content: List<T>,
    val size: Int,
    val nextCursor: Long?,
    val hasNext: Boolean
) {
    companion object {
        fun <T : Any, R : Any> from(
            content: List<T>,
            size: Int,
            cursorExtractor: (T) -> Long,
            transform: (T) -> R
        ): CursorPageResponse<R> {
            val hasNext = content.size > size
            val items = if (hasNext) content.dropLast(1) else content
            val nextCursor = if (hasNext) cursorExtractor(items.last()) else null
            return CursorPageResponse(
                content = items.map(transform),
                size = size,
                nextCursor = nextCursor,
                hasNext = hasNext
            )
        }
    }
}
