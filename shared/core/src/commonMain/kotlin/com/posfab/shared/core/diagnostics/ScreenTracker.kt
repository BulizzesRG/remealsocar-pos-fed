package com.posfab.shared.core.diagnostics

import kotlin.concurrent.Volatile

object ScreenTracker {
    const val INITIAL = "Inicio de la app"

    @Volatile
    var current: String = INITIAL
}
