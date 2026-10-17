package com.posfab.shared.features.sale

import com.posfab.shared.auth.domain.AuthTokens
import com.posfab.shared.auth.domain.AuthUser
import com.posfab.shared.auth.domain.UserSession
import com.posfab.shared.auth.refresh.AuthorizedApiExecutor
import com.posfab.shared.auth.repository.AuthRepository
import com.posfab.shared.auth.session.InMemorySessionStorage
import com.posfab.shared.auth.session.SessionManager
import com.posfab.shared.core.model.UserRole
import com.posfab.shared.core.result.AppError
import com.posfab.shared.core.result.AppResult
import com.posfab.shared.features.sale.domain.CheckoutResult
import com.posfab.shared.features.sale.domain.CustomerPaymentResult
import com.posfab.shared.features.sale.domain.SaleChannel
import com.posfab.shared.features.sale.domain.SaleCustomer
import com.posfab.shared.features.sale.domain.SaleDraft
import com.posfab.shared.features.sale.domain.SaleDraftKind
import com.posfab.shared.features.sale.domain.SaleKitchenKind
import com.posfab.shared.features.sale.domain.SaleLineInput
import com.posfab.shared.features.sale.domain.SaleMutationResult
import com.posfab.shared.features.sale.domain.SalePaymentType
import com.posfab.shared.features.sale.domain.SalePriceType
import com.posfab.shared.features.sale.domain.SaleProduct
import com.posfab.shared.features.sale.repository.SaleRepositoryImpl
import com.posfab.shared.network.catalog.BusinessConfigDto
import com.posfab.shared.network.catalog.BusinessUnitDto
import com.posfab.shared.network.catalog.CatalogApi
import com.posfab.shared.network.catalog.CreateProductRequestDto
import com.posfab.shared.network.catalog.ProductDetailDto
import com.posfab.shared.network.catalog.ProductListItemDto
import com.posfab.shared.network.catalog.ProductListResponseDto
import com.posfab.shared.network.catalog.ProductPricesDto
import com.posfab.shared.network.catalog.SetProductPricesRequestDto
import com.posfab.shared.network.catalog.UnitDto
import com.posfab.shared.network.catalog.UpdateProductRequestDto
import com.posfab.shared.network.customers.CreateCustomerRequestDto
import com.posfab.shared.network.customers.CustomerDto
import com.posfab.shared.network.customers.CustomerLedgerDto
import com.posfab.shared.network.customers.CustomerPaymentRequestDto
import com.posfab.shared.network.customers.CustomerPaymentResponseDto
import com.posfab.shared.network.customers.CustomersApi
import com.posfab.shared.network.customers.DebtorDto
import com.posfab.shared.network.customers.OpeningBalanceRequestDto
import com.posfab.shared.network.customers.OpeningBalanceResponseDto
import com.posfab.shared.network.pos.AddLineRequestDto
import com.posfab.shared.network.pos.BarcodeLookupProductDto
import com.posfab.shared.network.pos.BarcodeLookupResponseDto
import com.posfab.shared.network.pos.BarcodeLookupUomDto
import com.posfab.shared.network.pos.CancelDraftRequestDto
import com.posfab.shared.network.pos.CheckoutRequestDto
import com.posfab.shared.network.pos.CheckoutResponseDto
import com.posfab.shared.network.pos.DraftChannelRequestDto
import com.posfab.shared.network.pos.DraftCreateRequestDto
import com.posfab.shared.network.pos.DraftCreateResponseDto
import com.posfab.shared.network.pos.DraftDto
import com.posfab.shared.network.pos.DraftLineDto
import com.posfab.shared.network.pos.DraftListDto
import com.posfab.shared.network.pos.DraftTotalsDto
import com.posfab.shared.network.pos.PosApi
import com.posfab.shared.network.pos.ResolveLotsResponseDto
import com.posfab.shared.network.pos.UpdateLineRequestDto
import com.posfab.shared.network.pos.ValidateDraftResponseDto
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SaleRepositoryImplTest {
    private suspend fun repository(
        posApi: FakePosApi,
        catalogApi: FakeCatalogApi = FakeCatalogApi(),
        customersApi: FakeCustomersApi = FakeCustomersApi(),
    ): SaleRepositoryImpl {
        val manager = SessionManager(InMemorySessionStorage())
        manager.set(
            UserSession(
                tokens = AuthTokens("access", "refresh"),
                user = AuthUser("u1", "cajero", setOf(UserRole.CASHIER), emptySet()),
                terminalCode = "POS1",
            ),
        )
        val executor = AuthorizedApiExecutor(NoRefreshAuthRepository(manager), manager)
        return SaleRepositoryImpl(posApi, catalogApi, customersApi, TERMINAL_ID, BUSINESS_UNIT_ID, executor)
    }

    @Test
    fun open_drafts_maps_items_oldest_first() = runTest {
        val posApi = FakePosApi().apply {
            openDrafts = AppResult.Success(
                DraftListDto(listOf(draftDto(id = 9, cashTotal = "40.00"), draftDto(id = 7, cashTotal = "18.00"))),
            )
        }
        val result = repository(posApi).openDrafts()

        val drafts = assertIs<AppResult.Success<List<SaleDraft>>>(result).value
        assertEquals(listOf(7L, 9L), drafts.map { it.id })
        assertEquals(18.0, drafts.first().totals.cashTotal)
        assertEquals(0, posApi.createCalls.size)
    }

    @Test
    fun open_drafts_lists_sale_accounts_then_deliveries_each_by_id() = runTest {
        val posApi = FakePosApi().apply {
            openDrafts = AppResult.Success(DraftListDto(listOf(draftDto(id = 9), draftDto(id = 7))))
            deliveryDrafts = AppResult.Success(
                DraftListDto(
                    listOf(
                        draftDto(id = 5, type = "DELIVERY", cashTotal = "47.00"),
                        draftDto(id = 3, type = "DELIVERY", cashTotal = "20.00"),
                    ),
                ),
            )
        }
        val drafts = assertIs<AppResult.Success<List<SaleDraft>>>(repository(posApi).openDrafts()).value

        assertEquals(listOf(7L, 9L, 3L, 5L), drafts.map { it.id })
        assertEquals(
            listOf(SaleDraftKind.SALE, SaleDraftKind.SALE, SaleDraftKind.DELIVERY, SaleDraftKind.DELIVERY),
            drafts.map { it.kind },
        )
        assertEquals(listOf("SALE", "DELIVERY"), posApi.listedTypes)
    }

    @Test
    fun open_drafts_keeps_sale_accounts_when_the_delivery_list_fails() = runTest {
        val posApi = FakePosApi().apply {
            openDrafts = AppResult.Success(DraftListDto(listOf(draftDto(id = 7))))
            deliveryDrafts = AppResult.Failure(AppError.Forbidden)
        }
        val drafts = assertIs<AppResult.Success<List<SaleDraft>>>(repository(posApi).openDrafts()).value

        assertEquals(listOf(7L), drafts.map { it.id })
    }

    @Test
    fun open_delivery_drafts_maps_customer_address_and_pays_with() = runTest {
        val posApi = FakePosApi().apply {
            deliveryDrafts = AppResult.Success(
                DraftListDto(
                    listOf(
                        draftDto(
                            id = 4,
                            type = "DELIVERY",
                            customerName = "Lupe Hernández",
                            customerNickname = "Doña Lupe",
                            deliveryAddress = "Av. Juárez 5",
                            paysWith = "100.00",
                        ),
                        draftDto(id = 6, type = "DELIVERY", customerName = "Rosa Martínez", paysWith = "0.00"),
                    ),
                ),
            )
        }
        val drafts = assertIs<AppResult.Success<List<SaleDraft>>>(repository(posApi).openDeliveryDrafts()).value

        val lupe = drafts.first()
        assertEquals(SaleDraftKind.DELIVERY, lupe.kind)
        assertEquals("Lupe Hernández (Doña Lupe)", lupe.customerName)
        assertEquals("Lupe", lupe.customerShortName)
        assertEquals("Av. Juárez 5", lupe.deliveryAddress)
        assertEquals(100.0, lupe.paysWith)
        val rosa = drafts.last()
        assertEquals("Rosa Martínez", rosa.customerName)
        assertNull(rosa.paysWith)
        assertNull(rosa.deliveryAddress)
        assertEquals(listOf("DELIVERY"), posApi.listedTypes)
    }

    @Test
    fun unknown_draft_type_maps_to_sale() = runTest {
        val posApi = FakePosApi().apply {
            openDrafts = AppResult.Success(DraftListDto(listOf(draftDto(id = 7, type = "SOMETHING"))))
        }
        val draft = assertIs<AppResult.Success<List<SaleDraft>>>(repository(posApi).openDrafts()).value.single()

        assertEquals(SaleDraftKind.SALE, draft.kind)
        assertFalse(draft.isDelivery)
        assertNull(draft.customerName)
        assertNull(draft.paysWith)
    }

    @Test
    fun open_drafts_passes_failures_through() = runTest {
        val failures = listOf(AppError.Network("down"), AppError.Forbidden, AppError.Validation("Request rejected: 400"))
        failures.forEach { error ->
            val posApi = FakePosApi().apply { openDrafts = AppResult.Failure(error) }
            val result = repository(posApi).openDrafts()

            assertEquals(error, assertIs<AppResult.Failure>(result).error)
            assertEquals(0, posApi.createCalls.size)
        }
    }

    @Test
    fun conflict_on_add_refetches_the_same_draft() = runTest {
        val posApi = FakePosApi().apply { addLineResult = AppResult.Failure(AppError.Conflict) }
        val result = repository(posApi).addLine(7L, product(), SaleLineInput.Qty(1.0), expectedVersion = 1)

        val refetched = assertIs<SaleMutationResult.ConflictRefetched>(result)
        assertEquals(7L, refetched.draft.id)
        assertEquals(listOf(7L), posApi.getDraftCalls)
    }

    @Test
    fun conflict_on_a_cancelled_draft_reports_it_closed() = runTest {
        val posApi = FakePosApi().apply {
            addLineResult = AppResult.Failure(AppError.Conflict)
            draftStatus = "CANCELLED"
        }
        val result = repository(posApi).addLine(7L, product(), SaleLineInput.Qty(1.0), expectedVersion = 1)

        val closed = assertIs<SaleMutationResult.DraftClosed>(result)
        assertEquals(7L, closed.draft.id)
        assertEquals("CANCELLED", closed.draft.status)
        assertEquals(listOf(7L), posApi.getDraftCalls)
    }

    @Test
    fun new_draft_creates_then_reads_the_draft() = runTest {
        val posApi = FakePosApi()
        val result = repository(posApi).newDraft()

        assertEquals(99L, assertIs<AppResult.Success<SaleDraft>>(result).value.id)
        assertEquals(listOf("create", "get:99"), posApi.log)
    }

    @Test
    fun search_keeps_sellable_products_and_limits_after_filtering() = runTest {
        val items = listOf(
            listItem(id = "1", name = "Sin precio", cash = null),
            listItem(id = "2", name = "Kilo", priceType = "WEIGHT", cash = "90.00"),
            listItem(id = "3", name = "Kilo sin precio", priceType = "WEIGHT", cash = null),
            listItem(id = "4", name = "Abierto", priceType = "OPEN", cash = null),
            listItem(id = "5", name = "Coca", cash = "18.00"),
            listItem(id = "6", name = "Galletas", cash = "15.00"),
        )
        val catalogApi = FakeCatalogApi(items)
        val repository = repository(FakePosApi(), catalogApi)

        val limited = assertIs<AppResult.Success<List<SaleProduct>>>(repository.searchProducts("a", 3)).value
        assertEquals(listOf(2L, 4L, 5L), limited.map { it.id })
        assertEquals(50, catalogApi.lastLimit)
        assertEquals("a", catalogApi.lastQuery)

        val all = assertIs<AppResult.Success<List<SaleProduct>>>(repository.searchProducts("a", 8)).value
        assertEquals(listOf(2L, 4L, 5L, 6L), all.map { it.id })
        assertEquals(SalePriceType.WEIGHT, all[0].priceType)
        assertEquals(90.0, all[0].cashPrice)
        assertEquals(SalePriceType.OPEN, all[1].priceType)
        assertNull(all[1].cashPrice)
        assertTrue(all.all { it.isSellable })
    }

    @Test
    fun kitchen_products_include_weight_and_open_kitchen_items() = runTest {
        val items = listOf(
            listItem(id = "1", name = "Guisado kilo", priceType = "WEIGHT", cash = "200.00", kitchenKind = "FONDA"),
            listItem(id = "2", name = "Agua fresca", priceType = "OPEN", cash = null, kitchenKind = "FONDA"),
            listItem(id = "3", name = "Torta cubana", cash = "60.00", kitchenKind = "TORTERIA"),
            listItem(id = "4", name = "Barbacoa", priceType = "WEIGHT", cash = null, kitchenKind = "FONDA"),
            listItem(id = "5", name = "Coca", cash = "18.00"),
        )
        val result = repository(FakePosApi(), FakeCatalogApi(items)).kitchenProducts()

        val products = assertIs<AppResult.Success<List<SaleProduct>>>(result).value
        assertEquals(listOf("Agua fresca", "Guisado kilo", "Torta cubana"), products.map { it.name })
        assertEquals(
            listOf(SalePriceType.OPEN, SalePriceType.WEIGHT, SalePriceType.PIECE),
            products.map { it.priceType },
        )
    }

    @Test
    fun kitchen_products_keep_only_kitchen_items_sorted_by_kind_and_name() = runTest {
        val items = listOf(
            listItem(id = "1", name = "Torta cubana", cash = "60.00", kitchenKind = "TORTERIA"),
            listItem(id = "2", name = "Coca", cash = "18.00"),
            listItem(id = "3", name = "Sopa", cash = "40.00", kitchenKind = "FONDA"),
            listItem(id = "4", name = "Comida", cash = "63.00", kitchenKind = "FONDA"),
        )
        val catalogApi = FakeCatalogApi(items)
        val result = repository(FakePosApi(), catalogApi).kitchenProducts()

        val products = assertIs<AppResult.Success<List<SaleProduct>>>(result).value
        assertEquals(listOf("Comida", "Sopa", "Torta cubana"), products.map { it.name })
        assertEquals(
            listOf(SaleKitchenKind.FONDA, SaleKitchenKind.FONDA, SaleKitchenKind.TORTERIA),
            products.map { it.kitchenKind },
        )
        assertEquals(200, catalogApi.lastLimit)
        assertNull(catalogApi.lastQuery)
    }

    @Test
    fun barcode_not_found_maps_to_null() = runTest {
        val posApi = FakePosApi().apply { barcode = AppResult.Failure(AppError.Validation("Barcode not found")) }
        val result = repository(posApi).findProductByBarcode("7501")

        assertNull(assertIs<AppResult.Success<SaleProduct?>>(result).value)
    }

    @Test
    fun barcode_other_failures_pass_through() = runTest {
        val posApi = FakePosApi().apply { barcode = AppResult.Failure(AppError.Network("down")) }
        val result = repository(posApi).findProductByBarcode("7501")

        assertIs<AppResult.Failure>(result)
    }

    @Test
    fun inactive_barcode_product_maps_to_null() = runTest {
        val posApi = FakePosApi().apply { barcode = AppResult.Success(lookup(active = false)) }
        val result = repository(posApi).findProductByBarcode("7501")

        assertNull(assertIs<AppResult.Success<SaleProduct?>>(result).value)
    }

    @Test
    fun barcode_uses_the_matched_uom_unit_and_cash_price() = runTest {
        val posApi = FakePosApi().apply { barcode = AppResult.Success(lookup(matchedUnitId = 4L)) }
        val product = assertIs<AppResult.Success<SaleProduct?>>(repository(posApi).findProductByBarcode("7501")).value!!

        assertEquals(4L, product.unitId)
        assertEquals(21.0, product.cashPrice)
        assertEquals(22.1, product.listPrice)
        assertNull(product.listOnsitePrice)
        assertEquals(SaleKitchenKind.FONDA, product.kitchenKind)
        assertTrue(product.isSellable)

        val noUom = FakePosApi().apply { barcode = AppResult.Success(lookup(matchedUnitId = null)) }
        val base = assertIs<AppResult.Success<SaleProduct?>>(repository(noUom).findProductByBarcode("7501")).value!!
        assertEquals(1L, base.unitId)
    }

    @Test
    fun checkout_sends_payment_type_per_type() = runTest {
        SalePaymentType.entries.forEach { type ->
            val posApi = FakePosApi()
            val result = repository(posApi).checkout(
                draftId = 7L,
                paymentType = type,
                paidAmount = 100.0,
                idempotencyKey = "key-${type.name}",
            )

            val request = posApi.checkoutRequests.single()
            assertEquals(if (type == SalePaymentType.CREDIT) "ON_CREDIT" else "PAID", request.paymentStatus)
            assertNull(request.customerId)
            assertEquals(type.name, request.paymentType)
            assertEquals("100.00", request.paidAmount)
            assertEquals("key-${type.name}", request.idempotencyKey)
            val charged = assertIs<AppResult.Success<CheckoutResult>>(result).value
            assertEquals(type, charged.paymentType)
            assertEquals(63.0, charged.totalCharged)
            assertEquals(100.0, charged.paidAmount)
            assertEquals("F-1", charged.folio)
            if (type == SalePaymentType.CASH) {
                assertEquals(37.0, charged.change)
            } else {
                assertNull(charged.change)
            }
        }
    }

    @Test
    fun checkout_on_credit_sends_on_credit_status_and_customer_id() = runTest {
        val posApi = FakePosApi()
        val result = repository(posApi).checkout(
            draftId = 7L,
            paymentType = SalePaymentType.CREDIT,
            paidAmount = 66.15,
            idempotencyKey = "key-credit",
            customerId = 10L,
        )

        val request = posApi.checkoutRequests.single()
        assertEquals("ON_CREDIT", request.paymentStatus)
        assertEquals("CREDIT", request.paymentType)
        assertEquals(10L, request.customerId)
        assertEquals("66.15", request.paidAmount)
        assertEquals("key-credit", request.idempotencyKey)
        val charged = assertIs<AppResult.Success<CheckoutResult>>(result).value
        assertEquals(SalePaymentType.CREDIT, charged.paymentType)
        assertNull(charged.change)
    }

    @Test
    fun checkout_of_a_delivery_is_cash_without_customer_id() = runTest {
        val posApi = FakePosApi().apply {
            deliveryDrafts = AppResult.Success(
                DraftListDto(listOf(draftDto(id = 50, type = "DELIVERY", cashTotal = "47.00", customerName = "Lupe Hernández"))),
            )
        }
        val repository = repository(posApi)
        val delivery = assertIs<AppResult.Success<List<SaleDraft>>>(repository.openDeliveryDrafts()).value.single()

        repository.checkout(delivery.id, SalePaymentType.CASH, 100.0, "key-delivery")

        val request = posApi.checkoutRequests.single()
        assertEquals("PAID", request.paymentStatus)
        assertEquals("CASH", request.paymentType)
        assertEquals("100.00", request.paidAmount)
        assertNull(request.customerId)
    }

    @Test
    fun checkout_ignores_the_customer_outside_credit() = runTest {
        val posApi = FakePosApi()
        repository(posApi).checkout(7L, SalePaymentType.CASH, 100.0, "key-cash", customerId = 10L)

        val request = posApi.checkoutRequests.single()
        assertEquals("PAID", request.paymentStatus)
        assertNull(request.customerId)
    }

    @Test
    fun record_payment_sends_idempotency_key_business_unit_and_terminal() = runTest {
        val customersApi = FakeCustomersApi()
        val result = repository(FakePosApi(), customersApi = customersApi)
            .recordCustomerPayment(customerId = 10L, amount = 100.5, idempotencyKey = "abono-10")

        val call = customersApi.paymentCalls.single()
        assertEquals(10L, call.customerId)
        assertEquals("abono-10", call.idempotencyKey)
        assertEquals(CustomerPaymentRequestDto(BUSINESS_UNIT_ID, TERMINAL_ID, "100.50"), call.request)
        val paid = assertIs<AppResult.Success<CustomerPaymentResult>>(result).value
        assertEquals(CustomerPaymentResult(customerId = 10L, amount = 100.5, balance = 249.5), paid)
    }

    @Test
    fun search_customers_trims_the_query_and_passes_the_limit() = runTest {
        val customersApi = FakeCustomersApi()
        val repository = repository(FakePosApi(), customersApi = customersApi)

        val result = repository.searchCustomers("  Juan  ", 6)
        repository.searchCustomers("   ", 6)

        assertEquals(listOf<String?>("Juan", null), customersApi.searchQueries)
        assertEquals(listOf(6, 6), customersApi.searchLimits)
        val customers = assertIs<AppResult.Success<List<SaleCustomer>>>(result).value
        assertEquals(
            listOf(SaleCustomer(10L, "30000011", "Juan Pérez", "el Wero", "5551234", 350.0)),
            customers,
        )
    }

    @Test
    fun create_customer_maps_blank_phone_to_null() = runTest {
        val customersApi = FakeCustomersApi()
        val repository = repository(FakePosApi(), customersApi = customersApi)

        val created = repository.createCustomer("  Pedro  ", null, "   ")
        repository.createCustomer("Ana", null, " 555 1234 ")

        assertEquals(
            listOf(
                CreateCustomerRequestDto(name = "Pedro", phone = null),
                CreateCustomerRequestDto(name = "Ana", phone = "555 1234"),
            ),
            customersApi.createRequests,
        )
        val customer = assertIs<AppResult.Success<SaleCustomer>>(created).value
        assertEquals("Pedro", customer.name)
        assertNull(customer.phone)
        assertEquals(0.0, customer.balance)
    }

    @Test
    fun create_customer_sends_trimmed_nickname_or_null() = runTest {
        val customersApi = FakeCustomersApi()
        val repository = repository(FakePosApi(), customersApi = customersApi)

        val created = repository.createCustomer("Juan López", "  el Wero  ", null)
        repository.createCustomer("Ana", "   ", null)
        repository.createCustomer("Luis", null, null)

        assertEquals(listOf("el Wero", null, null), customersApi.createRequests.map { it.nickname })
        val customer = assertIs<AppResult.Success<SaleCustomer>>(created).value
        assertEquals("el Wero", customer.nickname)
        assertEquals("Juan López (el Wero)", customer.displayName)
    }

    @Test
    fun customers_map_code_and_nickname() = runTest {
        val customersApi = FakeCustomersApi()
        val repository = repository(FakePosApi(), customersApi = customersApi)

        val juan = assertIs<AppResult.Success<SaleCustomer>>(repository.customer(10L)).value
        val pedro = assertIs<AppResult.Success<SaleCustomer>>(repository.createCustomer("Pedro", " ", null)).value

        assertEquals("30000011", juan.code)
        assertEquals("el Wero", juan.nickname)
        assertEquals("Juan Pérez (el Wero)", juan.displayName)
        assertNull(pedro.code)
        assertNull(pedro.nickname)
        assertEquals("Pedro", pedro.displayName)
    }

    @Test
    fun set_channel_posts_channel_and_version_and_maps_the_draft() = runTest {
        val posApi = FakePosApi()
        val result = repository(posApi).setChannel(7L, SaleChannel.ONSITE, expectedVersion = 1)

        assertEquals(DraftChannelRequestDto("ONSITE", 1), posApi.channelRequests.single())
        val success = assertIs<SaleMutationResult.Success>(result)
        assertEquals(7L, success.draft.id)
        assertEquals(SaleChannel.ONSITE, success.draft.channel)
        assertEquals(2, success.draft.version)
        assertEquals(emptyList(), posApi.getDraftCalls)
    }

    @Test
    fun set_channel_conflict_refetches_the_same_draft() = runTest {
        val posApi = FakePosApi().apply { setChannelResult = AppResult.Failure(AppError.Conflict) }
        val result = repository(posApi).setChannel(7L, SaleChannel.ONSITE, expectedVersion = 1)

        val refetched = assertIs<SaleMutationResult.ConflictRefetched>(result)
        assertEquals(7L, refetched.draft.id)
        assertEquals(SaleChannel.TAKEAWAY, refetched.draft.channel)
        assertEquals(listOf(7L), posApi.getDraftCalls)
    }

    @Test
    fun draft_lines_map_list_and_cash_prices() = runTest {
        val line = DraftLineDto(
            id = 11L,
            lineNo = 1,
            productId = 5L,
            unitId = 2L,
            qty = "2.000000",
            unitPrice = "22.00",
            cashUnitPrice = "21.00",
            productName = "Cigarro suelto",
            unitCode = "PZA",
            lineTotal = "44.00",
        )
        val posApi = FakePosApi().apply {
            openDrafts = AppResult.Success(
                DraftListDto(
                    listOf(draftDto(id = 7, cashTotal = "42.00", listTotal = "44.00", cashDiscount = "2.00", lines = listOf(line))),
                ),
            )
        }
        val draft = assertIs<AppResult.Success<List<SaleDraft>>>(repository(posApi).openDrafts()).value.single()

        val mapped = draft.lines.single()
        assertEquals(22.0, mapped.unitPrice)
        assertEquals(21.0, mapped.cashUnitPrice)
        assertEquals(44.0, mapped.lineTotal)
        assertEquals(2.0, mapped.qty)
        assertEquals(44.0, draft.totals.listTotal)
        assertEquals(42.0, draft.totals.cashTotal)
        assertEquals(2.0, draft.totals.cashDiscount)
        assertEquals(SaleChannel.TAKEAWAY, draft.channel)
    }

    @Test
    fun draft_line_without_cash_price_falls_back_to_the_list_price() = runTest {
        val line = DraftLineDto(
            id = 11L,
            lineNo = 1,
            productId = 5L,
            unitId = 2L,
            qty = "3",
            unitPrice = "10.00",
            productName = "Abierto",
            unitCode = "PZA",
            lineTotal = "",
        )
        val posApi = FakePosApi().apply {
            openDrafts = AppResult.Success(DraftListDto(listOf(draftDto(id = 7, channel = "onsite", lines = listOf(line)))))
        }
        val draft = assertIs<AppResult.Success<List<SaleDraft>>>(repository(posApi).openDrafts()).value.single()

        val mapped = draft.lines.single()
        assertEquals(10.0, mapped.unitPrice)
        assertEquals(10.0, mapped.cashUnitPrice)
        assertEquals(30.0, mapped.lineTotal)
        assertEquals(SaleChannel.ONSITE, draft.channel)
    }

    @Test
    fun search_maps_list_prices() = runTest {
        val items = listOf(
            listItem(id = "3", name = "Coca", cash = "18.00", list = "18.90", listOnsite = "20.00"),
            listItem(id = "5", name = "Chicle", cash = "2.00"),
        )
        val result = repository(FakePosApi(), FakeCatalogApi(items)).searchProducts("c", 8)

        val products = assertIs<AppResult.Success<List<SaleProduct>>>(result).value
        assertEquals(18.9, products[0].listPrice)
        assertEquals(20.0, products[0].listOnsitePrice)
        assertEquals(2.0, products[1].listPrice)
        assertNull(products[1].listOnsitePrice)
    }

    @Test
    fun add_line_with_kilos_sends_three_decimal_qty() = runTest {
        val posApi = FakePosApi()
        val result = repository(posApi).addLine(7L, weightProduct(), SaleLineInput.Qty(0.35), expectedVersion = 2)

        assertIs<SaleMutationResult.Success>(result)
        val request = posApi.addLineRequests.single()
        assertEquals("0.350", request.qty)
        assertNull(request.amount)
        assertNull(request.unitPrice)
        assertEquals(13L, request.productId)
        assertEquals(3L, request.unitId)
        assertEquals(2, request.expectedVersion)
    }

    @Test
    fun add_line_with_pieces_sends_whole_qty() = runTest {
        val posApi = FakePosApi()
        repository(posApi).addLine(7L, product(), SaleLineInput.Qty(2.0), expectedVersion = 1)

        val request = posApi.addLineRequests.single()
        assertEquals("2", request.qty)
        assertNull(request.amount)
        assertNull(request.unitPrice)
    }

    @Test
    fun add_line_with_pesos_sends_amount_and_no_qty() = runTest {
        val posApi = FakePosApi()
        repository(posApi).addLine(7L, weightProduct(), SaleLineInput.Amount(20.0), expectedVersion = 1)

        val request = posApi.addLineRequests.single()
        assertEquals("20.00", request.amount)
        assertNull(request.qty)
        assertNull(request.unitPrice)
    }

    @Test
    fun add_open_line_sends_qty_1_and_unit_price() = runTest {
        val posApi = FakePosApi()
        repository(posApi).addLine(7L, openProduct(), SaleLineInput.OpenPrice(3.5), expectedVersion = 1)

        val request = posApi.addLineRequests.single()
        assertEquals("1", request.qty)
        assertEquals("3.50", request.unitPrice)
        assertNull(request.amount)
    }

    @Test
    fun update_line_maps_the_three_inputs() = runTest {
        val posApi = FakePosApi()
        val repository = repository(posApi)

        val kilos = repository.updateLine(7L, 11L, SaleLineInput.Qty(1.25), expectedVersion = 3)
        repository.updateLine(7L, 11L, SaleLineInput.Qty(4.0), expectedVersion = 4)
        repository.updateLine(7L, 11L, SaleLineInput.Amount(12.5), expectedVersion = 5)
        repository.updateLine(7L, 12L, SaleLineInput.OpenPrice(8.0), expectedVersion = 6)

        assertIs<SaleMutationResult.Success>(kilos)
        assertEquals(
            listOf(
                UpdateLineRequestDto(qty = "1.250", expectedVersion = 3),
                UpdateLineRequestDto(qty = "4", expectedVersion = 4),
                UpdateLineRequestDto(amount = "12.50", expectedVersion = 5),
                UpdateLineRequestDto(qty = "1", unitPrice = "8.00", expectedVersion = 6),
            ),
            posApi.updateLineRequests.map { it.second },
        )
        assertEquals(listOf(11L, 11L, 11L, 12L), posApi.updateLineRequests.map { it.first })
    }

    @Test
    fun draft_lines_map_price_type_with_piece_default() = runTest {
        val weight = DraftLineDto(
            id = 11L,
            lineNo = 1,
            productId = 13L,
            unitId = 3L,
            qty = "0.350000",
            unitPrice = "126.00",
            cashUnitPrice = "120.00",
            productName = "Jamón",
            unitCode = "KG",
            lineTotal = "44.10",
            priceType = "WEIGHT",
        )
        val open = weight.copy(id = 12L, lineNo = 2, priceType = "OPEN", qty = "1", unitPrice = "5.00", cashUnitPrice = "5.00")
        val piece = weight.copy(id = 13L, lineNo = 3, priceType = "PIECE")
        val legacy = DraftLineDto(
            id = 14L,
            lineNo = 4,
            productId = 5L,
            unitId = 1L,
            qty = "1",
            unitPrice = "22.00",
            productName = "Cigarro suelto",
            unitCode = "PZA",
            lineTotal = "22.00",
        )
        val posApi = FakePosApi().apply {
            openDrafts = AppResult.Success(DraftListDto(listOf(draftDto(id = 7, lines = listOf(weight, open, piece, legacy)))))
        }
        val draft = assertIs<AppResult.Success<List<SaleDraft>>>(repository(posApi).openDrafts()).value.single()

        assertEquals(
            listOf(SalePriceType.WEIGHT, SalePriceType.OPEN, SalePriceType.PIECE, SalePriceType.PIECE),
            draft.lines.map { it.priceType },
        )
        assertEquals(0.35, draft.lines.first().qty)
    }

    @Test
    fun varios_product_is_the_active_open_product_named_varios() = runTest {
        val items = listOf(
            listItem(id = "1", name = "Varios chicos", priceType = "OPEN", cash = null),
            listItem(id = "2", name = "Varios", priceType = "PIECE", cash = "10.00"),
            listItem(id = "3", name = "varios", priceType = "OPEN", cash = null),
        )
        val catalogApi = FakeCatalogApi(items)
        val result = repository(FakePosApi(), catalogApi).variosProduct()

        val varios = assertIs<AppResult.Success<SaleProduct?>>(result).value
        assertEquals(3L, varios?.id)
        assertEquals(SalePriceType.OPEN, varios?.priceType)
        assertEquals("Varios", catalogApi.lastQuery)
        assertEquals(true, catalogApi.lastActive)

        val none = FakeCatalogApi(items.take(2))
        assertNull(assertIs<AppResult.Success<SaleProduct?>>(repository(FakePosApi(), none).variosProduct()).value)
    }

    @Test
    fun varios_product_passes_failures_through() = runTest {
        val catalogApi = FakeCatalogApi(failure = AppError.Network("down"))
        val result = repository(FakePosApi(), catalogApi).variosProduct()

        assertEquals(AppError.Network("down"), assertIs<AppResult.Failure>(result).error)
    }

    @Test
    fun product_prices_per_channel_fall_back_to_takeaway() {
        val product = weightProduct()
        assertEquals(120.0, product.cashPriceFor(SaleChannel.TAKEAWAY))
        assertEquals(126.0, product.listPriceFor(SaleChannel.TAKEAWAY))
        assertEquals(120.0, product.cashPriceFor(SaleChannel.ONSITE))
        assertEquals(126.0, product.listPriceFor(SaleChannel.ONSITE))

        val onsite = product.copy(cashOnsitePrice = 130.0, listOnsitePrice = 136.5)
        assertEquals(130.0, onsite.cashPriceFor(SaleChannel.ONSITE))
        assertEquals(136.5, onsite.listPriceFor(SaleChannel.ONSITE))
        assertEquals(120.0, onsite.cashPriceFor(SaleChannel.TAKEAWAY))
    }

    @Test
    fun sellable_means_open_or_priced() {
        assertTrue(openProduct().isSellable)
        assertTrue(weightProduct().isSellable)
        assertFalse(weightProduct().copy(cashPrice = null).isSellable)
        assertFalse(product().copy(cashPrice = null).isSellable)
    }

    @Test
    fun search_and_barcode_map_the_onsite_cash_price() = runTest {
        val items = listOf(listItem(id = "3", name = "Coca", cash = "18.00", listOnsite = "20.00", cashOnsite = "19.00"))
        val searched = assertIs<AppResult.Success<List<SaleProduct>>>(
            repository(FakePosApi(), FakeCatalogApi(items)).searchProducts("c", 8),
        ).value.single()
        assertEquals(19.0, searched.cashOnsitePrice)

        val posApi = FakePosApi().apply { barcode = AppResult.Success(lookup(cashOnsite = "20.00")) }
        val scanned = assertIs<AppResult.Success<SaleProduct?>>(repository(posApi).findProductByBarcode("7501")).value
        assertEquals(20.0, scanned?.cashOnsitePrice)
    }

    private fun weightProduct() = SaleProduct(
        id = 13L,
        name = "Jamón",
        barcode = null,
        unitId = 3L,
        cashPrice = 120.0,
        listPrice = 126.0,
        listOnsitePrice = null,
        priceType = SalePriceType.WEIGHT,
    )

    private fun openProduct() = SaleProduct(
        id = 3L,
        name = "Varios",
        barcode = null,
        unitId = 1L,
        cashPrice = null,
        listPrice = null,
        listOnsitePrice = null,
        priceType = SalePriceType.OPEN,
    )

    private fun product() = SaleProduct(
        id = 1L,
        name = "Coca",
        barcode = null,
        unitId = 1L,
        cashPrice = 18.0,
        listPrice = 18.9,
        listOnsitePrice = null,
    )

    private fun lookup(
        active: Boolean = true,
        matchedUnitId: Long? = null,
        cashOnsite: String? = null,
    ) = BarcodeLookupResponseDto(
        product = BarcodeLookupProductDto(
            id = 10L,
            sku = "SKU",
            name = "Cigarro",
            baseUnitId = 1L,
            requiresLotTracking = false,
            active = active,
            priceType = "PIECE",
            kitchenKind = "FONDA",
            prices = ProductPricesDto(cashTakeaway = "21.00", listTakeaway = "22.10", cashOnsite = cashOnsite),
        ),
        matchedOn = if (matchedUnitId == null) "PRODUCT" else "UOM",
        matchedUom = matchedUnitId?.let { BarcodeLookupUomDto(id = 1L, unitId = it, factorToBase = "1", barcode = "7501") },
    )

    private fun listItem(
        id: String,
        name: String,
        cash: String?,
        priceType: String = "PIECE",
        kitchenKind: String? = null,
        list: String? = cash,
        listOnsite: String? = null,
        cashOnsite: String? = null,
    ) = ProductListItemDto(
        id = id,
        name = name,
        baseUnit = "PZA",
        baseUnitId = 1L,
        lotTracked = false,
        active = true,
        priceType = priceType,
        kitchenKind = kitchenKind,
        prices = cash?.let {
            ProductPricesDto(cashTakeaway = it, listTakeaway = list ?: it, cashOnsite = cashOnsite, listOnsite = listOnsite)
        },
    )

    private companion object {
        const val TERMINAL_ID = 3L
        const val BUSINESS_UNIT_ID = 1L
    }
}

