package com.posfab.shared.core

import com.posfab.shared.core.diagnostics.CrashReporter
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel

open class BaseViewModel(
    dispatcher: CoroutineDispatcher = Dispatchers.Default,
) {
    protected val scope = CoroutineScope(
        SupervisorJob() + dispatcher + CoroutineExceptionHandler { _, t -> CrashReporter.report(t) },
    )

    open fun clear() {
        scope.cancel()
    }
}
