package com.posfab.shared.features.shopping.repository

import com.posfab.shared.auth.refresh.AuthorizedApiExecutor
import com.posfab.shared.core.result.AppError
import com.posfab.shared.core.result.AppResult
import com.posfab.shared.features.shopping.domain.ShoppingCurrent
import com.posfab.shared.features.shopping.domain.ShoppingItem
import com.posfab.shared.features.shopping.domain.ShoppingItemStatus
import com.posfab.shared.features.shopping.domain.ShoppingList
import com.posfab.shared.features.shopping.domain.ShoppingListStatus
import com.posfab.shared.features.shopping.domain.ShoppingListSummary
import com.posfab.shared.features.shopping.domain.ShoppingProduct
import com.posfab.shared.features.shopping.domain.plainQty
import com.posfab.shared.network.catalog.CatalogApi
import com.posfab.shared.network.shopping.ShoppingApi
import com.posfab.shared.network.shopping.ShoppingItemDto
import com.posfab.shared.network.shopping.ShoppingItemRequestDto
import com.posfab.shared.network.shopping.ShoppingItemStatusRequestDto
import com.posfab.shared.network.shopping.ShoppingListDoneRequestDto
import com.posfab.shared.network.shopping.ShoppingListDto
import com.posfab.shared.network.shopping.ShoppingListSummaryDto
import kotlin.time.Instant

