package com.posfab.shared.network.auth

import kotlinx.serialization.Serializable

@Serializable
data class LoginRequestDto(
    val username: String,
    val password: String,
    val terminalCode: String,
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
    val accessToken: String,
    val refreshToken: String,
    val accessExpiresAt: String,
    val user: UserDto,
    val terminal: TerminalDto,
)

@Serializable
data class AuthRefreshResponseDto(
    val accessToken: String,
    val refreshToken: String,
    val accessExpiresAt: String,
)

@Serializable
data class UserDto(
    val id: Long,
    val fullName: String,
    val role: String,
    val permissions: List<String> = emptyList(),
)

@Serializable
data class TerminalDto(
    val id: Long,
    val code: String,
)
