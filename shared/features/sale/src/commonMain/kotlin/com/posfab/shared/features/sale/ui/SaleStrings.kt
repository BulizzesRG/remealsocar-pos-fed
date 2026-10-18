package com.posfab.shared.features.sale.ui

import com.posfab.shared.features.sale.domain.SaleChannel
import com.posfab.shared.features.sale.domain.SaleLine
import com.posfab.shared.features.sale.domain.SalePaymentType
import com.posfab.shared.features.sale.domain.SalePriceType
import kotlin.math.abs
import kotlin.math.round

internal object SaleStrings {
    const val LOADING = "Cargando venta…"
    const val CASH_CLOSED_TITLE = "La caja está cerrada"
    const val CASH_CLOSED_BODY = "Abre el día para empezar a vender"
    const val OPEN_DAY = "Abrir día"
    const val RETRY = "Reintentar"
    const val EMPTY_CART = "Escanea o busca un producto"
    const val TOTAL = "Total"
    const val CLEAR = "Vaciar"
    const val NEW_ACCOUNT = "Nueva cuenta"
    const val CLOSE_ACCOUNT = "Cerrar cuenta"
    const val EMPTY_ACCOUNT = "Vacía"
    const val EMPTY_ACCOUNT_DESCRIPTION = "Cuenta vacía"
    const val ACCOUNT_SELECTED = "seleccionada"
    const val REMOVE = "Quitar"
    const val QTY_LABEL = "Cantidad"
    const val DECREASE = "Quitar uno"
    const val INCREASE = "Agregar uno"
    const val SEARCH_LABEL = "Escanea o busca"
    const val KITCHEN_FONDA = "Fonda"
    const val KITCHEN_TORTERIA = "Tortería"
    const val CHARGE_QUESTION = "¿Con cuánto paga?"
    const val EXACT = "Exacto"
    const val PAID_LABEL = "Paga con"
    const val CHANGE = "Cambio"
    const val BACK = "Volver"
    const val CHARGED = "Cobrado"
    const val EXACT_PAYMENT = "Pago exacto"
    const val NEXT = "Siguiente"
    const val CLEAR_TITLE = "¿Vaciar la venta?"
    const val CLEAR_MESSAGE = "Se quitarán todos los productos"
    const val CLEAR_CONFIRM = "Vaciar"
    const val CLEAR_CANCEL = "Seguir"
    const val KITCHEN_UNAVAILABLE = "No se pudo cargar el tablero de cocina"
    const val SALE_CHANGED = "La venta cambió, se volvió a cargar"
    const val CASH_SESSION_REQUIRED = "La caja está cerrada. Ábrela para cobrar"
    const val NO_PRICE = "Este producto no tiene precio"
    const val NOTHING_TO_CHARGE = "No hay nada que cobrar"
    const val INSUFFICIENT_STOCK = "No hay existencia suficiente"
    const val NO_CONNECTION = "Sin conexión. Revisa la red y vuelve a intentar"
    const val SESSION_EXPIRED = "Tu sesión terminó. Vuelve a entrar"
    const val NOT_ALLOWED = "No tienes permiso para hacer esto"
    const val TOO_MANY_REQUESTS = "Demasiados intentos. Espera unos segundos"
    const val FINISH_CHARGE_FIRST = "Termina el cobro primero"
    const val INPUT_REJECTED = "El valor no es válido, vuelve a escribirlo"
    const val ACCOUNT_CLOSED = "Esta cuenta ya se cerró"
    const val CHECKOUT_CONFLICT = "No se pudo cobrar. La venta se volvió a cargar, revísala y cobra de nuevo"
    const val POSSIBLY_CHARGED = "Es posible que la venta ya se haya cobrado. Revisa Ventas del día"
    const val PAID_LOCKED = "Cobro en curso: ya no se puede cambiar la forma ni el monto. Vuelve a intentar con el botón de cobrar"
    const val PAYMENT_CASH = "Efectivo"
    const val PAYMENT_TRANSFER = "Transferencia"
    const val PAYMENT_CARD = "Tarjeta"
    const val PAYMENT_CREDIT = "Fiar"
    const val CREDIT_DONE = "Fiado"
    const val CUSTOMER_SEARCH_LABEL = "Nombre del cliente"
    const val NEW_CUSTOMER = "Nuevo cliente"
    const val CUSTOMER_NAME_LABEL = "Nombre del cliente"
    const val CUSTOMER_NICKNAME_LABEL = "Apodo (opcional)"
    const val CUSTOMER_PHONE_LABEL = "Teléfono (opcional)"
    const val SAVE = "Guardar"
    const val CHANGE_CUSTOMER = "Otro cliente"
    const val NO_DEBT = "Sin deuda"
    const val ABONO = "Abonar"
    const val ABONO_ALL = "Todo"
    const val OWES_NOW = "Debe ahora"
    const val NO_CUSTOMERS_FOUND = "No hay nadie con ese nombre"
    const val NO_DEBTORS_FOUND = "Nadie con ese nombre te debe"
    const val PICKER_HINT = "Escribe al menos 2 letras"
    const val ABONO_FIELD_LABEL = "¿Cuánto abona?"
    const val AMOUNT_ON_TAB = "Se anota"
    const val CUSTOMER_SAVE_FAILED = "No se pudo guardar el cliente, intenta de nuevo"
    const val CUSTOMER_PHONE_INVALID = "El teléfono debe tener 10 números"
    const val CUSTOMER_NAME_REQUIRED = "Escribe el nombre del cliente"
    const val CUSTOMER_CODE_EXISTS = "Ya existe un cliente con ese código"
    const val ABONO_EXCEEDS_DEBT = "El abono no puede ser mayor que lo que debe"
    const val CUSTOMER_INACTIVE = "Este cliente está dado de baja"
    const val CUSTOMER_NOT_FOUND = "No se encontró el cliente"
    const val ABONO_CASH_CLOSED = "La caja está cerrada. Abre el día en Caja para registrar el abono"
    const val ABONO_LOCKED = "Abono en curso: vuelve a tocar Abonar para reintentar"
    const val CHANNEL_TAKEAWAY = "Normal"
    const val CHANNEL_ONSITE = "Corral"
    const val CASH_DISCOUNT = "Descuento por pago en efectivo"
    const val AMOUNT_DUE = "A pagar"
    const val IN_CASH = "En efectivo"
    const val PROMPT_OPEN_TITLE = "¿Cuánto?"
    const val PROMPT_WEIGHT_TITLE = "¿Cuánto pesa?"
    const val AMOUNT_LABEL = "Importe"
    const val KILOS_LABEL = "Kilos"
    const val PESOS_LABEL = "Pesos"
    const val KG_SUFFIX = "kg"
    const val PESOS_SUFFIX = "$"
    const val ADD = "Agregar"
    const val CHANGE_LINE = "Cambiar"
    const val CANCEL = "Cancelar"
    const val OPEN_PRICE = "Precio abierto"
    const val VARIOS = "Varios"
    const val AMOUNT_TOO_SMALL = "Ese importe es muy chico para este producto"
    const val AMOUNT_NOT_EXACT = "No se puede cobrar ese importe exacto"
    const val DELIVERY_READ_ONLY = "Es un pedido: solo se cobra. Cambia a una cuenta para vender"
    const val REFRESH_DELIVERIES = "Actualizar pedidos"
    const val DELIVERY_ALREADY_CHARGED = "Este pedido ya se cobró en otra caja"
    const val PAYS_WITH_PREFIX = "Paga con "
    const val DELIVERY = "Pedido"
    const val DELIVERY_CANCELLED = "Este pedido se canceló en Pedidos"
    const val DELIVERY_READ_ONLY_HINT = "Solo cobro · no se puede editar"
    const val DELIVERY_SEARCH_DISABLED = "Cambia a una cuenta para vender"
    const val DELIVERY_TAKEAWAY = "Pedido · Para llevar"
    val QUICK_BILLS: List<Int> = listOf(20, 50, 100, 200, 500)
    val QUICK_AMOUNTS: List<Int> = listOf(3, 5, 10)
    val QUICK_WEIGHTS: List<Pair<Double, String>> = listOf(
        0.25 to "¼ kg",
        0.5 to "½ kg",
        0.75 to "¾ kg",
        1.0 to "1 kg",
    )

