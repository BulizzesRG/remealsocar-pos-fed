package com.posfab.shared.core.diagnostics

import com.posfab.shared.core.BaseViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class BaseViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    private class ProbeViewModel(dispatcher: CoroutineDispatcher) : BaseViewModel(dispatcher) {
        var secondRan = false

        fun fail(message: String) {
            scope.launch { throw IllegalStateException(message) }
        }

        fun waitForever() {
            scope.launch { awaitCancellation() }
        }

        fun cancelItself() {
            scope.launch { throw CancellationException("cancelled on purpose") }
        }

        fun runSecond() {
            scope.launch { secondRan = true }
        }
    }

    @Test
    fun coroutine_failure_is_reported_and_scope_stays_alive() = runTest(dispatcher) {
        val vm = ProbeViewModel(dispatcher)

        vm.fail("base-view-model-boom")
        advanceUntilIdle()

        assertEquals("base-view-model-boom", CrashReporter.lastCrash.value?.message)
        assertEquals("IllegalStateException", CrashReporter.lastCrash.value?.type)

        vm.runSecond()
        advanceUntilIdle()

        assertTrue(vm.secondRan)
        vm.clear()
    }

    @Test
    fun cancellation_is_not_reported() = runTest(dispatcher) {
        CrashReporter.report(IllegalStateException("base-view-model-before-cancel"), screen = "Vender")
        val before = CrashReporter.lastCrash.value
        val vm = ProbeViewModel(dispatcher)

        vm.waitForever()
        vm.cancelItself()
        advanceUntilIdle()
        vm.clear()
        advanceUntilIdle()

        assertSame(before, CrashReporter.lastCrash.value)
    }
}
