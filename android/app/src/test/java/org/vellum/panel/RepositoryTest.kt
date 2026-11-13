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
import org.vellum.panel.data.PanelClient
import org.vellum.panel.data.PanelJson
import org.vellum.panel.data.PanelRepository
import org.vellum.panel.data.Stage
import org.vellum.panel.enroll.DeviceIdentity
import org.vellum.panel.enroll.IdentityStore
import java.time.Instant

class RepositoryTest {
    @org.junit.Test fun requestTimeoutKeepsCachedState() {
        approved()
        server.enqueue(MockResponse().setBody(fixture))
        repository.tick()
        val fast = PanelRepository({ PanelClient(it, true, 200) }, store, cache) { clock }
        server.enqueue(MockResponse().setBody(fixture).setBodyDelay(2, java.util.concurrent.TimeUnit.SECONDS))
        fast.tick()
        assertNotNull(fast.snapshot.value.room)
        assertNotNull(fast.snapshot.value.offlineSince)
    }

    @org.junit.Test fun rejectsMalformedThemeBeforeItCanCrashTheUi() {
        approved()
        server.enqueue(MockResponse().setBody(fixture))
        repository.tick()
        server.enqueue(MockResponse().setBody(fixture.replace("#000000", "invalid-color")))
        repository.tick()
        assertEquals(
            "#000000",
            repository.snapshot.value.room!!
                .theme.headerBg,
        )
        assertNotNull(repository.snapshot.value.offlineSince)
    }

    private val server = MockWebServer().apply { start() }
    private val fixture = javaClass.getResource("/room-state.json")!!.readText()
    private val store = MemoryIdentity()
    private val cache = MemoryCache()
    private var clock = Instant.parse("2026-11-12T10:00:00Z")
    private val repository =
        PanelRepository({ PanelClient(it, allowLocalHttpForTest = true) }, store, cache) { clock }

    private fun approved() {
        store.save(
            DeviceIdentity.create(server.url("/").toString()).copy(token = "public-test-token"),
        )
    }

    @After fun stop() = server.shutdown()

    @Test fun enforcesHttpsWithoutUrlCredentialsOrPaths() {
        for (url in listOf(
            "http://example.com",
            "https://user:pass@localhost",
            "https://example.com/path",
            "https://example.com?token=x",
        )) {
            assertThrows(IllegalArgumentException::class.java) { PanelClient(url) }
        }
    }

    @Test fun startsEnrollmentAndKeepsIdentityAcrossPendingPolls() {
        repository.configure(server.url("/").toString())
        val id = store.load()!!.mac
        repeat(2) {
            server.enqueue(
                MockResponse().setBody("""{"status":"ok","data":{"status":"pending"}}"""),
            )
            repository.tick()
            assertEquals(Stage.PENDING, repository.snapshot.value.stage)
            assertEquals(id, store.load()!!.mac)
            val body = server.takeRequest().body.readUtf8()
            assertTrue(body.contains("android-panel"))
            assertFalse(body.contains("privateKey"))
        }
    }

    @Test fun rejectsPlaintextTokenEvenIfServerApproves() {
        repository.configure(server.url("/").toString())
        server.enqueue(
            MockResponse().setBody(
                """{"status":"ok","data":{"status":"approved","token":"public-test-token"}}""",
            ),
        )
        repository.tick()
        assertNull(store.load()!!.token)
        assertEquals(Stage.ERROR, repository.snapshot.value.stage)
    }

    @Test fun decryptsApprovalAndImmediatelyPollsRoom() {
        val scalar =
            ("77076d0a7318a57d3c16c17251b26645df" + "4c2f87ebc0992ab177fba51db92c2a")
                .chunked(
                    2,
                ).map {
                    it.toInt(16).toByte()
                }.toByteArray()
        store.save(
            DeviceIdentity
                .create(
                    server.url("/").toString(),
                ).copy(
                    privateKey =
                        java.util.Base64
                            .getEncoder()
                            .encodeToString(scalar),
                ),
        )
        val envelope =
            PanelJson
                .parseToJsonElement(
                    javaClass.getResource("/token-envelope.json")!!.readText(),
                ).toString()
        server.enqueue(
            MockResponse().setBody(
                """{"status":"ok","data":{"status":"approved","encryptedToken":$envelope}}""",
            ),
        )
        server.enqueue(MockResponse().setBody(fixture).setHeader("ETag", "W/\"one\""))
        repository.tick()
        assertEquals("public-interoperability-test", store.load()!!.token)
        assertEquals(Stage.ACTIVE, repository.snapshot.value.stage)
    }