class ShoppingRepositoryImpl(
    private val shoppingApi: ShoppingApi,
    private val catalogApi: CatalogApi,
    private val executor: AuthorizedApiExecutor,
) : ShoppingRepository {
    override suspend fun current(): AppResult<ShoppingCurrent> =
        when (val result = authorized { shoppingApi.current(it) }) {
            is AppResult.Success -> AppResult.Success(
                ShoppingCurrent(
                    open = result.value.open?.toDomain()?.takeIf { it.status == ShoppingListStatus.OPEN },
                    toShop = result.value.toShop?.toDomain()?.takeIf { it.status == ShoppingListStatus.TO_SHOP },
                ),
            )
            is AppResult.Failure -> result
        }

    override suspend fun history(limit: Int): AppResult<List<ShoppingListSummary>> =
        when (val result = authorized { shoppingApi.history(it, limit) }) {
            is AppResult.Success -> AppResult.Success(result.value.items.mapNotNull { it.toDomain() })
            is AppResult.Failure -> result
        }

    override suspend fun list(id: Long): AppResult<ShoppingList> =
        when (val result = authorized { shoppingApi.get(it, id) }) {
            is AppResult.Success -> result.value.toDomain()?.let { AppResult.Success(it) } ?: malformed()
            is AppResult.Failure -> result
        }

    override suspend fun addItem(
        idempotencyKey: String,
        text: String,
        productId: Long?,
        qty: Double?,
    ): AppResult<ShoppingItem> =
        authorized { shoppingApi.addItem(it, idempotencyKey, itemRequest(text, productId, qty)) }.mapItem()

    override suspend fun updateItem(id: Long, text: String, productId: Long?, qty: Double?): AppResult<ShoppingItem> =
        authorized { shoppingApi.updateItem(it, id, itemRequest(text, productId, qty)) }.mapItem()

    override suspend fun deleteItem(id: Long): AppResult<Unit> = authorized { shoppingApi.deleteItem(it, id) }

    override suspend fun setStatus(id: Long, status: ShoppingItemStatus): AppResult<ShoppingItem> =
        authorized { shoppingApi.setItemStatus(it, id, ShoppingItemStatusRequestDto(status.name)) }.mapItem()

    override suspend fun close(idempotencyKey: String, id: Long): AppResult<ShoppingList> =
        authorized { shoppingApi.close(it, idempotencyKey, id) }.mapList()

    override suspend fun finish(idempotencyKey: String, id: Long, carryOver: List<Long>): AppResult<ShoppingList> =
        authorized { shoppingApi.finish(it, idempotencyKey, id, ShoppingListDoneRequestDto(carryOver.distinct())) }.mapList()

    override suspend fun findProductByBarcode(code: String): AppResult<ShoppingProduct?> =
        when (val lookup = authorized { catalogApi.findByBarcode(it, code) }) {
            is AppResult.Success -> AppResult.Success(
                ShoppingProduct(id = lookup.value.product.id, name = lookup.value.product.name.trim(), barcode = code),
            )
            is AppResult.Failure -> if (lookup.error.isBarcodeNotFound()) AppResult.Success(null) else lookup
        }

    override suspend fun searchProducts(query: String, limit: Int): AppResult<List<ShoppingProduct>> =
        when (val result = authorized { catalogApi.listProducts(it, query.trim(), SEARCH_FETCH_LIMIT, 0, true) }) {
            is AppResult.Success -> AppResult.Success(
                result.value.items
                    .filter { it.active }
                    .mapNotNull { dto ->
                        dto.id.toLongOrNull()?.let { ShoppingProduct(it, dto.name.trim(), dto.barcode?.takeIf(String::isNotBlank)) }
                    }
                    .take(limit),
            )
            is AppResult.Failure -> result
        }

    private suspend fun <T> authorized(block: suspend (String) -> AppResult<T>): AppResult<T> = executor.execute(block)

    private fun itemRequest(text: String, productId: Long?, qty: Double?) = ShoppingItemRequestDto(
        text = text.trim(),
        productId = productId,
        qty = qty?.let(::plainQty),
    )

    private fun AppResult<ShoppingItemDto>.mapItem(): AppResult<ShoppingItem> = when (this) {
        is AppResult.Success -> value.toDomain()?.let { AppResult.Success(it) } ?: malformed()
        is AppResult.Failure -> this
    }

    private fun AppResult<ShoppingListDto>.mapList(): AppResult<ShoppingList> = when (this) {
        is AppResult.Success -> value.toDomain()?.let { AppResult.Success(it) } ?: malformed()
        is AppResult.Failure -> this
    }

    private fun malformed(): AppResult.Failure = AppResult.Failure(AppError.Unknown(MALFORMED))

    private fun ShoppingListDto.toDomain(): ShoppingList? {
        val parsedStatus = ShoppingListStatus.fromRaw(status) ?: return null
        val created = parseInstant(createdAt) ?: return null
        return ShoppingList(
            id = id,
            status = parsedStatus,
            createdAt = created,
            createdBy = createdBy?.trim().orEmpty(),
            closedAt = closedAt?.let(::parseInstant),
            doneAt = doneAt?.let(::parseInstant),
            items = items.map { it.toDomain() ?: return null },
        )
    }

    private fun ShoppingListSummaryDto.toDomain(): ShoppingListSummary? {
        if (ShoppingListStatus.fromRaw(status) == null) return null
        val created = parseInstant(createdAt) ?: return null
        return ShoppingListSummary(
            id = id,
            createdAt = created,
            doneAt = doneAt?.let(::parseInstant),
            itemCount = itemCount,
            boughtCount = boughtCount,
            notFoundCount = notFoundCount,
        )
    }

    private fun ShoppingItemDto.toDomain(): ShoppingItem? {
        val created = parseInstant(createdAt) ?: return null
        return ShoppingItem(
            id = id,
            text = text.trim(),
            productId = productId,
            productName = productName?.trim()?.takeIf { it.isNotEmpty() },
            qty = qty?.toDoubleOrNull()?.takeIf { it > 0.0 },
            status = ShoppingItemStatus.fromRaw(status),
            carriedFrom = carriedFrom,
            createdBy = createdBy?.trim().orEmpty(),
            createdAt = created,
        )
    }

    private fun parseInstant(raw: String): Instant? = runCatching { Instant.parse(raw) }.getOrNull()

    private fun AppError.isBarcodeNotFound(): Boolean =
        this is AppError.Validation && message.contains(BARCODE_NOT_FOUND, ignoreCase = true)

    private companion object {
        const val BARCODE_NOT_FOUND = "Barcode not found"
        const val MALFORMED = "shopping"
        const val SEARCH_FETCH_LIMIT = 20
    }
}