    fun channelNotice(channel: SaleChannel): String = when (channel) {
        SaleChannel.ONSITE -> "Precios de corral"
        SaleChannel.TAKEAWAY -> "Precios normales"
    }

    fun paymentTypeLabel(type: SalePaymentType): String = when (type) {
        SalePaymentType.CASH -> PAYMENT_CASH
        SalePaymentType.TRANSFER -> PAYMENT_TRANSFER
        SalePaymentType.CARD -> PAYMENT_CARD
        SalePaymentType.CREDIT -> CREDIT_DONE
    }

    fun paymentOptionLabel(type: SalePaymentType): String = when (type) {
        SalePaymentType.CASH -> PAYMENT_CASH
        SalePaymentType.TRANSFER -> PAYMENT_TRANSFER
        SalePaymentType.CARD -> PAYMENT_CARD
        SalePaymentType.CREDIT -> PAYMENT_CREDIT
    }

    fun discount(amount: Double): String = "\u2212${money(amount)}"

    fun notFoundWithCode(code: String): String = "No hay ningún producto ni cliente con el código $code"

    fun accountChip(total: Double, count: Int): String = "${money(total)} · $count"

    fun deliveryChip(customerShortName: String?, cashTotal: Double): String =
        listOfNotNull(DELIVERY, customerShortName, money(cashTotal)).joinToString(" · ")

