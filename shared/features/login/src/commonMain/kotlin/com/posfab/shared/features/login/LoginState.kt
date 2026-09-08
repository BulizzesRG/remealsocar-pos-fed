package com.posfab.shared.features.login

data class LoginState(
    val username: String = "",
    val password: String = "",
    val terminalCode: String = "",
    val isLoading: Boolean = false,
    val error: String? = null,
)
