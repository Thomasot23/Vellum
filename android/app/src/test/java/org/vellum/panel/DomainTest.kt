// SPDX-License-Identifier: AGPL-3.0-or-later
package org.vellum.panel

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.vellum.panel.data.PanelJson
import org.vellum.panel.data.RoomState
import org.vellum.panel.domain.RoomLogic
import org.vellum.panel.kiosk.PanelPolicy
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

class DomainTest {
    @org.junit.Test fun retainsServerReadableLargeTextColors() {
        org.junit.Assert.assertEquals("#888888", RoomLogic.readableOn("#FFFFFF", "#888888"))
    }

    private val state =
        PanelJson.decodeFromString<RoomState>(
            javaClass.getResource("/room-state.json")!!.readText(),
        )
    private val now = Instant.parse("2026-11-12T10:00:00Z")

    @Test fun endBoundaryIsExclusiveAndNextIsSorted() {
        val moment = RoomLogic.moment(state.events.reversed(), now, null)
        assertTrue(moment.busy)
        assertEquals("Planning", moment.current!!.subject)
        assertFalse(
            RoomLogic.moment(state.events, Instant.parse("2026-11-12T11:00:00Z"), null).busy,
        )
    }

    @Test fun privacyHiddenEventsDoNotMakeBusyRoomFree() {
        assertTrue(RoomLogic.moment(emptyList(), now, false).busy)
    }

    @Test fun soonThresholdIsFifteenMinutesIncludingBoundary() {
        assertTrue(RoomLogic.moment(state.events, Instant.parse("2026-11-12T08:45:00Z"), true).soon)
        assertFalse(
            RoomLogic.moment(state.events, Instant.parse("2026-11-12T08:44:59Z"), true).soon,
        )
    }

    @Test fun daySelectionIncludesOvernightBookingsAndExcludesEndBoundary() {
        val overnight =
            state.events.first().copy(
                start = "2026-11-11T23:00:00Z",
                end = "2026-11-12T01:00:00Z",
            )
        val room = state.copy(events = listOf(overnight))
        assertEquals(1, RoomLogic.eventsOn(room, LocalDate.parse("2026-11-12")).size)
        assertTrue(RoomLogic.eventsOn(room, LocalDate.parse("2026-11-13")).isEmpty())
    }

    @Test fun contrastUsesBlackOrWhiteOnlyWhenPreferredIsUnreadable() {
        assertEquals("#FFFFFF", RoomLogic.readableOn("#000000", "#FFFFFF"))
        assertEquals("#000000", RoomLogic.readableOn("#FFFFFF", "#FFFFFF"))
        assertEquals("#123456", RoomLogic.readableOn("#FFFFFF", "#123456"))
    }

    @Test fun overlappingBandBookingsUseDistinctLanes() {
        val event = state.events.first()
        val blocks =
            RoomLogic.lanes(
                listOf(
                    event,
                    event.copy(subject = "Review"),
                    event.copy(start = event.end, end = "2026-11-12T12:00:00Z"),
                ),
            )
        assertEquals(listOf(0, 1, 0), blocks.map { it.lane })
    }

    @Test fun idleTimeoutNightDimmingAndPixelShiftAreBounded() {
        assertFalse(PanelPolicy.returnHome(29999))
        assertTrue(PanelPolicy.returnHome(30000))
        assertEquals(1f, PanelPolicy.brightness(LocalTime.of(12, 0)))
        assertEquals(0.15f, PanelPolicy.brightness(LocalTime.of(23, 0)))
        repeat(1000) { assertTrue(PanelPolicy.pixelOffset(it.toLong()) in -2..2) }
    }
}
