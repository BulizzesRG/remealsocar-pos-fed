package com.posfab.shared.features.sale.ui

import com.posfab.shared.features.sale.domain.SalePaymentType

enum class SaleFunctionKey { F1, F2, F3, F4, F5 }

object SaleShortcuts {
    const val CHANNEL = "F1"
    const val CHARGE = "F2"
    const val ABONO = "F3"
    const val NEW_ACCOUNT = "F4"
    const val REFRESH_DELIVERIES = "F5"

    fun forPaymentType(type: SalePaymentType): String = when (type) {
        SalePaymentType.CASH -> "F1"
        SalePaymentType.TRANSFER -> "F2"
        SalePaymentType.CARD -> "F3"
        SalePaymentType.CREDIT -> "F4"
    }

    fun paymentTypeFor(key: SaleFunctionKey): SalePaymentType? = when (key) {
        SaleFunctionKey.F1 -> SalePaymentType.CASH
        SaleFunctionKey.F2 -> SalePaymentType.TRANSFER
        SaleFunctionKey.F3 -> SalePaymentType.CARD
        SaleFunctionKey.F4 -> SalePaymentType.CREDIT
        SaleFunctionKey.F5 -> null
    }
}
