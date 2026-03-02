package com.posfab.shared.network.auth

import com.posfab.shared.config.PosConfig
import com.posfab.shared.core.result.AppResult
import com.posfab.shared.network.http.safeCall
import io.ktor.client.HttpClient
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType

interface AuthApi {
    suspend fun login(request: LoginRequestDto): AppResult<AuthResponseDto>
    suspend fun refresh(request: RefreshRequestDto): AppResult<AuthResponseDto>
    suspend fun logout(request: LogoutRequestDto, accessToken: String? = null): AppResult<Unit>
}

class AuthApiClient(
    private val httpClient: HttpClient,
    private val config: PosConfig,
) : AuthApi {
    override suspend fun login(request: LoginRequestDto): AppResult<AuthResponseDto> = safeCall {
        httpClient.post("${config.apiBaseUrl}/api/v1/auth/login") {
            contentType(ContentType.Application.Json)
            setBody(request)
        }
    }

    override suspend fun refresh(request: RefreshRequestDto): AppResult<AuthResponseDto> = safeCall {
        httpClient.post("${config.apiBaseUrl}/api/v1/auth/refresh") {
            contentType(ContentType.Application.Json)
            setBody(request)
        }
    }

    override suspend fun logout(request: LogoutRequestDto, accessToken: String?): AppResult<Unit> = safeCall {
        httpClient.post("${config.apiBaseUrl}/api/v1/auth/logout") {
            contentType(ContentType.Application.Json)
            accessToken?.let { header(HttpHeaders.Authorization, "Bearer $it") }
            setBody(request)
        }
    }
}
