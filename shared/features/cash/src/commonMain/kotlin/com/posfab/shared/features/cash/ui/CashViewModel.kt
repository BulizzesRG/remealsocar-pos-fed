package com.posfab.shared.features.cash.ui

import com.posfab.shared.core.BaseViewModel
import com.posfab.shared.core.model.Permission
import com.posfab.shared.core.result.AppError
import com.posfab.shared.core.result.AppResult
import com.posfab.shared.features.cash.domain.CashDrawerStatus
import com.posfab.shared.features.cash.domain.CashSummary
import com.posfab.shared.features.cash.domain.PayoutReason
import com.posfab.shared.features.cash.usecase.CashUseCases
import kotlin.coroutines.cancellation.CancellationException
import kotlin.math.round
import kotlin.random.Random
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.getAndUpdate
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.updateAndGet
import kotlinx.coroutines.launch

class CashViewModel(
    private val cashUseCases: CashUseCases,
    private val terminalId: Long,
    private val businessUnitId: Long,
    permissions: Set<Permission> = emptySet(),
    private val now: () -> Instant = { Clock.System.now() },
    dispatcher: CoroutineDispatcher = Dispatchers.Default,
) : BaseViewModel(dispatcher) {
    private val _state = MutableStateFlow(CashState(canManage = Permission.MANAGE_CASH in permissions))
    val state: StateFlow<CashState> = _state.asStateFlow()

    private var loadJob: Job? = null
    private var keySequence = 0

    fun load() {
        loadJob?.cancel()
        loadJob = scope.launch { reload() }
    }

    fun refresh() {
        if (_state.value.hasOverlay) return
        _state.update { it.copy(notice = null, errorMessage = null) }
        load()
    }

    fun retry() {
        if (_state.value.phase !is CashPhase.Unavailable) return
        _state.update { it.copy(phase = CashPhase.Loading, notice = null, errorMessage = null) }
        load()
    }

    fun finishDay() {
        if (_state.value.phase !is CashPhase.DayClosed) return
        _state.update { it.copy(phase = CashPhase.Loading, notice = null, errorMessage = null) }
        load()
    }

    fun onOpeningAmountChange(value: String) {
        val sanitized = sanitizeAmount(value)
        _state.update {
            if (it.isOpening) {
                it
            } else {
                it.copy(openingInput = sanitized, openingRejected = sanitized != value, errorMessage = null)
            }
        }
    }

    fun openDay() {
        val snapshot = _state.value
        if (snapshot.phase !is CashPhase.Closed || snapshot.isOpening) return
        if (snapshot.openingRejected) {
            _state.update { it.copy(openingInput = "", openingRejected = false, errorMessage = CashStrings.INPUT_REJECTED) }
            return
        }
        val amount = snapshot.openingInput.toDoubleOrNull()?.takeIf { it >= 0.0 } ?: return
        val previous = _state.getAndUpdate {
            if (it.isOpening) it else it.copy(isOpening = true, notice = null, errorMessage = null)
        }
        if (previous.isOpening) return
        scope.launch {
            when (val result = guarded { cashUseCases.open(terminalId, businessUnitId, roundMoney(amount)) }) {
                is AppResult.Success -> {
                    reload()
                    _state.update { it.copy(isOpening = false) }
                }
                is AppResult.Failure -> if (CashErrorText.isAlreadyOpen(result.error)) {
                    reload(notice = CashStrings.ALREADY_OPEN)
                    _state.update { it.copy(isOpening = false) }
                } else {
                    _state.update { it.copy(isOpening = false, errorMessage = CashErrorText.from(result.error)) }
                }
            }
        }
    }

    fun openPayout() {
        _state.update {
            if (!it.canManage || it.openSession == null || it.hasOverlay) {
                it
            } else {
                it.copy(payout = PayoutForm(), notice = null, errorMessage = null)
            }
        }
    }

    fun dismissPayout() {
        _state.update { current -> if (current.payout?.isLocked == true) current else current.copy(payout = null) }
    }

    fun onPayoutEscape() {
        val form = _state.value.payout ?: return
        if (form.isLocked) return
        if (form.isBlank) dismissPayout() else editPayout { PayoutForm(reason = reason) }
    }

    fun onPayoutAmountChange(value: String) {
        val sanitized = sanitizeAmount(value)
        editPayout { copy(amount = sanitized, amountRejected = sanitized != value) }
    }

    fun onPayoutReasonChange(reason: PayoutReason) {
        editPayout { copy(reason = reason) }
    }

    fun onPayoutNotesChange(value: String) {
        editPayout { copy(notes = value.take(MAX_NOTES_LENGTH)) }
    }

    fun confirmPayout() {
        val snapshot = _state.value
        val form = snapshot.payout ?: return
        val session = snapshot.openSession ?: return
        if (form.isSubmitting) return
        if (form.amountRejected) {
            editPayout { copy(amount = "", amountRejected = false, errorMessage = CashStrings.INPUT_REJECTED) }
            return
        }
        val amount = form.parsedAmount?.let(::roundMoney)?.takeIf { it > 0.0 } ?: return
        val notes = if (form.needsNotes) {
            form.notes.trim().takeIf { it.isNotEmpty() } ?: run {
                updatePayout { copy(errorMessage = CashStrings.PAYOUT_NOTES_REQUIRED) }
                return
            }
        } else {
            null
        }
        if (cents(amount) > cents(session.expectedInDrawer)) {
            updatePayout { copy(errorMessage = CashStrings.NOT_ENOUGH) }
            return
        }
        val key = form.pendingKey ?: buildKey()
        val previous = _state.getAndUpdate { current ->
            val open = current.payout
            if (open == null || open.isSubmitting) {
                current
            } else {
                current.copy(payout = open.copy(isSubmitting = true, pendingKey = key, errorMessage = null))
            }
        }
        val before = previous.payout
        if (before == null || before.isSubmitting) return
        scope.launch { runPayout(amount, form.reason, notes, key) }
    }

    private suspend fun runPayout(amount: Double, reason: PayoutReason, notes: String?, key: String) {
        when (val result = guarded { cashUseCases.payout(terminalId, key, amount, reason, notes) }) {
            is AppResult.Success -> {
                _state.update { it.copy(payout = null) }
                reload(notice = CashStrings.payoutSaved(result.value.reason, result.value.amount))
            }
            is AppResult.Failure -> when {
                result.error is AppError.Network -> updatePayout {
                    copy(isSubmitting = false, pendingKey = key, errorMessage = CashStrings.PAYOUT_RETRY)
                }
                CashErrorText.isSessionClosed(result.error) -> {
                    _state.update { it.copy(payout = null) }
                    reload(notice = CashStrings.CLOSED_NOTICE)
                }
                else -> updatePayout {
                    copy(isSubmitting = false, pendingKey = null, errorMessage = CashErrorText.from(result.error))
                }
            }
        }
    }

    fun openClose() {
        _state.update {
            if (!it.canManage || it.openSession == null || it.hasOverlay) {
                it
            } else {
                it.copy(closing = CloseForm(), notice = null, errorMessage = null)
            }
        }
    }

    fun dismissClose() {
        _state.update { current -> if (current.closing?.isSubmitting == true) current else current.copy(closing = null) }
    }

    fun onCloseEscape() {
        val form = _state.value.closing ?: return
        if (form.isSubmitting) return
        if (form.counted.isBlank()) dismissClose() else editClose { CloseForm() }
    }

    fun onCountedChange(value: String) {
        val sanitized = sanitizeAmount(value)
        editClose { copy(counted = sanitized, countedRejected = sanitized != value) }
    }

    fun confirmClose() {
        val snapshot = _state.value
        val form = snapshot.closing ?: return
        val session = snapshot.openSession ?: return
        if (form.isSubmitting || session.openAccounts > 0) return
        if (form.countedRejected) {
            editClose { copy(counted = "", countedRejected = false, errorMessage = CashStrings.INPUT_REJECTED) }
            return
        }
        val counted = form.parsedCounted?.let(::roundMoney) ?: return
        val previous = _state.getAndUpdate { current ->
            val open = current.closing
            if (open == null || open.isSubmitting) {
                current
            } else {
                current.copy(closing = open.copy(isSubmitting = true, errorMessage = null))
            }
        }
        val before = previous.closing
        if (before == null || before.isSubmitting) return
        scope.launch { runClose(counted) }
    }

    private suspend fun runClose(counted: Double) {
        when (val result = guarded { cashUseCases.close(terminalId, counted) }) {
            is AppResult.Success -> _state.update {
                it.copy(
                    phase = CashPhase.DayClosed(result.value),
                    closing = null,
                    payout = null,
                    notice = null,
                    errorMessage = null,
                    isRefreshing = false,
                    loadGeneration = it.loadGeneration + 1,
                )
            }
            is AppResult.Failure -> when {
                CashErrorText.isSessionClosed(result.error) -> {
                    _state.update { it.copy(closing = null) }
                    reload(notice = CashStrings.CLOSED_NOTICE)
                }
                CashErrorText.isOpenAccounts(result.error) -> {
                    reload()
                    updateClose { copy(isSubmitting = false, errorMessage = CashErrorText.from(result.error)) }
                }
                else -> updateClose { copy(isSubmitting = false, errorMessage = CashErrorText.from(result.error)) }
            }
        }
    }

    private suspend fun reload(notice: String? = null) {
        val generation = _state.updateAndGet {
            it.copy(isRefreshing = true, loadGeneration = it.loadGeneration + 1)
        }.loadGeneration
        val result = guarded { cashUseCases.summary(terminalId) }
        _state.update { current ->
            when {
                current.loadGeneration != generation -> current
                current.phase is CashPhase.DayClosed -> current.copy(isRefreshing = false)
                result is AppResult.Success -> current.applySummary(result.value, notice)
                result is AppResult.Failure -> current.applyFailure(CashErrorText.from(result.error), notice)
                else -> current
            }
        }
    }

    private fun CashState.applySummary(summary: CashSummary, notice: String?): CashState {
        val session = summary.session
        return if (summary.status == CashDrawerStatus.OPEN && session != null) {
            copy(
                phase = CashPhase.Open(session),
                isRefreshing = false,
                notice = notice ?: this.notice,
                errorMessage = null,
            )
        } else {
            val wasClosed = phase is CashPhase.Closed
            copy(
                phase = CashPhase.Closed(summary.previousClosingAmount),
                openingInput = if (wasClosed) openingInput else editableAmount(summary.previousClosingAmount),
                openingRejected = if (wasClosed) openingRejected else false,
                isRefreshing = false,
                payout = null,
                closing = null,
                notice = notice ?: this.notice,
                errorMessage = null,
            )
        }
    }

    private fun CashState.applyFailure(message: String, notice: String?): CashState = when (phase) {
        is CashPhase.Open, is CashPhase.Closed -> copy(isRefreshing = false, notice = notice, errorMessage = message)
        else -> copy(phase = CashPhase.Unavailable(message), isRefreshing = false, notice = notice, errorMessage = null)
    }

    private fun editPayout(change: PayoutForm.() -> PayoutForm) {
        updatePayout { if (isLocked) this else change().copy(pendingKey = null, errorMessage = null) }
    }

    private fun updatePayout(change: PayoutForm.() -> PayoutForm) {
        _state.update { current -> current.payout?.let { current.copy(payout = it.change()) } ?: current }
    }

    private fun editClose(change: CloseForm.() -> CloseForm) {
        updateClose { if (isSubmitting) this else change().copy(errorMessage = null) }
    }

    private fun updateClose(change: CloseForm.() -> CloseForm) {
        _state.update { current -> current.closing?.let { current.copy(closing = it.change()) } ?: current }
    }

    private suspend fun <T> guarded(block: suspend () -> AppResult<T>): AppResult<T> = try {
        block()
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (failure: Exception) {
        AppResult.Failure(AppError.Unknown(failure.message ?: failure::class.simpleName.orEmpty()))
    }

    private fun buildKey(): String {
        keySequence += 1
        return "payout-${now().toEpochMilliseconds()}-$keySequence-${Random.nextInt(KEY_RANDOM_MIN, KEY_RANDOM_MAX)}"
    }

    private fun sanitizeAmount(value: String): String {
        val filtered = value.filter { it.isDigit() || it == '.' }
        val dot = filtered.indexOf('.')
        if (dot < 0) return filtered.take(MAX_AMOUNT_DIGITS)
        val whole = filtered.substring(0, dot).take(MAX_AMOUNT_DIGITS)
        val decimals = filtered.substring(dot + 1).replace(".", "").take(MAX_AMOUNT_DECIMALS)
        return "$whole.$decimals"
    }

    private fun editableAmount(amount: Double?): String {
        if (amount == null) return ""
        val total = cents(amount).coerceAtLeast(0L)
        val whole = total / CENTS_PER_UNIT
        val fraction = total % CENTS_PER_UNIT
        return if (fraction == 0L) "$whole" else "$whole.${fraction.toString().padStart(2, '0')}"
    }

    private fun cents(amount: Double): Long = round(amount * CENTS_PER_UNIT).toLong()

    private fun roundMoney(amount: Double): Double = round(amount * CENTS_PER_UNIT) / CENTS_PER_UNIT

    companion object {
        const val MAX_NOTES_LENGTH = 255
        private const val MAX_AMOUNT_DIGITS = 6
        private const val MAX_AMOUNT_DECIMALS = 2
        private const val CENTS_PER_UNIT = 100L
        private const val KEY_RANDOM_MIN = 1_000
        private const val KEY_RANDOM_MAX = 9_999
    }
}
