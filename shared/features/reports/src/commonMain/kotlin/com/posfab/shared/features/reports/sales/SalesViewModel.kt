package com.posfab.shared.features.reports.sales

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import com.posfab.shared.core.BaseViewModel
import com.posfab.shared.core.input.DateMask
import com.posfab.shared.core.result.AppError
import com.posfab.shared.core.result.AppResult
import com.posfab.shared.features.reports.common.ReportsUseCases
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.updateAndGet
import kotlinx.coroutines.launch
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime

class SalesViewModel(
    private val reportsUseCases: ReportsUseCases,
    private val now: () -> Instant = { Clock.System.now() },
    val timeZone: TimeZone = TimeZone.currentSystemDefault(),
    dispatcher: CoroutineDispatcher = Dispatchers.Default,
) : BaseViewModel(dispatcher) {
    private val _state = MutableStateFlow(today().let { SalesState(day = it, today = it) })
    val state: StateFlow<SalesState> = _state.asStateFlow()

    fun load() {
        val today = today()
        showDay(today, today, resetView = true)
    }

    fun refresh() {
        val snapshot = _state.value
        val selectedId = snapshot.selectedId
        if (selectedId != null) {
            select(selectedId)
        } else {
            showDay(snapshot.day, today())
        }
    }

    fun previousDay() {
        val snapshot = _state.value
        if (snapshot.showsDetail) return
        showDay(snapshot.day.minus(1, DateTimeUnit.DAY), today())
    }

    fun nextDay() {
        val snapshot = _state.value
        if (snapshot.showsDetail) return
        val today = today()
        if (snapshot.day >= today) return
        val next = snapshot.day.plus(1, DateTimeUnit.DAY)
        showDay(if (next > today) today else next, today)
    }

    fun onDateInput(value: TextFieldValue) {
        _state.update {
            val masked = DateMask.apply(previous = it.dateInput.text, typed = value.text)
            it.copy(dateInput = TextFieldValue(masked, TextRange(masked.length)), notice = null)
        }
    }

    fun goToTypedDate() {
        val snapshot = _state.value
        if (snapshot.showsDetail) return
        val date = parseTypedDate(snapshot.dateInput.text)
        if (date == null) {
            _state.update { it.copy(notice = SalesStrings.DATE_INVALID) }
            return
        }
        val today = today()
        if (date > today) {
            _state.update { it.copy(notice = SalesStrings.DATE_FUTURE) }
            return
        }
        _state.update { it.copy(dateInput = TextFieldValue("")) }
        showDay(date, today)
    }

    fun select(id: Long) {
        _state.update {
            it.copy(
                selectedId = id,
                detail = it.detail?.takeIf { detail -> detail.id == id },
                isDetailLoading = true,
                detailError = null,
                notice = null,
            )
        }
        scope.launch {
            val result = guarded { reportsUseCases.salesRepository.saleDetail(id) }
            _state.update { current ->
                if (current.selectedId != id) {
                    current
                } else {
                    when (result) {
                        is AppResult.Success -> current.copy(isDetailLoading = false, detail = result.value)
                        is AppResult.Failure -> current.copy(
                            isDetailLoading = false,
                            detailError = SalesErrorText.from(result.error),
                        )
                    }
                }
            }
        }
    }

    fun back() {
        _state.update {
            it.copy(selectedId = null, detail = null, isDetailLoading = false, detailError = null)
        }
    }

    private fun showDay(day: LocalDate, today: LocalDate, resetView: Boolean = false) {
        val generation = _state.updateAndGet {
            val sameDay = it.day == day
            val moved = it.copy(
                day = day,
                today = today,
                sales = if (sameDay) it.sales else emptyList(),
                units = if (sameDay) it.units else null,
                isLoading = true,
                dayError = null,
                notice = null,
                nowEpochMillis = now().toEpochMilliseconds(),
                loadGeneration = it.loadGeneration + 1,
            )
            if (resetView) {
                moved.copy(
                    selectedId = null,
                    detail = null,
                    isDetailLoading = false,
                    detailError = null,
                    dateInput = TextFieldValue(""),
                )
            } else {
                moved
            }
        }.loadGeneration
        scope.launch {
            val repository = reportsUseCases.salesRepository
            val salesCall = async { guarded { repository.salesOfDay(day) } }
            val unitsCall = async { guarded { repository.unitsOfDay(day) } }
            val sales = salesCall.await()
            val units = unitsCall.await()
            _state.update { current ->
                if (current.loadGeneration != generation) {
                    current
                } else {
                    val failure = (sales as? AppResult.Failure)?.error ?: (units as? AppResult.Failure)?.error
                    current.copy(
                        sales = (sales as? AppResult.Success)?.value ?: current.sales,
                        units = (units as? AppResult.Success)?.value ?: current.units,
                        isLoading = false,
                        dayError = failure?.let(SalesErrorText::from),
                    )
                }
            }
        }
    }

    private fun today(): LocalDate = now().toLocalDateTime(timeZone).date

    private suspend fun <T> guarded(block: suspend () -> AppResult<T>): AppResult<T> = try {
        block()
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (failure: Exception) {
        AppResult.Failure(AppError.Unknown(failure.message ?: failure::class.simpleName.orEmpty()))
    }
}
