package com.posfab.shared.features.reports.sales

import androidx.compose.ui.text.input.TextFieldValue
import kotlinx.datetime.LocalDate

data class SalesState(
    val day: LocalDate,
    val today: LocalDate,
    val dateInput: TextFieldValue = TextFieldValue(""),
    val sales: List<SaleListItem> = emptyList(),
    val units: DayUnits? = null,
    val isLoading: Boolean = false,
    val selectedId: Long? = null,
    val detail: SaleDetail? = null,
    val isDetailLoading: Boolean = false,
    val dayError: String? = null,
    val detailError: String? = null,
    val notice: String? = null,
    val nowEpochMillis: Long = 0L,
    val loadGeneration: Int = 0,
) {
    val total: Double get() = sales.sumOf { it.total }

    val count: Int get() = sales.size

    val byPayment: Map<SalePayment, Double>
        get() = SalePayment.entries.associateWith { payment ->
            sales.filter { it.payment == payment }.sumOf { it.total }
        }

    val isToday: Boolean get() = day >= today

    val showsDetail: Boolean get() = selectedId != null

    val showsAmounts: Boolean get() = sales.isNotEmpty() || (!isLoading && dayError == null)
}
