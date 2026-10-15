package com.posfab.shared.features.catalog.common

import java.math.BigDecimal
import java.math.RoundingMode

fun proposeListPrice(cash: Double, pct: Double): Double =
    BigDecimal.valueOf(cash)
        .multiply(BigDecimal.ONE + BigDecimal.valueOf(pct).movePointLeft(2))
        .setScale(0, RoundingMode.CEILING)
        .toDouble()

fun moneyInput(value: Double?): String =
    value?.let { BigDecimal.valueOf(it).setScale(2, RoundingMode.HALF_UP).toPlainString() } ?: ""