internal fun draftDto(
    id: Long,
    cashTotal: String = "0",
    lines: List<DraftLineDto> = emptyList(),
    status: String = "OPEN",
    listTotal: String = cashTotal,
    cashDiscount: String = "0",
    channel: String = "TAKEAWAY",
    version: Int = 1,
    type: String = "SALE",
    customerName: String? = null,
    customerNickname: String? = null,
    deliveryAddress: String? = null,
    paysWith: String? = null,
) = DraftDto(
    id = id,
    type = type,
    status = status,
    terminalId = 3L,
    version = version,
    lines = lines,
    totals = DraftTotalsDto(
        amount = listTotal,
        subtotal = listTotal,
        tax = "0",
        total = listTotal,
        cashDiscount = cashDiscount,
        cashTotal = cashTotal,
    ),
    channel = channel,
    customerName = customerName,
    customerNickname = customerNickname,
    deliveryAddress = deliveryAddress,
    paysWith = paysWith,
)

private class NoRefreshAuthRepository(private val manager: SessionManager) : AuthRepository {
    override suspend fun login(username: String, password: String, terminalCode: String): AppResult<UserSession> =
        AppResult.Failure(AppError.Unknown("unused"))

    override suspend fun refresh(refreshTokenOverride: String?): AppResult<UserSession> =
        AppResult.Failure(AppError.Unauthorized)

