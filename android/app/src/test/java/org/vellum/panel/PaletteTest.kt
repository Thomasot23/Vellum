// SPDX-License-Identifier: AGPL-3.0-or-later
package org.vellum.panel

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.vellum.panel.data.PanelJson
import org.vellum.panel.data.RoomState
import org.vellum.panel.domain.Palette
import org.vellum.panel.domain.Palette.Phase
import java.time.Instant

class PaletteTest {
    private val room =
        PanelJson.decodeFromString<RoomState>(javaClass.getResource("/room-state.json")!!.readText())

    // Neutral stand-ins: a warm accent, a dark booking colour, a white page.
    private val theme =
        room.theme.copy(
            freeBadge = "#FFD200",
            busyBadge = "#505050",
            eventBg = "#505050",
            background = "#FFFFFF",
            badgeText = "#FFFFFF",
            slotText = "#FFFFFF",
            slotSecondary = "#505050",
            footerText = "#000000",
        )
    private val event = room.events.first().copy(start = "2026-11-12T10:00:00Z", end = "2026-11-12T11:00:00Z")

    private fun at(time: String) = Instant.parse("2026-11-12T${time}Z")

    @Test fun tellsPastRunningAndComingBookingsApart() {
        assertEquals(Phase.AHEAD, Palette.phase(event, at("09:59:59")))
        assertEquals(Phase.NOW, Palette.phase(event, at("10:00:00")))
        assertEquals(Phase.NOW, Palette.phase(event, at("10:59:59")))
        assertEquals(Phase.PAST, Palette.phase(event, at("11:00:00")))
    }

    @Test fun pastBookingsFadeInsteadOfTakingTheAccent() {
        val past = Palette.band(theme, Phase.PAST)
        assertEquals("#DCDCDC", past.ground)
        assertNotEquals(theme.freeBadge, past.ground)
        assertEquals(theme.eventBg, past.ink)
        assertNull(past.accentEdge)
    }

    @Test fun onlyTheRunningBookingCarriesTheAccentEdge() {
        assertEquals(theme.freeBadge, Palette.band(theme, Phase.NOW).accentEdge)
        assertNull(Palette.band(theme, Phase.AHEAD).accentEdge)
        assertEquals(theme.eventBg, Palette.band(theme, Phase.NOW).ground)
        assertEquals("#FFFFFF", Palette.band(theme, Phase.AHEAD).ink)
    }

    @Test fun agendaHighlightsNowAndQuietensThePast() {
        val now = Palette.agenda(theme, Phase.NOW)
        assertEquals(theme.busyBadge, now.ground)
        assertEquals("#FFFFFF", now.primary)
        val past = Palette.agenda(theme, Phase.PAST)
        assertNull(past.ground)
        assertEquals("#969696", past.primary)
        val ahead = Palette.agenda(theme, Phase.AHEAD)
        assertNull(ahead.ground)
        assertEquals("#000000", ahead.primary)
        assertEquals("#505050", ahead.secondary)
    }

    @Test fun countsRemainingMinutesUpAndTracksProgress() {
        assertEquals(60, Palette.minutesLeft(at("11:00:00"), at("10:00:00")))
        assertEquals(1, Palette.minutesLeft(at("11:00:00"), at("10:59:30")))
        assertEquals(0, Palette.minutesLeft(at("11:00:00"), at("11:00:00")))
        assertEquals(0, Palette.minutesLeft(at("11:00:00"), at("11:05:00")))
        assertEquals(.5f, Palette.progress(event, at("10:30:00")), .001f)
        assertEquals(0f, Palette.progress(event, at("09:00:00")), .001f)
        assertEquals(1f, Palette.progress(event, at("12:00:00")), .001f)
    }

    @Test fun blendsOpaqueColours() {
        assertEquals("#000000", Palette.blend("#000000", "#FFFFFF", 1f))
        assertEquals("#FFFFFF", Palette.blend("#000000", "#FFFFFF", 0f))
        assertEquals("#808080", Palette.blend("#000000", "#FFFFFF", .5f))
    }
}
