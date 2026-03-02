package com.posfab.shared.features.login

import com.posfab.shared.core.model.TerminalCode

data class LoginState(
    val username: String = "",
    val password: String = "",
    val terminal: TerminalCode = TerminalCode.POS1,
    val isLoading: Boolean = false,
    val error: String? = null,
)