    override suspend fun restoreSession(): AppResult<UserSession?> = AppResult.Success(manager.current())

    override suspend fun logout(): AppResult<Unit> = AppResult.Success(Unit)
}

private class FakePosApi : PosApi {
    var openDrafts: AppResult<DraftListDto> = AppResult.Success(DraftListDto(emptyList()))
    var deliveryDrafts: AppResult<DraftListDto> = AppResult.Success(DraftListDto(emptyList()))
    val listedTypes = mutableListOf<String>()
    var draftStatus: String = "OPEN"
    var addLineResult: AppResult<DraftDto>? = null
    var barcode: AppResult<BarcodeLookupResponseDto> = AppResult.Failure(AppError.Validation("Barcode not found"))
    val createCalls = mutableListOf<DraftCreateRequestDto>()
    val getDraftCalls = mutableListOf<Long>()
    val checkoutRequests = mutableListOf<CheckoutRequestDto>()
    val channelRequests = mutableListOf<DraftChannelRequestDto>()
    var setChannelResult: AppResult<DraftDto>? = null
    val addLineRequests = mutableListOf<AddLineRequestDto>()
    val updateLineRequests = mutableListOf<Pair<Long, UpdateLineRequestDto>>()
    val log = mutableListOf<String>()

    override suspend fun currentCashSession(accessToken: String, terminalId: Long): AppResult<Boolean> =
        AppResult.Success(true)

