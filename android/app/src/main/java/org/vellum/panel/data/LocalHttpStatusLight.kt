// SPDX-License-Identifier: AGPL-3.0-or-later
package org.vellum.panel.data

import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.vellum.panel.domain.LightSignal
import org.vellum.panel.domain.StatusLight
import java.util.concurrent.TimeUnit

/**
 * Drives panel LEDs through the vendor's device-local HTTP service.
 *
 * The service listens on the panel itself, so this client only ever talks to the
 * loopback address; the manifest permits cleartext for that address and nothing
 * else. The vendor documents the token as a query parameter. It never leaves the
 * device that way, and it is never logged here.
 *
 * Every failure is swallowed: a panel without the service, without a token or
 * with a rejected token simply has no status light.
 *
 * Residual risk: any process on the panel that binds the port before the vendor
 * service would receive the token. Android does not let an app check who owns a
 * loopback socket (`/proc/net` is closed to apps since Android 10), so this rests
 * on the panel being managed: only operator-installed apps, see the checklist.
 */
class LocalHttpStatusLight(
    private val token: () -> String?,
    private val base: HttpUrl = "http://127.0.0.1:3535/".toHttpUrl(),
    private val now: () -> Long = System::currentTimeMillis,
) : StatusLight {
    init {
        // Matches network_security_config.xml, which permits cleartext for this address only.
        require(base.host == "127.0.0.1") { "Loopback only" }
    }

    private val http =
        OkHttpClient
            .Builder()
            .followRedirects(false)
            .followSslRedirects(false)
            .connectTimeout(2, TimeUnit.SECONDS)
            .readTimeout(2, TimeUnit.SECONDS)
            .callTimeout(3, TimeUnit.SECONDS)
            .build()

    /** The last token the service accepted. Kept until a replacement has worked. */
    private var confirmed: String? = null
    private var shown: LightSignal? = null
    private var shownAt = 0L

    /** Whether the LEDs were switched off with [confirmed] after a newer token failed. */
    private var darkenedWithConfirmed = false

    @Synchronized
    override fun show(signal: LightSignal) {
        val current = token()
        val time = now()
        if (current == null) {
            // The secret was removed: switch off once with the last accepted one, then forget it.
            val previous = confirmed ?: return
            if (send(LightSignal.OFF, previous)) {
                confirmed = null
                shown = null
            }
            return
        }
        if (current == confirmed && signal == shown && time - shownAt < REFRESH_MS) return
        if (send(signal, current)) {
            confirmed = current
            shown = signal
            shownAt = time
            darkenedWithConfirmed = false
            return
        }
        shown = null
        // A replacement that the service rejects must not freeze the old state:
        // switch off once with the token that still works.
        val previous = confirmed
        if (previous != null && previous != current && !darkenedWithConfirmed) {
            darkenedWithConfirmed = send(LightSignal.OFF, previous)
        }
    }

    private fun send(
        signal: LightSignal,
        credential: String,
    ): Boolean =
        try {
            val url =
                base
                    .newBuilder()
                    .encodedPath(if (signal == LightSignal.OFF) "/v1/setAllLedsOff" else "/v1/setAllLeds")
                    .apply {
                        when (signal) {
                            LightSignal.FREE -> addQueryParameter("lrgb", FREE_LRGB)
                            LightSignal.BUSY -> addQueryParameter("lrgb", BUSY_LRGB)
                            LightSignal.OFF -> Unit
                        }
                    }.addQueryParameter("token", credential)
                    .build()
            http.newCall(Request.Builder().url(url).build()).execute().use { it.isSuccessful }
        } catch (_: Exception) {
            false
        }

    companion object {
        /** Level, red, green, blue. The level's meaning is to be confirmed on a device. */
        const val FREE_LRGB = "255,0,255,0"
        const val BUSY_LRGB = "255,255,0,0"

        /** Repeats an unchanged signal, in case the panel's service restarted. */
        const val REFRESH_MS = 5 * 60_000L
    }
}
