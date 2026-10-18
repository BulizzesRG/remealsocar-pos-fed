package com.posfab.shared.features.catalog.ui

internal object CatalogStrings {
    const val NEW_PRODUCT = "Nuevo"
    const val SEARCH_LABEL = "Buscar o escanear"
    const val FILTER_SELLING = "Se venden"
    const val FILTER_ALL = "Todos"
    const val OPEN_PRICE = "Precio abierto"
    const val CHIP_FONDA = "Fonda"
    const val CHIP_TORTERIA = "Tortería"
    const val CHIP_WEIGHT = "Por kilo"
    const val CHIP_INACTIVE = "Ya no se vende"
    const val LOAD_MORE = "Ver más"
    const val EMPTY_LIST = "No hay productos"
    const val EMPTY_SEARCH = "No se encontró ningún producto"
    const val EMPTY_DETAIL = "Escanea o busca un producto, o toca Nuevo"
    const val NEW_TITLE = "Producto nuevo"

    const val NAME = "Nombre"
    const val BARCODE = "Código de barras"
    const val GENERATE_CODE = "Generar código"
    const val GENERATING_CODE = "Generando…"
    const val CODE_ON_SAVE = "Se generará al guardar"
    const val DONT_GENERATE_CODE = "No generar"
    const val HOW_SOLD = "Cómo se vende"
    const val BY_PIECE = "Por pieza"
    const val BY_WEIGHT = "Por kilo"
    const val BY_OPEN_PRICE = "Precio abierto"
    const val CASH_TAKEAWAY = "Precio en efectivo normal"
    const val CASH_ONSITE = "Precio en efectivo en corral (opcional)"
    const val WITH_CARD = "Con tarjeta"
    const val SUGGESTED_PRICE = "Precio sugerido, sin descuento"
    const val KITCHEN = "Cocina"
    const val KITCHEN_NONE = "No"
    const val KITCHEN_FONDA = "Fonda"
    const val KITCHEN_TORTERIA = "Tortería"
    const val COUNT_SECTION = "Para contar"
    const val COUNTED = "Se cuenta"
    const val COUNTED_HINT = "Solo lo que se vende entero"
    const val PIECES_PER_PACK = "Piezas por paquete"
    const val ACTIVE = "Se vende"
    const val ACTIVE_HINT = "Apágalo para descontinuar sin borrar"
    const val SAVE = "Guardar"
    const val CANCEL = "Cancelar"
    const val UNSAVED_CHANGES = "Cambios sin guardar"

    const val PRICE_HISTORY = "Precios anteriores"
    const val PRICE_HISTORY_EMPTY = "Este producto no ha cambiado de precio"
    const val PRICE_HISTORY_LOADING = "Cargando…"
    const val HISTORY_UNTIL = "Hasta"
    const val HISTORY_WITH_CARD = "con tarjeta"
    const val HISTORY_SUGGESTED = "precio sugerido"
    const val HISTORY_ONSITE = "corral"
    const val HISTORY_CHANGED_BY = "lo cambió"
    const val UNKNOWN_DATE = "—"
    const val AM = "am"
    const val PM = "pm"
    val MONTHS = listOf("ene", "feb", "mar", "abr", "may", "jun", "jul", "ago", "sep", "oct", "nov", "dic")

    const val SAVED = "Guardado"
    const val CODE_GENERATED = "Código generado"

    const val NAME_REQUIRED = "Escribe el nombre"
    const val CASH_REQUIRED = "Escribe el precio en efectivo normal"
    const val CARD_REQUIRED = "Escribe el precio con tarjeta"
    const val CARD_BELOW_CASH = "El precio con tarjeta no puede ser menor que el de efectivo"
    const val ONSITE_INVALID = "El precio en efectivo en corral debe ser mayor que cero"
    const val PIECES_INVALID = "Piezas por paquete debe ser 2 o más"

    const val CONFIRM_CREATE_TITLE = "¿Crear el producto?"
    const val CONFIRM_CREATE = "Crear"
    const val CONFIRM_EDIT_TITLE = "¿Guardar los cambios?"
    const val CONFIRM_EDIT = "Guardar"
    const val CONFIRM_BACK = "Volver"
    const val DISCARD_TITLE = "Hay cambios sin guardar"
    const val DISCARD_CONFIRM = "Salir sin guardar"
    const val DISCARD_KEEP = "Seguir editando"

    fun notFoundWithCode(code: String): String = "No hay ningún producto con el código $code"

    fun currentPriceSince(date: String): String = "Precio actual desde $date"

    fun showing(count: Int, total: Int): String = "Mostrando $count de $total"

    fun confirmCreateMessage(name: String): String = "Se creará «$name»"

    fun confirmEditMessage(name: String): String = "Se guardarán los cambios de «$name»"

    fun discardMessage(name: String): String =
        if (name.isBlank()) "Si sales se perderán los cambios" else "Si sales se perderán los cambios de «$name»"

    fun cardHint(pct: Double): String {
        val shown = if (pct % 1.0 == 0.0) pct.toLong().toString() else pct.toString()
        return "Con tarjeta se propone con el $shown% y se puede corregir"
    }
}