    override suspend fun listOpenDrafts(accessToken: String, type: String): AppResult<DraftListDto> {
        listedTypes += type
        return if (type == "DELIVERY") deliveryDrafts else openDrafts
    }

    override suspend fun createDraft(accessToken: String, request: DraftCreateRequestDto): AppResult<DraftCreateResponseDto> {
        createCalls += request
        log += "create"
        return AppResult.Success(DraftCreateResponseDto(id = 99L, version = 1))
    }

    override suspend fun getDraft(accessToken: String, draftId: Long): AppResult<DraftDto> {
        getDraftCalls += draftId
        log += "get:$draftId"
        return AppResult.Success(draftDto(id = draftId, status = draftStatus))
    }

    override suspend fun addDraftLine(accessToken: String, draftId: Long, request: AddLineRequestDto): AppResult<DraftDto> {
        addLineRequests += request
        return addLineResult ?: AppResult.Success(draftDto(id = draftId, version = request.expectedVersion + 1))
    }

    override suspend fun updateDraftLine(
        accessToken: String,
        draftId: Long,
        lineId: Long,
        request: UpdateLineRequestDto,
    ): AppResult<DraftDto> {
        updateLineRequests += lineId to request
        return AppResult.Success(draftDto(id = draftId, version = request.expectedVersion + 1))
    }

