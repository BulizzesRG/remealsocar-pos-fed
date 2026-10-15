package com.posfab.shared.auth.domain

import com.posfab.shared.core.model.Permission
import com.posfab.shared.core.model.UserRole
import kotlinx.serialization.Serializable

@Serializable
data class AuthTokens(
    val accessToken: String,
    val refreshToken: String,
)

@Serializable
data class AuthUser(
    val id: String,
    val username: String,
    val roles: Set<UserRole>,
    val permissions: Set<Permission>,
)

@Serializable
data class UserSession(
    val tokens: AuthTokens,
    val user: AuthUser,
    val terminalCode: String = "",
    val terminalType: String = "POS",
)
