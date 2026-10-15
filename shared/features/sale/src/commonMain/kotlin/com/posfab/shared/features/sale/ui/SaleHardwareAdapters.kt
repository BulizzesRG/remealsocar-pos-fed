package com.posfab.shared.features.sale.ui

import com.posfab.shared.features.sale.domain.CashCheckoutResult
import com.posfab.shared.features.sale.domain.SaleLine

interface CardPaymentAdapter {
    suspend fun onSaleCompleted(result: CashCheckoutResult)
}

interface ReceiptPrinterAdapter {
    suspend fun printSaleReceipt(result: CashCheckoutResult, lines: List<SaleLine>)
}

object NoOpCardPaymentAdapter : CardPaymentAdapter {
    override suspend fun onSaleCompleted(result: CashCheckoutResult) = Unit
}

object NoOpReceiptPrinterAdapter : ReceiptPrinterAdapter {
    override suspend fun printSaleReceipt(result: CashCheckoutResult, lines: List<SaleLine>) = Unit
}
