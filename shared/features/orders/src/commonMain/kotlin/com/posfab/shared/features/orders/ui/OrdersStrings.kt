package com.posfab.shared.features.orders.ui

import com.posfab.shared.features.orders.domain.CancelReason
import com.posfab.shared.features.orders.domain.OrderKitchenKind
import com.posfab.shared.features.orders.domain.OrderPriceType
import com.posfab.shared.features.orders.domain.OrderStage
import kotlin.math.abs
import kotlin.math.round
import kotlin.time.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

object OrdersStrings {
    const val TITLE = "Pedidos"
    const val NEW_ORDER = "Nuevo pedido"
    const val REFRESH = "Actualizar"
    const val OPEN_SECTION = "Abiertos"
    const val EMPTY_OPEN = "No hay pedidos abiertos"
    const val EMPTY_OPEN_HINT = "Toca Nuevo pedido cuando llame un cliente"
    const val RETRY = "Reintentar"
    const val NO_CUSTOMER_MATCH = "Sin resultados. Toca Nuevo cliente"
    const val CUSTOMER_SEARCH_FAILED = "No se pudo buscar. Revisa la conexión"
    const val CUSTOMER_SAVE_CONFLICT = "No se pudo guardar el cliente. Revisa el teléfono"
    const val DISCARD_FAILED = "No se pudo descartar el pedido vacío"
    const val MISSING_CUSTOMER = "Falta: cliente"
    const val MISSING_ADDRESS = "Falta: dirección"
    const val MISSING_LINES = "Falta: productos"
    const val PROMPT_KEYS = "Enter agrega · Esc cancela"
    const val LESS = "Menos"
    const val MORE = "Más"
    const val NONE_TODAY = "Ninguno todavía"
    const val STAGE_ANOTADO = "Anotado"
    const val STAGE_ENVIADO = "Enviado"
    const val MARK_SENT = "Enviado"
    const val CANCEL = "Cancelar"
    const val NO_NAME = "Sin nombre"
    const val NO_ADDRESS = "Sin dirección"
    const val BACK = "Volver"
    const val NEW_ORDER_TITLE = "Pedido nuevo"
    const val WHO_AND_WHERE = "Quién y a dónde"
    const val CUSTOMER_QUERY = "Nombre o teléfono"
    const val SEARCHING = "Buscando…"
    const val NEW_CUSTOMER = "Nuevo cliente"
    const val OTHER_CUSTOMER = "Otro cliente"
    const val SEARCH_CUSTOMER = "Buscar cliente"
    const val NEW_CUSTOMER_NAME = "Nombre"
    const val NEW_CUSTOMER_PHONE = "Teléfono (10 dígitos)"
    const val SAVE_CUSTOMER = "Guardar cliente"
    const val ADDRESS = "Dirección"
    const val NOTES = "Nota (opcional)"
    const val PAYS_WITH = "¿Con cuánto paga?"
    const val PRODUCTS = "Productos"
    const val SEARCH_PRODUCT = "Buscar producto"
    const val NO_PRODUCTS_FOUND = "No hay productos con ese nombre"
    const val KITCHEN_FONDA = "Fonda"
    const val KITCHEN_TORTERIA = "Tortería"
    const val OPEN_PRICE = "Precio abierto"
    const val ORDER_LINES = "Lo que pidió"
    const val EMPTY_CART = "Todavía no hay productos"
    const val REMOVE = "Quitar"
    const val SAVE = "Guardar"
    const val CANCEL_ORDER = "Cancelar pedido"
    const val WEIGHT_PROMPT = "¿Cuánto pesa?"
    const val OPEN_PROMPT = "¿Cuánto?"
    const val KILOS = "Kilos"
    const val PESOS = "Pesos"
    const val KG_SUFFIX = "kg"
    const val PESO_SIGN = "$"
    const val ADD = "Agregar"
    const val CANCEL_TITLE = "¿Por qué se cancela?"
    const val CANCEL_NOTES = "¿Por qué?"
    const val KEEP_ORDER = "No cancelar"
    const val LEAVE_TITLE = "¿Salir sin guardar?"
    const val LEAVE_MESSAGE = "Se pierden los cambios de cliente, dirección, nota y pago. Los productos ya quedaron anotados."
    const val LEAVE_CONFIRM = "Salir"
    const val STAY = "Seguir aquí"
    const val LOADING = "Cargando…"
    const val NAME_INVALID = "Escribe el nombre del cliente"
    const val PHONE_INCOMPLETE = "El teléfono debe tener 10 dígitos"
    const val PROMPT_INVALID = "Escribe una cantidad mayor a cero"
    const val WAIT_FOR_LINES = "Espera a que terminen de guardarse los productos"
    const val SAVED = "Pedido guardado"
    const val SENT = "Pedido enviado"
    const val CANCELLED = "Pedido cancelado"
    const val ORDER_NOT_FOUND = "Ese pedido ya no está"
    const val ORDER_CLOSED = "Ese pedido ya se cerró"
    const val NOTES_REQUIRED = "Escribe por qué"
    const val CUSTOMER_INACTIVE = "Ese cliente está dado de baja"
    const val CUSTOMER_NOT_FOUND = "Ese cliente ya no está"
    const val PAYS_WITH_INVALID = "Revisa con cuánto paga"
    const val LINE_NOT_FOUND = "Ese producto ya no está en el pedido"
    const val HAS_LINES = "El pedido tiene productos: cancélalo con un motivo"
    const val TEXT_TOO_LONG = "El texto es demasiado largo"
    const val CONFLICT = "El pedido cambió en otro equipo. Ya se recargó"
    const val NETWORK = "Sin conexión. Revisa la red y vuelve a intentar"
    const val FORBIDDEN = "Tu cuenta no puede anotar pedidos"
    const val SIGN_IN_AGAIN = "Vuelve a entrar"
    const val FAILED = "No se pudo completar. Vuelve a intentar"
    const val LOAD_FAILED = "No se pudieron cargar los pedidos"
    const val SEPARATOR = " · "
    const val PIECES = "pz"
    private const val HOURS_PER_HALF_DAY = 12
    private const val AM = "am"
    private const val PM = "pm"
    private const val QTY_SCALE = 1000

