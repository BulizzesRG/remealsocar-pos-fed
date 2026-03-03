package com.posfab.shared.core

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel

open class BaseViewModel(
    dispatcher: CoroutineDispatcher = Dispatchers.Default,
) {
    protected val scope = CoroutineScope(SupervisorJob() + dispatcher)

    open fun clear() {
        scope.cancel()
    }
}
