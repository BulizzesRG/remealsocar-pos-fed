package com.posfab.shared.features.shopping.repository

import com.posfab.shared.core.result.AppResult
import com.posfab.shared.features.shopping.domain.ShoppingCurrent
import com.posfab.shared.features.shopping.domain.ShoppingItem
import com.posfab.shared.features.shopping.domain.ShoppingItemStatus
import com.posfab.shared.features.shopping.domain.ShoppingList
import com.posfab.shared.features.shopping.domain.ShoppingListSummary
import com.posfab.shared.features.shopping.domain.ShoppingProduct

interface ShoppingRepository {
    suspend fun current(): AppResult<ShoppingCurrent>
    suspend fun history(limit: Int): AppResult<List<ShoppingListSummary>>
    suspend fun list(id: Long): AppResult<ShoppingList>
    suspend fun addItem(idempotencyKey: String, text: String, productId: Long?, qty: Double?): AppResult<ShoppingItem>
    suspend fun updateItem(id: Long, text: String, productId: Long?, qty: Double?): AppResult<ShoppingItem>
    suspend fun deleteItem(id: Long): AppResult<Unit>
    suspend fun setStatus(id: Long, status: ShoppingItemStatus): AppResult<ShoppingItem>
    suspend fun close(idempotencyKey: String, id: Long): AppResult<ShoppingList>
    suspend fun finish(idempotencyKey: String, id: Long, carryOver: List<Long>): AppResult<ShoppingList>
    suspend fun findProductByBarcode(code: String): AppResult<ShoppingProduct?>
    suspend fun searchProducts(query: String, limit: Int = DEFAULT_SEARCH_LIMIT): AppResult<List<ShoppingProduct>>

    companion object {
        const val DEFAULT_SEARCH_LIMIT = 5
    }
}
