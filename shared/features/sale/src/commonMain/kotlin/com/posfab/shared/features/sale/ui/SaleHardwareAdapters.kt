package com.posfab.shared.features.sale.ui

import com.posfab.shared.features.sale.domain.CheckoutResult
import com.posfab.shared.features.sale.domain.SaleLine

interface CardPaymentAdapter {
    suspend fun onSaleCompleted(result: CheckoutResult)
}

interface ReceiptPrinterAdapter {
    suspend fun printSaleReceipt(result: CheckoutResult, lines: List<SaleLine>)
}

object NoOpCardPaymentAdapter : CardPaymentAdapter {
    override suspend fun onSaleCompleted(result: CheckoutResult) = Unit
}

object NoOpReceiptPrinterAdapter : ReceiptPrinterAdapter {
    override suspend fun printSaleReceipt(result: CheckoutResult, lines: List<SaleLine>) = Unit
}
