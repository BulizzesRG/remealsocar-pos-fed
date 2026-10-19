package com.posfab.shared.features.shopping

import com.posfab.shared.core.result.AppError
import com.posfab.shared.core.result.AppResult
import com.posfab.shared.features.shopping.domain.ShoppingCurrent
import com.posfab.shared.features.shopping.domain.ShoppingItem
import com.posfab.shared.features.shopping.domain.ShoppingItemStatus
import com.posfab.shared.features.shopping.domain.ShoppingList
import com.posfab.shared.features.shopping.domain.ShoppingListStatus
import com.posfab.shared.features.shopping.domain.ShoppingListSummary
import com.posfab.shared.features.shopping.domain.ShoppingProduct
import com.posfab.shared.features.shopping.repository.ShoppingRepository
import kotlinx.coroutines.CompletableDeferred
import kotlin.time.Instant

val T0: Instant = Instant.parse("2026-10-18T15:00:00Z")

val COCA = ShoppingProduct(1L, "Coca cola", "1234567891234")
val JAMON = ShoppingProduct(13L, "Jamón", "20000059")

fun shoppingItem(
    id: Long,
    text: String = "renglón $id",
    status: ShoppingItemStatus = ShoppingItemStatus.PENDING,
    productId: Long? = null,
    qty: Double? = null,
    carriedFrom: Long? = null,
    createdBy: String = "cajero",
) = ShoppingItem(
    id = id,
    text = text,
    productId = productId,
    productName = null,
    qty = qty,
    status = status,
    carriedFrom = carriedFrom,
    createdBy = createdBy,
    createdAt = T0,
)

fun shoppingList(
    id: Long,
    status: ShoppingListStatus = ShoppingListStatus.OPEN,
    items: List<ShoppingItem> = emptyList(),
    createdAt: Instant = T0,
) = ShoppingList(id = id, status = status, createdAt = createdAt, createdBy = "gerente", items = items)

data class AddCall(val key: String, val text: String, val productId: Long?, val qty: Double?)

data class UpdateCall(val id: Long, val text: String, val productId: Long?, val qty: Double?)

data class FinishCall(val key: String, val id: Long, val carry: List<Long>)

