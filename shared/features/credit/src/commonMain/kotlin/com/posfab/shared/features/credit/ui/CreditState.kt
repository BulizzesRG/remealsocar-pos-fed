package com.posfab.shared.features.credit.ui

import com.posfab.shared.features.credit.domain.CustomerMatch
import com.posfab.shared.features.credit.domain.CustomerStatement
import com.posfab.shared.features.credit.domain.Debtor
import com.posfab.shared.features.credit.domain.LoadedCustomer

data class CreditState(
    val debtors: List<Debtor> = emptyList(),
    val filter: String = "",
    val selectedId: Long? = null,
    val selected: CustomerStatement? = null,
    val isLoading: Boolean = false,
    val isStatementLoading: Boolean = false,
    val errorMessage: String? = null,
    val nowEpochMillis: Long = 0L,
    val canLoadLibreta: Boolean = false,
    val libreta: LibretaState? = null,
    val canEditCustomers: Boolean = false,
    val searchResults: List<CustomerMatch> = emptyList(),
    val isSearching: Boolean = false,
    val searchFailed: Boolean = false,
    val savedNotice: String? = null,
    val edit: CustomerEditState? = null,
) {
    val visibleDebtors: List<Debtor>
        get() {
            val query = foldForSearch(filter.trim())
            return if (query.isEmpty()) debtors else debtors.filter { foldForSearch(it.displayName).contains(query) }
        }

    val totalOwed: Double get() = debtors.sumOf { it.balance }

    val debtorCount: Int get() = debtors.size

    val showsDetail: Boolean get() = selectedId != null

    val showsLibreta: Boolean get() = libreta != null

    val otherCustomers: List<CustomerMatch>
        get() {
            val debtorIds = debtors.mapTo(HashSet()) { it.id }
            return searchResults.filterNot { it.id in debtorIds }
        }

    val showsNoMatches: Boolean
        get() = filter.isNotBlank() && visibleDebtors.isEmpty() && otherCustomers.isEmpty() && !isSearching && !searchFailed

    val isEditing: Boolean get() = edit != null

    val selectedOwesSince: String?
        get() = selectedId?.let { id -> debtors.firstOrNull { it.id == id }?.owesSince }
}

data class CustomerEditState(
    val customerId: Long,
    val code: String?,
    val form: CustomerEditForm,
    val original: CustomerEditForm,
    val isSaving: Boolean = false,
    val errorMessage: String? = null,
    val confirm: CustomerEditConfirm? = null,
    val leaveOnDiscard: Boolean = false,
    val focusRequest: Int = 0,
) {
    val hasChanges: Boolean get() = form != original

    val isDeactivating: Boolean get() = original.active && !form.active
}

data class CustomerEditForm(
    val name: String = "",
    val nickname: String = "",
    val phone: String = "",
    val address: String = "",
    val active: Boolean = true,
)

enum class CustomerEditConfirm { SAVE, DEACTIVATE, DISCARD }

data class LibretaState(
    val form: LibretaForm = LibretaForm(),
    val suggestions: List<CustomerMatch> = emptyList(),
    val isSearching: Boolean = false,
    val loadedToday: List<LoadedCustomer> = emptyList(),
    val errorMessage: String? = null,
    val pendingKey: String? = null,
    val focusRequest: Int = 0,
    val savedNotice: String? = null,
) {
    val formIsEmpty: Boolean
        get() = form.selected == null &&
            listOf(form.name, form.nickname, form.phone, form.amount, form.owedSince).all { it.isBlank() }
}

data class LibretaForm(
    val name: String = "",
    val nickname: String = "",
    val phone: String = "",
    val amount: String = "",
    val owedSince: String = "",
    val selected: CustomerMatch? = null,
    val isSaving: Boolean = false,
    val amountInputRejected: Boolean = false,
)

private val accentFolds: Map<Char, Char> = mapOf(
    'á' to 'a', 'à' to 'a', 'ä' to 'a', 'â' to 'a',
    'é' to 'e', 'è' to 'e', 'ë' to 'e', 'ê' to 'e',
    'í' to 'i', 'ì' to 'i', 'ï' to 'i', 'î' to 'i',
    'ó' to 'o', 'ò' to 'o', 'ö' to 'o', 'ô' to 'o',
    'ú' to 'u', 'ù' to 'u', 'ü' to 'u', 'û' to 'u',
)

internal fun foldForSearch(text: String): String =
    text.lowercase().map { accentFolds[it] ?: it }.joinToString("")
