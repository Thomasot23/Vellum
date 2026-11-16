// SPDX-License-Identifier: AGPL-3.0-or-later
package org.vellum.panel

import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.vellum.panel.data.CacheStore
import org.vellum.panel.data.CachedRoom
import org.vellum.panel.data.FileSequenceStore
import org.vellum.panel.data.PanelClient
import org.vellum.panel.data.PanelLog
import org.vellum.panel.data.PanelRepository
import org.vellum.panel.data.SequenceStore
import org.vellum.panel.enroll.DeviceIdentity
import org.vellum.panel.enroll.IdentityStore
import java.nio.file.Files
import java.time.Instant

class PanelLogTest {
    private var clock = Instant.parse("2026-11-13T12:00:00Z")
    private var next = 0L
    private val sequence =
        object : SequenceStore {
            override fun reserve() = ++next
        }
    private val log = PanelLog(sequence, { clock }, capacity = 3)
    private val server = MockWebServer().apply { start() }

    @After fun stop() = server.shutdown()

    @Test fun foldsRepeatsAndKeepsOnlyTheNewestEntries() {
        repeat(5) { log.warn("ROOM_POLL_FAILED", "IOException") }
        log.error("LIGHT_COMMAND_FAILED")
        log.warn("ROOM_HTTP", "503")
        log.warn("TOKEN_REJECTED")
        val batch = log.nextBatch()!!
        assertEquals(
            listOf(
                "2026-11-13T12:00:00Z E LIGHT_COMMAND_FAILED",
                "2026-11-13T12:00:00Z W ROOM_HTTP 503",
                "2026-11-13T12:00:00Z W TOKEN_REJECTED",
            ),
            batch.lines.lines(),
        )
        log.attempted(batch, true)
        log.warn("ROOM_POLL_FAILED", "IOException")
        log.warn("ROOM_POLL_FAILED", "IOException")
        clock = clock.plusMillis(PanelLog.INTERVAL_MS)
        assertTrue(log.nextBatch()!!.lines.endsWith("ROOM_POLL_FAILED IOException x2"))
    }

    @Test fun cannotCarrySecretsOrFreeText() {
        assertThrows(IllegalArgumentException::class.java) { log.warn("token=abc") }
        log.warn("ROOM_POLL_FAILED", "https://server.example/?token=secret value")
        val line = log.nextBatch()!!.lines
        assertFalse(line.contains("/"))
        assertFalse(line.contains("="))
        assertFalse(line.contains("secret value"))
    }

    @Test fun keepsTheSequenceUntilAcknowledgedAndWaitsBetweenAttempts() {
        log.warn("TOKEN_REJECTED")
        val first = log.nextBatch()!!
        log.attempted(first, false)
        assertEquals(first, log.nextBatch())
        log.attempted(first, true)
        assertNull(log.nextBatch())
        log.warn("ROOM_HTTP", "500")
        assertNull(log.nextBatch())
        clock = clock.plusMillis(PanelLog.INTERVAL_MS)
        val second = log.nextBatch()!!
        assertEquals(first.seq + 1, second.seq)
    }

    @Test fun reservesPersistedSequenceNumbersAcrossRestarts() {
        val file = Files.createTempDirectory("panel-log").resolve("log.seq").toFile()
        assertEquals(1, FileSequenceStore(file).reserve())
        assertEquals(2, FileSequenceStore(file).reserve())
        file.writeText("garbage")
        assertEquals(1, FileSequenceStore(file).reserve())
    }

    @Test fun uploadsAfterASuccessfulPollWithTheDeviceToken() {
        val identities = MemoryIdentity()
        identities.save(DeviceIdentity.create(server.url("/").toString()).copy(token = "public-test-token"))
        val repository =
            PanelRepository({ PanelClient(it, allowLocalHttpForTest = true) }, identities, NoCache, log = log) { clock }
        server.enqueue(MockResponse().setResponseCode(503))
        repository.tick()
        server.takeRequest()
        server.enqueue(MockResponse().setResponseCode(204))
        server.enqueue(MockResponse().setBody("""{"status":"ok","data":{"seq":1}}"""))
        repository.tick()
        server.takeRequest()
        val upload = server.takeRequest()
        assertEquals("/api/v1/ink/logs", upload.path)
        assertEquals("public-test-token", upload.getHeader("x-device-token"))
        val body = upload.body.readUtf8()
        assertTrue(body.contains("ROOM_HTTP 503"))
        assertFalse(body.contains("public-test-token"))
        assertNotNull(Regex("\"seq\":1").find(body))
        assertNull(log.nextBatch())
    }

    private class MemoryIdentity : IdentityStore {
        var value: DeviceIdentity? = null

        override fun load() = value

        override fun save(identity: DeviceIdentity) {
            value = identity
        }

        override fun clear() {
            value = null
        }
    }

    private object NoCache : CacheStore {
        override fun load(): CachedRoom? = null

        override fun save(room: CachedRoom) = Unit

        override fun clear() = Unit
    }
}
