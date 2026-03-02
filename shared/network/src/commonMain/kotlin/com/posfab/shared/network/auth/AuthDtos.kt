package com.posfab.shared.network.auth

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class LoginRequestDto(
    val username: String,
    val password: String,
    @SerialName("terminalCode") val terminalCode: String,
)

@Serializable
data class RefreshRequestDto(
    val refreshToken: String,
)

@Serializable
data class LogoutRequestDto(
    val refreshToken: String,
)

@Serializable
data class AuthResponseDto(
    @SerialName("accessToken") val accessToken: String,
    @SerialName("refreshToken") val refreshToken: String,
    @SerialName("accessTokenExpiresAt") val accessTokenExpiresAt: String? = null,
    @SerialName("refreshTokenExpiresAt") val refreshTokenExpiresAt: String? = null,
    val user: UserDto,
)

@Serializable
data class UserDto(
    val id: String,
    val username: String,
    val roles: List<String>,
)
