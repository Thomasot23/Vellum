// SPDX-License-Identifier: AGPL-3.0-or-later
package org.vellum.panel

import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test
import org.vellum.panel.data.LocalHttpStatusLight
import org.vellum.panel.data.PanelJson
import org.vellum.panel.data.RoomState
import org.vellum.panel.data.Snapshot
import org.vellum.panel.data.Stage
import org.vellum.panel.domain.LightPolicy
import org.vellum.panel.domain.LightSignal
import org.vellum.panel.domain.NoStatusLight
import java.time.Instant

class StatusLightTest {
    private val server = MockWebServer().apply { start(java.net.InetAddress.getByName("127.0.0.1"), 0) }
    private val room =
        PanelJson.decodeFromString<RoomState>(
            javaClass.getResource("/room-state.json")!!.readText(),
        )
    private val now = Instant.parse("2026-11-12T10:00:00Z")
    private var token: String? = "synthetic-vendor-token"
    private var clock = 0L
    private val light =
        LocalHttpStatusLight({ token }, "http://127.0.0.1:${server.port}/".toHttpUrl(), { clock })

    @After fun stop() = server.shutdown()

    private fun active(
        free: Boolean,
        last: Instant? = now,
    ) = Snapshot(
        Stage.ACTIVE,
        room.copy(state = room.state.copy(isRoomFree = free, providerUnavailable = false)),
        last,
    )

    @Test fun mirrorsFreeAndBusy() {
        assertEquals(LightSignal.FREE, LightPolicy.signal(active(true), now, false))
        assertEquals(LightSignal.BUSY, LightPolicy.signal(active(false), now, false))
    }

    @Test fun staysOffWheneverTheStateCannotBeVouchedFor() {
        val busy = active(false)
        assertEquals(LightSignal.OFF, LightPolicy.signal(busy, now, true))
        assertEquals(LightSignal.OFF, LightPolicy.signal(Snapshot(), now, false))
        assertEquals(LightSignal.OFF, LightPolicy.signal(busy.copy(stage = Stage.ERROR), now, false))
        assertEquals(LightSignal.OFF, LightPolicy.signal(active(true, null), now, false))
        val unreachable =
            busy.copy(room = busy.room!!.copy(state = busy.room!!.state.copy(providerUnavailable = true)))
        assertEquals(LightSignal.OFF, LightPolicy.signal(unreachable, now, false))
    }

    @Test fun goesDarkOnceTheLastAnswerIsStale() {
        val free = active(true)
        assertEquals(LightSignal.FREE, LightPolicy.signal(free, now.plusSeconds(180), false))
        assertEquals(LightSignal.OFF, LightPolicy.signal(free, now.plusSeconds(181), false))
        // Clock jumped back: the age of the state is unknown.
        assertEquals(LightSignal.OFF, LightPolicy.signal(free, now.minusSeconds(1), false))
    }

    @Test fun sendsDocumentedCommandsToTheLocalService() {
        repeat(3) { server.enqueue(MockResponse()) }
        light.show(LightSignal.FREE)
        light.show(LightSignal.BUSY)
        light.show(LightSignal.OFF)
        val free = server.takeRequest().requestUrl!!
        assertEquals("/v1/setAllLeds", free.encodedPath)
        assertEquals("255,0,255,0", free.queryParameter("lrgb"))
        assertEquals("synthetic-vendor-token", free.queryParameter("token"))
        assertEquals("255,255,0,0", server.takeRequest().requestUrl!!.queryParameter("lrgb"))
        val off = server.takeRequest().requestUrl!!
        assertEquals("/v1/setAllLedsOff", off.encodedPath)
        assertNull(off.queryParameter("lrgb"))
    }

    @Test fun repeatsAnUnchangedSignalOnlyAfterTheRefreshInterval() {
        repeat(2) { server.enqueue(MockResponse()) }
        light.show(LightSignal.FREE)
        light.show(LightSignal.FREE)
        assertEquals(1, server.requestCount)
        clock += LocalHttpStatusLight.REFRESH_MS
        light.show(LightSignal.FREE)
        assertEquals(2, server.requestCount)
    }

    @Test fun doesNothingWithoutASecret() {
        token = null
        light.show(LightSignal.BUSY)
        assertEquals(0, server.requestCount)
    }

    @Test fun switchesOffOnceWhenTheSecretIsRemoved() {
        repeat(2) { server.enqueue(MockResponse()) }
        light.show(LightSignal.BUSY)
        token = null
        light.show(LightSignal.BUSY)
        light.show(LightSignal.BUSY)
        assertEquals(2, server.requestCount)
        server.takeRequest()
        val off = server.takeRequest().requestUrl!!
        assertEquals("/v1/setAllLedsOff", off.encodedPath)
        assertEquals("synthetic-vendor-token", off.queryParameter("token"))
    }

    @Test fun retriesAfterRejectionOrFailureWithoutThrowing() {
        server.enqueue(MockResponse().setResponseCode(401))
        server.enqueue(MockResponse().setResponseCode(500))
        server.enqueue(MockResponse())
        repeat(3) { light.show(LightSignal.FREE) }
        assertEquals(3, server.requestCount)
        light.show(LightSignal.FREE)
        assertEquals(3, server.requestCount)
        server.shutdown()
        light.show(LightSignal.BUSY)
    }

    @Test fun refusesAnythingButTheLoopbackAddress() {
        for (host in listOf("example.com", "localhost")) {
            assertThrows(IllegalArgumentException::class.java) {
                LocalHttpStatusLight(
                    { token },
                    okhttp3.HttpUrl
                        .Builder()
                        .scheme("http")
                        .host(host)
                        .build(),
                )
            }
        }
        NoStatusLight.show(LightSignal.BUSY)
    }

    @Test fun keepsTheAcceptedTokenUntilAReplacementWorks() {
        server.enqueue(MockResponse()) // FREE with the original token
        light.show(LightSignal.FREE)
        token = "rejected-replacement"
        server.enqueue(MockResponse().setResponseCode(401)) // BUSY with the replacement
        server.enqueue(MockResponse()) // OFF with the accepted token
        light.show(LightSignal.BUSY)
        server.enqueue(MockResponse().setResponseCode(401)) // retried, no second OFF
        light.show(LightSignal.BUSY)
        assertEquals(4, server.requestCount)
        server.takeRequest()
        assertEquals("rejected-replacement", server.takeRequest().requestUrl!!.queryParameter("token"))
        val off = server.takeRequest().requestUrl!!
        assertEquals("/v1/setAllLedsOff", off.encodedPath)
        assertEquals("synthetic-vendor-token", off.queryParameter("token"))
        token = "working-replacement"
        server.enqueue(MockResponse())
        light.show(LightSignal.BUSY)
        server.takeRequest()
        assertEquals("working-replacement", server.takeRequest().requestUrl!!.queryParameter("token"))
    }
}