    override suspend fun removeDraftLine(
        accessToken: String,
        draftId: Long,
        lineId: Long,
        expectedVersion: Int,
    ): AppResult<DraftDto> = error("unused")

    override suspend fun validateDraft(accessToken: String, draftId: Long): AppResult<ValidateDraftResponseDto> =
        error("unused")

    override suspend fun resolveLots(accessToken: String, draftId: Long): AppResult<ResolveLotsResponseDto> =
        error("unused")

    override suspend fun checkoutDraft(
        accessToken: String,
        draftId: Long,
        request: CheckoutRequestDto,
    ): AppResult<CheckoutResponseDto> {
        checkoutRequests += request
        return AppResult.Success(CheckoutResponseDto(id = 1L, folio = "F-1", totalAmount = "63.00"))
    }

    override suspend fun setDraftChannel(
        accessToken: String,
        draftId: Long,
        request: DraftChannelRequestDto,
    ): AppResult<DraftDto> {
        channelRequests += request
        return setChannelResult
            ?: AppResult.Success(draftDto(id = draftId, channel = request.channel, version = request.expectedVersion + 1))
    }

    override suspend fun cancelDraft(accessToken: String, draftId: Long, request: CancelDraftRequestDto): AppResult<Unit> =
        error("unused")

    override suspend fun findByBarcode(accessToken: String, barcode: String): AppResult<BarcodeLookupResponseDto> =
        this.barcode
}

