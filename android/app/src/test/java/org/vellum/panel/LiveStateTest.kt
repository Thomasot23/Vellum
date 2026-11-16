// SPDX-License-Identifier: AGPL-3.0-or-later
package org.vellum.panel

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.vellum.panel.data.LiveEvidence
import org.vellum.panel.data.PanelJson
import org.vellum.panel.data.RoomEvent
import org.vellum.panel.data.RoomState
import org.vellum.panel.data.Snapshot
import org.vellum.panel.data.Stage
import org.vellum.panel.domain.LightPolicy
import org.vellum.panel.domain.LightSignal
import org.vellum.panel.domain.RoomLogic
import java.time.Instant

class LiveStateTest {
    private val now = Instant.parse("2026-11-12T10:00:00Z")
    private val room = PanelJson.decodeFromString<RoomState>(javaClass.getResource("/room-state.json")!!.readText())

    private fun live(complete: Boolean = true) =
        LiveEvidence(
            complete,
            now.toString(),
            now.toString(),
            now.plusSeconds(120).toString(),
            now.minusSeconds(3600).toString(),
            now.plusSeconds(3600).toString(),
        )

    private fun event(
        start: Long,
        end: Long,
    ) = RoomEvent(now.plusSeconds(start).toString(), now.plusSeconds(end).toString(), "Booking", "", false)

    private fun snapshot(
        free: Boolean,
        events: List<RoomEvent> = emptyList(),
        evidence: LiveEvidence? = live(),
    ) = Snapshot(
        Stage.ACTIVE,
        room.copy(
            events = events,
            state = room.state.copy(isRoomFree = free, providerUnavailable = false),
            live = evidence,
        ),
        now,
    )

    @Test fun startsAndEndsLocallyWithoutPoll() {
        val s = snapshot(true, listOf(event(10, 30)))
        assertFalse(RoomLogic.liveMoment(s, now.plusSeconds(9))!!.busy)
        assertTrue(RoomLogic.liveMoment(s, now.plusSeconds(10))!!.busy)
        assertFalse(RoomLogic.liveMoment(s, now.plusSeconds(30))!!.busy)
    }

    @Test fun overlapsStayBusyUntilAllHaveEnded() {
        val s = snapshot(false, listOf(event(-10, 30), event(20, 60)))
        assertTrue(RoomLogic.liveMoment(s, now.plusSeconds(40))!!.busy)
        assertFalse(RoomLogic.liveMoment(s, now.plusSeconds(60))!!.busy)
    }

    @Test fun completeEmptyListIsFree() = assertFalse(RoomLogic.liveMoment(snapshot(false), now)!!.busy)

    @Test fun hiddenEmptyListKeepsAuthorityThenExpires() {
        val s = snapshot(false, evidence = live(false).copy(validUntil = now.plusSeconds(30).toString()))
        assertTrue(RoomLogic.liveMoment(s, now.plusSeconds(29))!!.busy)
        assertNull(RoomLogic.liveMoment(s, now.plusSeconds(30)))
    }

    @Test fun staleSourceCannotBeRenewedByRecentContact() =
        assertNull(
            RoomLogic.liveMoment(
                snapshot(true, evidence = live().copy(sourceFetchedAt = now.minusSeconds(181).toString())),
                now,
            ),
        )

    @Test fun expiryAndWindowAreExclusive() {
        assertNull(RoomLogic.liveMoment(snapshot(true), now.plusSeconds(120)))
        assertNull(RoomLogic.liveMoment(snapshot(true, evidence = live().copy(windowEnd = now.toString())), now))
    }

    @Test fun futureEvidenceIsUnknown() = assertNull(RoomLogic.liveMoment(snapshot(true), now.minusSeconds(1)))

    @Test fun unavailableAndInactiveAreUnknown() {
        val s = snapshot(true)
        assertNull(
            RoomLogic.liveMoment(
                s.copy(room = s.room!!.copy(state = s.room!!.state.copy(providerUnavailable = true))),
                now,
            ),
        )
        assertNull(RoomLogic.liveMoment(s.copy(stage = Stage.PENDING), now))
    }

    @Test fun legacyStateNeverInfersFromEmptyEvents() {
        val s = snapshot(false, evidence = null)
        assertTrue(RoomLogic.liveMoment(s, now)!!.busy)
        assertNull(RoomLogic.liveMoment(s, now.plusSeconds(31)))
        assertNull(RoomLogic.liveMoment(s, now.minusSeconds(1)))
    }

    @Test fun ledsFollowLocalTransitionsAndUnknown() {
        val s = snapshot(true, listOf(event(10, 30)))
        assertEquals(LightSignal.FREE, LightPolicy.signal(s, now, false))
        assertEquals(LightSignal.BUSY, LightPolicy.signal(s, now.plusSeconds(10), false))
        assertEquals(LightSignal.OFF, LightPolicy.signal(s, now.plusSeconds(120), false))
        assertEquals(LightSignal.OFF, LightPolicy.signal(s, now, true))
    }
}
