package com.posfab.shared.auth

import com.posfab.shared.auth.repository.AuthRepositoryImpl
import com.posfab.shared.auth.session.InMemorySessionStorage
import com.posfab.shared.auth.session.SessionManager
import com.posfab.shared.config.PosConfig
import com.posfab.shared.config.PosEnv
import com.posfab.shared.core.logging.LogLevel
import com.posfab.shared.core.model.TerminalCode
import com.posfab.shared.core.result.AppError
import com.posfab.shared.core.result.AppResult
import com.posfab.shared.network.auth.AuthApiClient
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class AuthRepositoryTest {
    private val config = PosConfig(
        env = PosEnv.DEV,
        apiBaseUrl = "http://localhost:8080",
        logLevel = LogLevel.DEBUG,
        requestTimeoutMs = 5_000,
        enableVerboseLogs = false,
    )

    @Test
    fun login_success_maps_user_session() = runTest {
        val engine = MockEngine {
            respond(
                content = """
                {
                  "accessToken": "access-1",
                  "refreshToken": "refresh-1",
                  "user": {"id": "u1", "username": "alice", "roles": ["CASHIER"]}
                }
                """.trimIndent(),
                status = HttpStatusCode.OK,
                headers = headersOf("Content-Type", ContentType.Application.Json.toString()),
            )
        }
        val api = AuthApiClient(httpClient(engine), config)
        val repo = AuthRepositoryImpl(api, SessionManager(InMemorySessionStorage()))

        val result = repo.login("alice", "pw", TerminalCode.POS1)

        val success = assertIs<AppResult.Success<*>>(result)
        assertEquals("alice", (success.value as com.posfab.shared.auth.domain.UserSession).user.username)
    }

    @Test
    fun login_failure_maps_unauthorized() = runTest {
        val engine = MockEngine {
            respond(
                content = "{}",
                status = HttpStatusCode.Unauthorized,
                headers = headersOf("Content-Type", ContentType.Application.Json.toString()),
            )
        }
        val api = AuthApiClient(httpClient(engine), config)
        val repo = AuthRepositoryImpl(api, SessionManager(InMemorySessionStorage()))

        val result = repo.login("alice", "bad", TerminalCode.POS1)

        val failure = assertIs<AppResult.Failure>(result)
        assertIs<AppError.Unauthorized>(failure.error)
    }

    private fun httpClient(engine: MockEngine): HttpClient {
        val json = Json { ignoreUnknownKeys = true }
        return HttpClient(engine) {
            install(ContentNegotiation) { json(json) }
            expectSuccess = false
        }
    }
}