    fun deliveryChipName(customerShortName: String?): String =
        listOfNotNull(DELIVERY, customerShortName).joinToString(" · ")

    fun deliveriesLabel(count: Int): String = "Pedidos ($count)"

    fun changeAmount(amount: Double): String = "$CHANGE ${money(amount)}"

    fun deliveryHeader(name: String?, address: String?): String =
        listOfNotNull(name?.let { "$DELIVERY de $it" } ?: DELIVERY, address).joinToString(" · ")

    fun deliveryDone(name: String?): String = name?.let { "$DELIVERY de $it" } ?: DELIVERY

    fun paysWith(amount: Double): String = "$PAYS_WITH_PREFIX${money(amount)}"

    fun accountDescription(total: Double, count: Int): String =
        "Cuenta con ${money(total)}, $count ${if (count == 1) "producto" else "productos"}"

    fun confirmLabel(type: SalePaymentType, amount: Double): String = when (type) {
        SalePaymentType.CASH -> "Cobrar ${money(amount)} en efectivo"
        SalePaymentType.TRANSFER -> "Cobrar ${money(amount)} con transferencia"
        SalePaymentType.CARD -> "Cobrar ${money(amount)} con tarjeta"
        SalePaymentType.CREDIT -> "Fiar ${money(amount)}"
    }

    fun creditDoneTitle(name: String): String = "Fiado a $name"

    fun owes(name: String, balance: Double): String = "$name · ${owesAmount(balance)}"

    fun noDebt(name: String): String = "$name · $NO_DEBT"

    fun owesAmount(balance: Double): String = "debe ${money(balance)}"

    fun abonoLabel(amount: Double?): String = if (amount == null) ABONO else "$ABONO ${money(amount)}"

    fun abonoRegistered(amount: Double, name: String, balanceAfter: Double): String {
        val after = if (roundCents(balanceAfter) <= 0L) "$name ya no debe nada" else "$name debe ahora ${money(balanceAfter)}"
        return "Abono de ${money(amount)} registrado · $after"
    }

    fun chargeLabel(total: Double): String = "Cobrar ${money(total)}"

    fun added(name: String): String = "Agregado: $name"

    fun changed(name: String): String = "Cambiado: $name"

    fun kilosInput(qty: Double): String {
        val grams = round(qty * 1000.0).toLong()
        val absolute = abs(grams)
        val sign = if (grams < 0) "-" else ""
        return "$sign${absolute / 1000}.${(absolute % 1000).toString().padStart(3, '0')}"
    }

    fun kilos(qty: Double): String = "${kilosInput(qty)} kg"

    fun perKilo(price: Double): String = "${money(price)} / kg"

    fun weightFormula(kilos: Double, price: Double): String = "${kilos(kilos)} × ${money(price)}"

    fun pesosPreview(amount: Double, kilos: Double): String = "${money(amount)} ≈ ${kilos(kilos)}"

    fun lineQty(line: SaleLine): String = when (line.priceType) {
        SalePriceType.WEIGHT -> "${kilos(line.qty)} × ${money(line.unitPrice)}"
        else -> "${qtyTimes(line.qty)}  ${money(line.unitPrice)}"
    }

    fun notAdded(name: String): String = "No se agregó $name, vuelve a escanear"

    fun missing(amount: Double): String = "Falta ${money(amount)}"

    fun quickBill(amount: Int): String = "$$amount"

    fun qtyTimes(qty: Double): String = "×${qtyText(qty)}"

    fun folio(folio: String): String = "Folio $folio"

    fun couldNotComplete(message: String): String = "No se pudo completar: $message"

    private fun roundCents(amount: Double): Long = round(amount * 100.0).toLong()

    fun money(amount: Double): String {
        val cents = round(amount * 100.0).toLong()
        val sign = if (cents < 0) "-" else ""
        val absolute = abs(cents)
        val whole = (absolute / 100).toString().reversed().chunked(3).joinToString(",").reversed()
        return "$sign$$whole.${(absolute % 100).toString().padStart(2, '0')}"
    }

    fun qtyText(qty: Double): String {
        val whole = round(qty)
        return if (abs(qty - whole) < 1e-9) whole.toLong().toString() else qty.toString()
    }
}
