package com.posfab.shared.network.device

import com.posfab.shared.config.PosConfig
import com.posfab.shared.core.result.AppResult
import com.posfab.shared.network.http.safeCall
import io.ktor.client.HttpClient
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType

interface DeviceApi {
    suspend fun register(request: DeviceRegisterRequestDto): AppResult<DeviceRegisterResponseDto>
    suspend fun verify(request: DeviceVerifyRequestDto): AppResult<DeviceVerifyResponseDto>
}

class DeviceApiClient(
    private val httpClient: HttpClient,
    private val config: PosConfig,
) : DeviceApi {
    override suspend fun register(request: DeviceRegisterRequestDto): AppResult<DeviceRegisterResponseDto> = safeCall {
        httpClient.post("${config.apiBaseUrl}/devices/register") {
            contentType(ContentType.Application.Json)
            setBody(request)
        }
    }

    override suspend fun verify(request: DeviceVerifyRequestDto): AppResult<DeviceVerifyResponseDto> = safeCall {
        httpClient.post("${config.apiBaseUrl}/devices/verify") {
            contentType(ContentType.Application.Json)
            setBody(request)
        }
    }
}