    val QUICK_WEIGHTS: List<Pair<Double, String>> = listOf(0.25 to "0.250 kg", 0.5 to "0.500 kg", 1.0 to "1 kg")

    val CANCEL_REASONS: List<CancelReason> = CancelReason.entries

    fun paidToday(count: Int): String = "Pagados hoy ($count)"

    fun cancelledToday(count: Int): String = "Cancelados hoy ($count)"

    fun orderOf(name: String): String = "Pedido de $name"

    fun openSection(count: Int): String = "Abiertos ($count)"

    fun cancelSubtitle(name: String?, total: Double): String =
        "${orderOf(name?.takeIf { it.isNotBlank() } ?: NO_NAME)}$SEPARATOR${money(total)}"

    fun total(amount: Double): String = "Total ${money(amount)}"

    fun change(amount: Double): String = "Cambio ${money(amount)}"

    fun missing(amount: Double): String = "Le falta ${money(abs(amount))}"

    fun changeOrMissing(change: Double): String = if (round(change * 100.0) < 0.0) missing(change) else change(change)

    fun paysWithLine(paysWith: Double, change: Double?): String {
        val paid = "Paga con ${money(paysWith)}"
        return change?.let { "$paid$SEPARATOR${changeOrMissing(it)}" } ?: paid
    }

    fun stageLabel(stage: OrderStage?): String = when (stage) {
        OrderStage.ENVIADO -> STAGE_ENVIADO
        OrderStage.ANOTADO, null -> STAGE_ANOTADO
    }

    fun reasonLabel(reason: CancelReason?): String = when (reason) {
        CancelReason.NO_CONTESTO -> "No contestó"
        CancelReason.DIRECCION -> "Dirección equivocada"
        CancelReason.SE_ARREPINTIO -> "Se arrepintió"
        CancelReason.SIN_DINERO -> "No tenía dinero"
        CancelReason.OTRO, null -> "Otro"
    }

    fun cancelledLine(reason: CancelReason?, notes: String?): String =
        listOfNotNull(reasonLabel(reason), notes?.takeIf { it.isNotBlank() }).joinToString(SEPARATOR)

    fun kitchenLabel(kind: OrderKitchenKind): String = when (kind) {
        OrderKitchenKind.FONDA -> KITCHEN_FONDA
        OrderKitchenKind.TORTERIA -> KITCHEN_TORTERIA
    }

    fun perKilo(price: Double): String = "${money(price)} / kg"

    fun productCount(count: Int): String = if (count == 1) "1 producto" else "$count productos"

    fun folio(folio: String): String = "Venta $folio"

    fun qtyLabel(qty: Double, unitCode: String, priceType: OrderPriceType): String = when (priceType) {
        OrderPriceType.OPEN -> ""
        OrderPriceType.WEIGHT -> "${thousandths(qty, trim = false)} $KG_SUFFIX"
        OrderPriceType.PIECE -> "${thousandths(qty, trim = true)} ${pieceUnit(unitCode)}"
        OrderPriceType.OTHER -> listOf(thousandths(qty, trim = true), unitCode.trim().lowercase())
            .filter { it.isNotEmpty() }
            .joinToString(" ")
    }

    fun addButton(amount: Double?): String = amount?.takeIf { it > 0.0 }?.let { "$ADD ${money(it)}" } ?: ADD

    fun timeLabel(instant: Instant, timeZone: TimeZone = TimeZone.currentSystemDefault()): String {
        val local = instant.toLocalDateTime(timeZone)
        val hour = (local.hour % HOURS_PER_HALF_DAY).let { if (it == 0) HOURS_PER_HALF_DAY else it }
        val suffix = if (local.hour < HOURS_PER_HALF_DAY) AM else PM
        return "$hour:${local.minute.toString().padStart(2, '0')} $suffix"
    }

    fun money(amount: Double): String {
        val cents = round(amount * 100.0).toLong()
        val sign = if (cents < 0) "-" else ""
        val absolute = abs(cents)
        val whole = (absolute / 100).toString().reversed().chunked(3).joinToString(",").reversed()
        return "$sign$$whole.${(absolute % 100).toString().padStart(2, '0')}"
    }

    private fun pieceUnit(unitCode: String): String = when (val code = unitCode.trim().uppercase()) {
        "", "PZA", "PZ" -> PIECES
        else -> code.lowercase()
    }

    private fun thousandths(qty: Double, trim: Boolean): String {
        val scaled = round(qty * QTY_SCALE).toLong()
        val sign = if (scaled < 0) "-" else ""
        val absolute = abs(scaled)
        val whole = absolute / QTY_SCALE
        val fraction = (absolute % QTY_SCALE).toString().padStart(3, '0')
        val shown = if (trim) fraction.trimEnd('0') else fraction
        return if (shown.isEmpty()) "$sign$whole" else "$sign$whole.$shown"
    }
}
