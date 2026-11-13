// SPDX-License-Identifier: AGPL-3.0-or-later
package org.vellum.panel

import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.vellum.panel.data.AssetCache
import org.vellum.panel.data.PanelClient
import org.vellum.panel.data.PanelJson
import org.vellum.panel.data.RoomState
import org.vellum.panel.enroll.DeviceIdentity
import java.nio.file.Files

class AssetCacheTest {
    private val server = MockWebServer().apply { start() }
    private val identity = DeviceIdentity.create(server.url("/").toString()).copy(token = "public-test-token")
    private val client = PanelClient(identity.server, true)
    private val directory = Files.createTempDirectory("panel-assets").toFile()
    private val cache = AssetCache(directory)
    private val id = "123e4567-e89b-42d3-a456-426614174000"
    private val fixture = PanelJson.decodeFromString<RoomState>(javaClass.getResource("/room-state.json")!!.readText())
    private val room = fixture.copy(brand = fixture.brand.copy(markAssetId = id))

    @After fun stop() {
        server.shutdown()
        directory.deleteRecursively()
    }

    @Test fun downloadsAndRevalidatesOnlyCurrentBranding() {
        server.enqueue(
            MockResponse().setHeader("Content-Type", "image/svg+xml").setHeader("ETag", "one").setBody("<svg/>"),
        )
        val file = cache.sync(client, identity, room).getValue(id)
        assertEquals("<svg/>", file.readText())
        assertEquals("public-test-token", server.takeRequest().getHeader("x-device-token"))
        server.enqueue(MockResponse().setResponseCode(304))
        assertEquals(file, cache.sync(client, identity, room).getValue(id))
        assertEquals("one", server.takeRequest().getHeader("If-None-Match"))
        assertTrue(cache.sync(client, identity, fixture).isEmpty())
        assertFalse(file.exists())
    }

    @Test fun revokedAssetIsRemovedAndUnsafeMimeNeverBecomesAnImage() {
        server.enqueue(MockResponse().setHeader("Content-Type", "image/png").setBody("public test image bytes"))
        val file = cache.sync(client, identity, room).getValue(id)
        server.enqueue(MockResponse().setResponseCode(404))
        assertTrue(cache.sync(client, identity, room).isEmpty())
        assertFalse(file.exists())
        server.enqueue(MockResponse().setHeader("Content-Type", "text/html").setBody("not an image"))
        assertTrue(cache.sync(client, identity, room).isEmpty())
    }

    @Test fun refusesPathTraversalAssetIdentifiers() {
        val bad = room.copy(brand = room.brand.copy(markAssetId = "../../other"))
        assertTrue(cache.sync(client, identity, bad).isEmpty())
        assertEquals(0, server.requestCount)
    }
}
