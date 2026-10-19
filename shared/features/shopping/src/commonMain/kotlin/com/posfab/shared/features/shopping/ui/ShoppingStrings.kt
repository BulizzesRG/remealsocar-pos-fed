package com.posfab.shared.features.shopping.ui

import com.posfab.shared.features.shopping.domain.ShoppingItem
import com.posfab.shared.features.shopping.domain.ShoppingItemStatus
import com.posfab.shared.features.shopping.domain.plainQty

object ShoppingStrings {
    const val TITLE = "Surtido"
    const val REFRESH = "Actualizar"
    const val INPUT_LABEL = "¿Qué falta?"
    const val QTY_LABEL = "Cuántos (opcional)"
    const val ADD = "Anotar"
    const val INPUT_KEYS = "Enter agrega · escanea para ligar el producto"
    const val EMPTY_OPEN = "Anota lo primero que falte y se crea la lista de hoy"
    const val CLOSE = "Cerrar lista para surtir"
    const val TO_SHOP = "Para surtir"
    const val BOUGHT = "Comprado"
    const val NOT_FOUND = "No había"
    const val LEFT_PENDING = "Quedó pendiente"
    const val FINISH = "Terminó el surtido"
    const val HISTORY = "Listas anteriores"
    const val NO_HISTORY = "Todavía no hay listas terminadas"
    const val FIX = "Corregir"
    const val REMOVE = "Quitar"
    const val SAVE = "Guardar"
    const val CANCEL = "Cancelar"
    const val BACK = "Volver"
    const val RETRY = "Reintentar"
    const val LOADING = "Cargando…"
    const val CARRIED = "viene de la lista anterior"
    const val LIST_TITLE_PREFIX = "Lista del"

    const val EDIT_TITLE = "Corregir renglón"
    const val EDIT_TEXT_LABEL = "Qué falta"
    const val EDIT_QTY_LABEL = "Cuántos"
    const val EDIT_KEYS = "Enter guarda · Esc cierra"

    const val CLOSE_TITLE = "¿Cerrar la lista para surtir?"
    const val CLOSE_TEXT = "Ya no se le podrán agregar renglones. Lo que se anote después irá a la lista siguiente."
    const val CLOSE_CONFIRM = "Cerrar lista"
    const val FINISH_TITLE = "¿Terminó el surtido?"
    const val FINISH_TEXT = "Lo marcado pasa a la lista nueva."
    const val FINISH_ALL_BOUGHT = "Se compró todo. La lista pasa a Listas anteriores."
    const val FINISH_CONFIRM = "Terminar"
    const val DELETE_TITLE = "¿Quitar este renglón?"
    const val DELETE_CONFIRM = "Quitar"
    const val KEEP = "Dejarlo"

    const val SAVED = "Guardado"
    const val CLOSED_NOTICE = "Lista cerrada para surtir"
    const val FINISHED_NOTICE = "Surtido terminado"
    const val LIST_CLOSED = "La lista ya se cerró para surtir"
    const val LIST_FINISHED = "Esta lista ya se terminó"

    const val TEXT_REQUIRED = "Escribe qué falta"
    const val TEXT_TOO_LONG = "El texto es muy largo: máximo 120 letras"
    const val TEXT_INVALID = "El texto tiene un carácter que no se puede guardar"
    const val QTY_INVALID = "La cantidad no es válida"
    const val LIST_EMPTY = "La lista está vacía"
    const val ITEM_NOT_FOUND = "Ese renglón ya no existe"
    const val PRODUCT_GONE = "Ese producto ya no se vende"
    const val RELOAD_AND_RETRY = "Vuelve a cargar la lista e inténtalo otra vez"
    const val FORBIDDEN = "Solo el gerente puede cerrar la lista"
    const val NETWORK = "Sin conexión con el servidor. Revisa la red e inténtalo otra vez"
    const val CONFLICT = "Alguien más cambió la lista. Se volvió a cargar"
    const val SIGN_IN_AGAIN = "Tu sesión terminó. Vuelve a entrar"
    const val FAILED = "No se pudo completar. Inténtalo otra vez"
    const val LOAD_FAILED = "No se pudo cargar la lista. Toca Actualizar"
    const val HISTORY_FAILED = "No se pudieron cargar las listas anteriores"
    const val DETAIL_FAILED = "No se pudo abrir la lista"
    const val SEARCH_FAILED = "No se pudo buscar el producto"

    fun added(text: String): String = "Se anotó: $text"

    fun removed(text: String): String = "Se quitó: $text"

    fun notFoundWithCode(code: String): String = "No hay ningún producto con el código $code"

    fun progress(bought: Int, total: Int): String = "$bought de $total comprados"

    fun itemCount(count: Int): String = if (count == 1) "1 renglón" else "$count renglones"

    fun itemLine(item: ShoppingItem): String = itemLine(item.text, item.qty)

    fun itemLine(text: String, qty: Double?): String = if (qty == null) text else "$text × ${plainQty(qty)}"

    fun byLine(user: String): String = if (user.isBlank()) "" else "anotó $user"

    fun historyStatus(status: ShoppingItemStatus): String = when (status) {
        ShoppingItemStatus.BOUGHT -> BOUGHT
        ShoppingItemStatus.NOT_FOUND -> NOT_FOUND
        ShoppingItemStatus.PENDING -> LEFT_PENDING
    }

    fun historyToggle(count: Int?): String = if (count == null) HISTORY else "$HISTORY ($count)"
}
