package com.posfab.shared.core.model

import kotlinx.serialization.Serializable

@Serializable
data class DeviceRegistration(
    val deviceToken: String,
    val terminalId: Long,
    val terminalCode: String,
    val terminalType: String,
    val businessUnitId: Long,
)