class FakeShoppingRepository(
    var open: ShoppingList? = null,
    var toShop: ShoppingList? = null,
    var products: List<ShoppingProduct> = listOf(COCA, JAMON),
    var barcodes: Map<String, ShoppingProduct> = mapOf("1234567891234" to COCA, "20000059" to JAMON),
) : ShoppingRepository {
    var currentCalls = 0
    val currentResults = ArrayDeque<AppResult<ShoppingCurrent>>()
    val currentGates = ArrayDeque<CompletableDeferred<Unit>>()

    val addCalls = mutableListOf<AddCall>()
    val addResults = ArrayDeque<AppResult<ShoppingItem>>()
    var addGate: CompletableDeferred<Unit>? = null

    val updateCalls = mutableListOf<UpdateCall>()
    val updateResults = ArrayDeque<AppResult<ShoppingItem>>()

    val deleteCalls = mutableListOf<Long>()
    val deleteResults = ArrayDeque<AppResult<Unit>>()

    val statusCalls = mutableListOf<Pair<Long, ShoppingItemStatus>>()
    val statusResults = ArrayDeque<AppResult<ShoppingItem>>()
    var statusGate: CompletableDeferred<Unit>? = null

    val closeCalls = mutableListOf<Pair<String, Long>>()
    val closeResults = ArrayDeque<AppResult<ShoppingList>>()

    val finishCalls = mutableListOf<FinishCall>()
    val finishResults = ArrayDeque<AppResult<ShoppingList>>()

    var historyCalls = 0
    var historyResult: AppResult<List<ShoppingListSummary>> = AppResult.Success(emptyList())

    val listCalls = mutableListOf<Long>()
    var listResult: AppResult<ShoppingList>? = null
    var listGate: CompletableDeferred<Unit>? = null

    val barcodeCalls = mutableListOf<String>()
    var barcodeFailure: AppError? = null

    val searchCalls = mutableListOf<String>()
    val searchGates = mutableMapOf<String, CompletableDeferred<Unit>>()
    var searchFailure: AppError? = null

    private var nextItemId = 500L
    private var nextListId = 90L

    override suspend fun current(): AppResult<ShoppingCurrent> {
        currentCalls += 1
        val gate = currentGates.removeFirstOrNull()
        val queued = currentResults.removeFirstOrNull()
        gate?.await()
        return queued ?: AppResult.Success(ShoppingCurrent(open, toShop))
    }

    override suspend fun history(limit: Int): AppResult<List<ShoppingListSummary>> {
        historyCalls += 1
        return historyResult
    }

    override suspend fun list(id: Long): AppResult<ShoppingList> {
        listCalls += id
        listGate?.await()
        return listResult ?: AppResult.Success(shoppingList(id, ShoppingListStatus.DONE))
    }

    override suspend fun addItem(idempotencyKey: String, text: String, productId: Long?, qty: Double?): AppResult<ShoppingItem> {
        addCalls += AddCall(idempotencyKey, text, productId, qty)
        addGate?.await()
        addResults.removeFirstOrNull()?.let { return it }
        val item = shoppingItem(nextItemId++, text, productId = productId, qty = qty)
        val list = open ?: shoppingList(nextListId++)
        open = list.copy(items = list.items + item)
        return AppResult.Success(item)
    }

    override suspend fun updateItem(id: Long, text: String, productId: Long?, qty: Double?): AppResult<ShoppingItem> {
        updateCalls += UpdateCall(id, text, productId, qty)
        updateResults.removeFirstOrNull()?.let { return it }
        val existing = open?.items?.firstOrNull { it.id == id } ?: return AppResult.Failure(AppError.Validation("Item not found"))
        val updated = existing.copy(text = text, productId = productId, qty = qty)
        open = open?.withItem(updated)
        return AppResult.Success(updated)
    }

    override suspend fun deleteItem(id: Long): AppResult<Unit> {
        deleteCalls += id
        deleteResults.removeFirstOrNull()?.let { return it }
        open = open?.withoutItem(id)
        return AppResult.Success(Unit)
    }

    override suspend fun setStatus(id: Long, status: ShoppingItemStatus): AppResult<ShoppingItem> {
        statusCalls += id to status
        statusGate?.await()
        statusResults.removeFirstOrNull()?.let { return it }
        val existing = toShop?.items?.firstOrNull { it.id == id } ?: return AppResult.Failure(AppError.Validation("Item not found"))
        val updated = existing.copy(status = status)
        toShop = toShop?.withItem(updated)
        return AppResult.Success(updated)
    }

    override suspend fun close(idempotencyKey: String, id: Long): AppResult<ShoppingList> {
        closeCalls += idempotencyKey to id
        closeResults.removeFirstOrNull()?.let { return it }
        val closed = open?.copy(status = ShoppingListStatus.TO_SHOP) ?: return AppResult.Failure(AppError.Conflict)
        toShop = closed
        open = null
        return AppResult.Success(closed)
    }

    override suspend fun finish(idempotencyKey: String, id: Long, carryOver: List<Long>): AppResult<ShoppingList> {
        finishCalls += FinishCall(idempotencyKey, id, carryOver)
        finishResults.removeFirstOrNull()?.let { return it }
        val done = toShop?.copy(status = ShoppingListStatus.DONE) ?: return AppResult.Failure(AppError.Conflict)
        val carried = done.items.filter { it.id in carryOver }.map {
            it.copy(id = nextItemId++, status = ShoppingItemStatus.PENDING, carriedFrom = it.id)
        }
        if (carried.isNotEmpty()) {
            val list = open ?: shoppingList(nextListId++)
            open = list.copy(items = list.items + carried)
        }
        toShop = null
        return AppResult.Success(done)
    }

    override suspend fun findProductByBarcode(code: String): AppResult<ShoppingProduct?> {
        barcodeCalls += code
        barcodeFailure?.let { return AppResult.Failure(it) }
        return AppResult.Success(barcodes[code])
    }

    override suspend fun searchProducts(query: String, limit: Int): AppResult<List<ShoppingProduct>> {
        searchCalls += query
        searchGates[query]?.await()
        searchFailure?.let { return AppResult.Failure(it) }
        return AppResult.Success(products.filter { it.name.contains(query, ignoreCase = true) }.take(limit))
    }
}
