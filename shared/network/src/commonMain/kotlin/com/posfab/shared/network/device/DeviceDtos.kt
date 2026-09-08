package com.posfab.shared.network.device

import kotlinx.serialization.Serializable

@Serializable
data class DeviceRegisterRequestDto(
    val username: String,
    val password: String,
    val terminalCode: String,
    val deviceName: String,
)

@Serializable
data class DeviceRegisterResponseDto(
    val deviceToken: String,
    val terminal: DeviceTerminalInfoDto,
)

@Serializable
data class DeviceVerifyRequestDto(
    val deviceToken: String,
)

@Serializable
data class DeviceVerifyResponseDto(
    val deviceToken: String,
    val terminal: DeviceTerminalInfoDto,
)

@Serializable
data class DeviceTerminalInfoDto(
    val id: Long,
    val code: String,
    val terminalType: String,
    val businessUnitId: Long,
)
