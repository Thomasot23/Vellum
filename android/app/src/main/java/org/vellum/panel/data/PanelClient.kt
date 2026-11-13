// SPDX-License-Identifier: AGPL-3.0-or-later
package org.vellum.panel.data

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.vellum.panel.enroll.DeviceIdentity
import org.vellum.panel.enroll.TokenEnvelope
import java.io.IOException
import java.util.concurrent.TimeUnit

@Serializable private data class HelloRequest(
    val mac: String,
    val publicKey: String,
    val display: Model = Model(),
)

@Serializable private data class Model(
    val model: String = "android-panel",
)

@Serializable private data class HelloResponse(
    val status: String,
    val data: HelloReply? = null,
)

@Serializable data class HelloReply(
    val status: String,
    val encryptedToken: TokenEnvelope? = null,
)

data class RoomReply(
    val status: Int,
    val etag: String?,
    val room: RoomState?,
)

data class AssetReply(
    val status: Int,
    val etag: String?,
    val bytes: ByteArray?,
    val mime: String?,
)

/** TLS verification stays enabled; redirects never receive device credentials. */
class PanelClient(
    server: String,
    allowLocalHttpForTest: Boolean = false,
    callTimeoutMs: Long = 20_000,
) {
    private val origin: HttpUrl =
        server.trim().toHttpUrl().also {
            require(
                it.scheme == "https" ||
                    (
                        allowLocalHttpForTest && it.scheme == "http" &&
                            it.host in setOf("localhost", "127.0.0.1", "::1")
                    ),
            ) { "Use an HTTPS server address" }
            require(
                it.username.isEmpty() && it.password.isEmpty() && it.encodedPath == "/" &&
                    it.query == null &&
                    it.fragment == null,
            ) { "Use a server origin without credentials or path" }
        }

    init {
        require(callTimeoutMs in 100..20_000)
    }

    private val http =
        OkHttpClient
            .Builder()
            .followRedirects(false)
            .followSslRedirects(false)
            .connectTimeout(
                10,
                TimeUnit.SECONDS,
            ).readTimeout(15, TimeUnit.SECONDS)
            .callTimeout(callTimeoutMs, TimeUnit.MILLISECONDS)
            .build()

    private fun url(
        path: String,
        mac: String,
    ) = origin
        .newBuilder()
        .encodedPath(path)
        .addQueryParameter("mac", mac)
        .build()

    private fun body(
        response: Response,
        max: Long,
    ): ByteArray {
        val source = response.body?.source() ?: throw IOException("Empty response")
        source.request(max + 1)
        require(source.buffer.size <= max) { "Response exceeds size limit" }
        return source.readByteArray()
    }

    fun hello(identity: DeviceIdentity): HelloReply {
        val payload = PanelJson.encodeToString(HelloRequest(identity.mac, identity.publicKey))
        val request =
            Request
                .Builder()
                .url(origin.newBuilder().encodedPath("/api/v1/ink/hello").build())
                .post(payload.toRequestBody("application/json".toMediaType()))
                .build()
        return http.newCall(request).execute().use {
            if (!it.isSuccessful) throw IOException("Enrollment unavailable")
            val reply =
                PanelJson.decodeFromString<HelloResponse>(
                    body(it, 64 * 1024).toString(Charsets.UTF_8),
                )
            require(reply.status == "ok")
            reply.data ?: throw IOException("Missing enrollment data")
        }
    }

    fun room(
        identity: DeviceIdentity,
        etag: String?,
    ): RoomReply {
        val request =
            Request
                .Builder()
                .url(url("/api/v1/ink/room-state", identity.mac))
                .header("x-device-token", requireNotNull(identity.token))
        if (etag != null) request.header("If-None-Match", etag)
        return http.newCall(request.build()).execute().use {
            RoomReply(
                it.code,
                it.header("ETag"),
                if (it.code ==
                    200
                ) {
                    PanelJson
                        .decodeFromString<RoomState>(
                            body(it, 2 * 1024 * 1024).toString(Charsets.UTF_8),
                        ).validated()
                } else {
                    null
                },
            )
        }
    }

    fun asset(
        identity: DeviceIdentity,
        id: String,
        etag: String?,
    ): AssetReply {
        require(id.matches(Regex("[0-9a-fA-F-]{36}")))
        val request =
            Request
                .Builder()
                .url(
                    url("/api/v1/ink/asset/$id", identity.mac),
                ).header("x-device-token", requireNotNull(identity.token))
        if (etag != null) request.header("If-None-Match", etag)
        return http.newCall(request.build()).execute().use {
            val mime = it.header("Content-Type")?.substringBefore(';')
            if (it.code ==
                200
            ) {
                require(mime in setOf("image/svg+xml", "image/png", "image/jpeg", "image/webp"))
            }
            AssetReply(
                it.code,
                it.header("ETag"),
                if (it.code ==
                    200
                ) {
                    body(it, 4 * 1024 * 1024)
                } else {
                    null
                },
                mime,
            )
        }
    }
}