    private fun enrolledWithInteroperabilityKey() {
        val scalar =
            ("77076d0a7318a57d3c16c17251b26645df" + "4c2f87ebc0992ab177fba51db92c2a")
                .chunked(2)
                .map { it.toInt(16).toByte() }
                .toByteArray()
        store.save(
            DeviceIdentity.create(server.url("/").toString()).copy(
                privateKey =
                    java.util.Base64
                        .getEncoder()
                        .encodeToString(scalar),
                token = "public-test-token",
            ),
        )
    }

    private fun lightSecret(revision: String): MockResponse {
        val envelope =
            PanelJson
                .parseToJsonElement(javaClass.getResource("/status-light-envelope.json")!!.readText())
                .toString()
        return MockResponse().setBody(
            """{"version":1,"revision":"$revision","encryptedToken":$envelope}""",
        )
    }

    @Test fun fetchesStatusLightSecretOnlyWhenItsRevisionChanges() {
        enrolledWithInteroperabilityKey()
        val revision = "0123456789abcdef"
        server.enqueue(MockResponse().setBody(fixture).setHeader("X-Status-Light-Revision", revision))
        server.enqueue(lightSecret(revision))
        repository.tick()
        assertEquals("public-status-light-test", repository.statusLightToken())
        assertEquals(revision, store.load()!!.lightRevision)
        assertEquals(Stage.ACTIVE, repository.snapshot.value.stage)
        server.takeRequest()
        val fetch = server.takeRequest()
        assertTrue(fetch.path!!.startsWith("/api/v1/ink/status-light?mac="))
        assertEquals("public-test-token", fetch.getHeader("x-device-token"))
        assertFalse(fetch.path!!.contains("public-test-token"))
        server.enqueue(MockResponse().setResponseCode(304).setHeader("X-Status-Light-Revision", revision))
        repository.tick()
        assertEquals(3, server.requestCount)
    }

    @Test fun clearsStatusLightSecretWhenServerHoldsNone() {
        enrolledWithInteroperabilityKey()
        store.save(store.load()!!.copy(lightToken = "old-secret", lightRevision = "0123456789abcdef"))
        server.enqueue(MockResponse().setBody(fixture).setHeader("X-Status-Light-Revision", "none"))
        repository.tick()
        assertNull(repository.statusLightToken())
        assertEquals(1, server.requestCount)
        store.save(store.load()!!.copy(lightToken = "old-secret", lightRevision = "0123456789abcdef"))
        server.enqueue(MockResponse().setResponseCode(204).setHeader("X-Status-Light-Revision", "fedcba9876543210"))
        server.enqueue(MockResponse().setResponseCode(204))
        repository.tick()
        assertNull(repository.statusLightToken())
        assertEquals(Stage.UNASSIGNED, repository.snapshot.value.stage)
    }

    @Test fun keepsRoomAndSecretWhenStatusLightAnswerIsMissingOrUnusable() {
        enrolledWithInteroperabilityKey()
        store.save(store.load()!!.copy(lightToken = "old-secret", lightRevision = "0123456789abcdef"))
        val repository = PanelRepository({ PanelClient(it, allowLocalHttpForTest = true) }, store, cache) { clock }
        server.enqueue(MockResponse().setBody(fixture))
        repository.tick()
        assertEquals("old-secret", repository.statusLightToken())
        for (bad in listOf(
            MockResponse().setResponseCode(500),
            MockResponse().setBody("""{"version":1,"revision":"fedcba9876543210","token":"plaintext"}"""),
            lightSecret("not-a-revision"),
        )) {
            server.enqueue(MockResponse().setBody(fixture).setHeader("X-Status-Light-Revision", "fedcba9876543210"))
            server.enqueue(bad)
            repository.tick()
            assertEquals("old-secret", repository.statusLightToken())
            assertEquals("0123456789abcdef", store.load()!!.lightRevision)
            assertEquals(Stage.ACTIVE, repository.snapshot.value.stage)
            assertNull(repository.snapshot.value.offlineSince)
        }
        server.enqueue(MockResponse().setBody(fixture).setHeader("X-Status-Light-Revision", "<script>"))
        repository.tick()
        assertEquals("old-secret", repository.statusLightToken())
    }