private class FakeCatalogApi(
    private val items: List<ProductListItemDto> = emptyList(),
    private val failure: AppError? = null,
) : CatalogApi {
    var lastQuery: String? = null
    var lastLimit: Int? = null
    var lastActive: Boolean? = null

    override suspend fun listProducts(
        accessToken: String,
        query: String?,
        limit: Int,
        offset: Int,
        active: Boolean?,
    ): AppResult<ProductListResponseDto> {
        lastQuery = query
        lastLimit = limit
        lastActive = active
        if (failure != null) return AppResult.Failure(failure)
        return AppResult.Success(ProductListResponseDto(items = items.take(limit), total = items.size, limit = limit, offset = offset))
    }

    override suspend fun createProduct(accessToken: String, request: CreateProductRequestDto): AppResult<ProductDetailDto> =
        error("unused")

    override suspend fun getProduct(accessToken: String, productId: String): AppResult<ProductDetailDto> = error("unused")

    override suspend fun updateProduct(
        accessToken: String,
        productId: String,
        request: UpdateProductRequestDto,
    ): AppResult<ProductDetailDto> = error("unused")

    override suspend fun setPrices(
        accessToken: String,
        productId: String,
        request: SetProductPricesRequestDto,
    ): AppResult<ProductDetailDto> = error("unused")

    override suspend fun assignInternalCode(accessToken: String, productId: String): AppResult<ProductDetailDto> =
        error("unused")

    override suspend fun getBusinessConfig(accessToken: String): AppResult<BusinessConfigDto> = error("unused")

    override suspend fun listUnits(accessToken: String): AppResult<List<UnitDto>> = error("unused")

    override suspend fun listBusinessUnits(accessToken: String): AppResult<List<BusinessUnitDto>> = error("unused")

    override suspend fun findByBarcode(accessToken: String, barcode: String): AppResult<BarcodeLookupResponseDto> =
        error("unused")
}

