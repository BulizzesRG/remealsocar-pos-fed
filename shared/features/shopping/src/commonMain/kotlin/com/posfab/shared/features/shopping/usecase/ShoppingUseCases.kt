package com.posfab.shared.features.shopping.usecase

import com.posfab.shared.core.result.AppResult
import com.posfab.shared.features.shopping.domain.ShoppingCurrent
import com.posfab.shared.features.shopping.domain.ShoppingItem
import com.posfab.shared.features.shopping.domain.ShoppingItemStatus
import com.posfab.shared.features.shopping.domain.ShoppingList
import com.posfab.shared.features.shopping.domain.ShoppingListSummary
import com.posfab.shared.features.shopping.domain.ShoppingProduct
import com.posfab.shared.features.shopping.repository.ShoppingRepository

class ShoppingUseCases(private val repository: ShoppingRepository) {
    suspend fun current(): AppResult<ShoppingCurrent> = repository.current()

    suspend fun history(limit: Int): AppResult<List<ShoppingListSummary>> = repository.history(limit)

    suspend fun list(id: Long): AppResult<ShoppingList> = repository.list(id)

    suspend fun addItem(idempotencyKey: String, text: String, productId: Long?, qty: Double?): AppResult<ShoppingItem> =
        repository.addItem(idempotencyKey, text, productId, qty)

    suspend fun updateItem(id: Long, text: String, productId: Long?, qty: Double?): AppResult<ShoppingItem> =
        repository.updateItem(id, text, productId, qty)

    suspend fun deleteItem(id: Long): AppResult<Unit> = repository.deleteItem(id)

    suspend fun setStatus(id: Long, status: ShoppingItemStatus): AppResult<ShoppingItem> = repository.setStatus(id, status)

    suspend fun close(idempotencyKey: String, id: Long): AppResult<ShoppingList> = repository.close(idempotencyKey, id)

    suspend fun finish(idempotencyKey: String, id: Long, carryOver: List<Long>): AppResult<ShoppingList> =
        repository.finish(idempotencyKey, id, carryOver)

    suspend fun findProductByBarcode(code: String): AppResult<ShoppingProduct?> = repository.findProductByBarcode(code)

    suspend fun searchProducts(query: String, limit: Int): AppResult<List<ShoppingProduct>> =
        repository.searchProducts(query, limit)
}