    @Test fun cachesRoomAndRefreshesContactTimeOn304() {
        approved()
        server.enqueue(MockResponse().setBody(fixture).setHeader("ETag", "W/\"one\""))
        repository.tick()
        assertEquals(
            "Meeting Room",
            repository.snapshot.value.room!!
                .room.name,
        )
        val first = server.takeRequest()
        assertEquals("public-test-token", first.getHeader("x-device-token"))
        assertFalse(first.path!!.contains("public-test-token"))
        clock = clock.plusSeconds(60)
        server.enqueue(MockResponse().setResponseCode(304))
        repository.tick()
        assertEquals("W/\"one\"", server.takeRequest().getHeader("If-None-Match"))
        assertEquals(clock.toString(), cache.value!!.lastSuccess)
    }

    @Test fun keepsLastRoomWhenServerFailsAndClearsItOn401() {
        approved()
        server.enqueue(MockResponse().setBody(fixture))
        repository.tick()
        server.enqueue(MockResponse().setResponseCode(503))
        repository.tick()
        assertNotNull(repository.snapshot.value.room)
        assertNotNull(repository.snapshot.value.offlineSince)
        server.enqueue(MockResponse().setResponseCode(401))
        repository.tick()
        assertNull(store.load()!!.token)
        assertNull(repository.statusLightToken())
        assertNull(repository.snapshot.value.room)
        assertNull(cache.value)
        assertEquals(Stage.PENDING, repository.snapshot.value.stage)
    }

    @Test fun clearsPreviousAssignmentOn204And422() {
        approved()
        for (status in listOf(204, 422)) {
            server.enqueue(MockResponse().setBody(fixture))
            repository.tick()
            server.enqueue(MockResponse().setResponseCode(status))
            repository.tick()
            assertNull(repository.snapshot.value.room)
            assertEquals(
                if (status ==
                    204
                ) {
                    Stage.UNASSIGNED
                } else {
                    Stage.UNSUPPORTED
                },
                repository.snapshot.value.stage,
            )
        }
    }

    @Test fun doesNotForwardTokensOnRedirects() {
        approved()
        server.enqueue(
            MockResponse().setResponseCode(302).setHeader("Location", "https://example.com/"),
        )
        repository.tick()
        assertEquals(Stage.ERROR, repository.snapshot.value.stage)
        assertEquals(1, server.requestCount)
    }

    @Test fun discardsUnsupportedContractWithoutReplacingKnownGoodCache() {
        approved()
        server.enqueue(MockResponse().setBody(fixture))
        repository.tick()
        server.enqueue(MockResponse().setBody(fixture.replace("\"version\": 1", "\"version\": 99")))
        repository.tick()
        assertEquals(
            1,
            repository.snapshot.value.room!!
                .version,
        )
        assertNotNull(repository.snapshot.value.offlineSince)
    }

    @Test fun restoresOnlyCacheBoundToTheSameServerAndDevice() {
        approved()
        server.enqueue(MockResponse().setBody(fixture))
        repository.tick()
        store.save(store.load()!!.copy(lightToken = "stored-secret", lightRevision = "0123456789abcdef"))
        val restored = PanelRepository({ PanelClient(it, true) }, store, cache) { clock }
        assertNotNull(restored.snapshot.value.room)
        assertEquals("stored-secret", restored.statusLightToken())
        store.save(
            DeviceIdentity.create(server.url("/").toString()).copy(token = "public-test-token"),
        )
        val other = PanelRepository({ PanelClient(it, true) }, store, cache) { clock }
        assertNull(other.snapshot.value.room)
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

    private class MemoryCache : CacheStore {
        var value: CachedRoom? = null

        override fun load() = value

        override fun save(room: CachedRoom) {
            value = room
        }

        override fun clear() {
            value = null
        }
    }
}
