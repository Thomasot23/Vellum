// SPDX-License-Identifier: AGPL-3.0-or-later
package org.vellum.panel

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.vellum.panel.data.PanelInfo
import org.vellum.panel.data.PanelInfoReporter
import org.vellum.panel.data.PanelJson
import java.util.Base64

class PanelInfoTest {
    private val fixture = javaClass.getResource("/panel-info.json")!!.readText()
    private val info = PanelJson.decodeFromString<PanelInfo>(fixture)
    private var clock = 0L
    private var current: PanelInfo? = info
    private val reporter = PanelInfoReporter({ current }, { clock })

    @Test fun encodesTheSharedContractAsUnpaddedBase64Url() {
        val header = info.header()
        assertEquals(true, header.matches(Regex("[A-Za-z0-9_-]+")))
        val decoded = String(Base64.getUrlDecoder().decode(header), Charsets.UTF_8)
        assertEquals(PanelJson.parseToJsonElement(fixture), PanelJson.parseToJsonElement(decoded))
    }

    @Test fun reportsFirstThenOnlyOnChangeOrAfterTheInterval() {
        val first = reporter.next()!!
        reporter.delivered(first)
        assertNull(reporter.next())
        current = info.copy(adbEnabled = true)
        reporter.delivered(reporter.next()!!)
        assertNull(reporter.next())
        clock += PanelInfoReporter.INTERVAL_MS
        assertNotNull(reporter.next())
    }

    @Test fun resendsWhenThePollFailedAndNeverThrows() {
        assertNotNull(reporter.next())
        assertNotNull(reporter.next())
        current = null
        assertNull(reporter.next())
        val failing = PanelInfoReporter({ error("platform unavailable") })
        assertNull(failing.next())
    }

    @Test fun sanitizesVendorStringsToTheServerLimits() {
        assertEquals("unknown", PanelInfo.label("", 64))
        assertEquals("unknown", PanelInfo.label(null, 64))
        assertEquals("Panel", PanelInfo.label(" Panel\u0000é ", 64))
        assertEquals(64, PanelInfo.label("x".repeat(100), 64).length)
        assertNull(PanelInfo.patch(""))
        assertNull(PanelInfo.patch("2025-10"))
        assertEquals("2025-10-05", PanelInfo.patch("2025-10-05"))
        assertEquals("userdebug", PanelInfo.buildType("UserDebug"))
        assertEquals("unknown", PanelInfo.buildType(""))
        assertEquals("release-keys", PanelInfo.buildTags("Release-Keys"))
        assertEquals("test-keys,dev", PanelInfo.buildTags("test-keys, dev;"))
    }
}
