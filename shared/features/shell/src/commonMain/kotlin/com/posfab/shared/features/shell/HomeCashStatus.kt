package com.posfab.shared.features.shell

sealed interface HomeCashStatus {
    data object Hidden : HomeCashStatus
    data object Loading : HomeCashStatus
    data object Closed : HomeCashStatus
    data class Open(val openedAt: String, val openingAmount: Double) : HomeCashStatus
    data object Unavailable : HomeCashStatus
}
