package com.posfab.shared.features.shell

import com.posfab.shared.core.model.Permission

enum class ShellRoute(val title: String, val permission: Permission?) {
    HOME("Inicio", null),
    SELL("Vender", Permission.SELL),
    CASH("Caja", Permission.OPEN_CASH),
    FIADO("Fiado", Permission.VIEW_CREDIT),
    DAILY_SALES("Ventas del día", Permission.VIEW_SALES),
    PRODUCTS("Productos", Permission.MANAGE_PRODUCTS),
    HELP("Ayuda", null),
}