private data class PaymentCall(
    val customerId: Long,
    val idempotencyKey: String,
    val request: CustomerPaymentRequestDto,
)

private class FakeCustomersApi : CustomersApi {
    val searchQueries = mutableListOf<String?>()
    val searchLimits = mutableListOf<Int>()
    val createRequests = mutableListOf<CreateCustomerRequestDto>()
    val paymentCalls = mutableListOf<PaymentCall>()

    private val juan = CustomerDto(
        id = 10L,
        code = "30000011",
        name = "Juan Pérez",
        nickname = "el Wero",
        phone = "5551234",
        balance = "350.00",
    )
    private val inactive = CustomerDto(id = 12L, name = "Juana Baja", active = false, balance = "20.00")

    override suspend fun search(accessToken: String, query: String?, limit: Int): AppResult<List<CustomerDto>> {
        searchQueries += query
        searchLimits += limit
        return AppResult.Success(listOf(juan, inactive))
    }

    override suspend fun get(accessToken: String, customerId: Long): AppResult<CustomerDto> =
        AppResult.Success(juan)

    override suspend fun create(accessToken: String, request: CreateCustomerRequestDto): AppResult<CustomerDto> {
        createRequests += request
        return AppResult.Success(
            CustomerDto(
                id = 20L + createRequests.size,
                name = request.name,
                nickname = request.nickname,
                phone = request.phone,
                balance = "0",
            ),
        )
    }

    override suspend fun recordPayment(
        accessToken: String,
        customerId: Long,
        idempotencyKey: String,
        request: CustomerPaymentRequestDto,
    ): AppResult<CustomerPaymentResponseDto> {
        paymentCalls += PaymentCall(customerId, idempotencyKey, request)
        return AppResult.Success(
            CustomerPaymentResponseDto(id = 1L, customerId = customerId, amount = request.amount, balance = "249.50"),
        )
    }

    override suspend fun loadOpeningBalance(
        accessToken: String,
        customerId: Long,
        idempotencyKey: String,
        request: OpeningBalanceRequestDto,
    ): AppResult<OpeningBalanceResponseDto> = AppResult.Failure(AppError.Unknown("unused"))

    override suspend fun updateAddress(accessToken: String, customerId: Long, address: String): AppResult<CustomerDto> =
        AppResult.Failure(AppError.Unknown("unused"))

    override suspend fun debtors(accessToken: String): AppResult<List<DebtorDto>> = AppResult.Success(emptyList())

    override suspend fun ledger(accessToken: String, customerId: Long, limit: Int): AppResult<CustomerLedgerDto> =
        AppResult.Success(CustomerLedgerDto(customer = juan))
}
