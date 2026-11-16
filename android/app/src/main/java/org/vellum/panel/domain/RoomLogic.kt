// SPDX-License-Identifier: AGPL-3.0-or-later
package org.vellum.panel.domain

import org.vellum.panel.data.RoomEvent
import org.vellum.panel.data.RoomState
import org.vellum.panel.data.Snapshot
import org.vellum.panel.data.Stage
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.pow

data class Moment(
    val current: RoomEvent?,
    val next: RoomEvent?,
    val busy: Boolean,
    val soon: Boolean,
)

data class LaneEvent(
    val event: RoomEvent,
    val lane: Int,
)

object RoomLogic {
    /** One effective state for display, QR and LEDs. Null means unknown. */
    fun liveMoment(
        snapshot: Snapshot,
        now: Instant,
    ): Moment? {
        val room = snapshot.room ?: return null
        if (snapshot.stage != Stage.ACTIVE || room.state.providerUnavailable) return null
        val live = room.live
        if (live == null) {
            if (snapshot.lastSuccess == null) return null
            // Legacy responses provide no source evidence. Never renew from contact-only 304s.
            val evaluated = Instant.parse(room.serverTime)
            val age = Duration.between(evaluated, now)
            if (age.isNegative || age >= Duration.ofSeconds(30)) return null
            return moment(room.events, now, room.state.isRoomFree)
        }
        // A restored cache has no monotonic clock anchor until a new response.
        if (snapshot.offlineSince != null && snapshot.clockAnchor == null) return null
        val evaluated = Instant.parse(live.evaluatedAt)
        val source = Instant.parse(live.sourceFetchedAt)
        if (now < evaluated || now < source || now >= Instant.parse(live.validUntil) ||
            now < Instant.parse(live.windowStart) || now >= Instant.parse(live.windowEnd) ||
            Duration.between(source, now) > Duration.ofSeconds(180)
        ) {
            return null
        }
        if (!live.completeEvents && Duration.between(evaluated, now) >= Duration.ofSeconds(30)) return null
        return moment(room.events, now, if (live.completeEvents) null else room.state.isRoomFree)
    }

    fun moment(
        events: List<RoomEvent>,
        now: Instant,
        isRoomFree: Boolean?,
    ): Moment {
        val sorted = events.sortedBy { Instant.parse(it.start) }
        val current =
            sorted.firstOrNull {
                Instant.parse(it.start) <= now &&
                    Instant.parse(it.end) > now
            }
        val next = sorted.firstOrNull { Instant.parse(it.start) > now }
        val busy = isRoomFree?.not() ?: (current != null)
        val soon =
            !busy && next != null && Duration.between(now, Instant.parse(next.start)).seconds <= 900
        return Moment(current, next, busy, soon)
    }

    fun eventsOn(
        state: RoomState,
        date: LocalDate,
    ): List<RoomEvent> {
        val zone = ZoneId.of(state.timezone)
        val from = date.atStartOfDay(zone).toInstant()
        val until = date.plusDays(1).atStartOfDay(zone).toInstant()
        return state.events
            .filter {
                Instant.parse(it.start) < until && Instant.parse(it.end) > from
            }.sortedBy { it.start }
    }

    fun lanes(events: List<RoomEvent>): List<LaneEvent> {
        val ends = mutableListOf<Instant>()
        return events.sortedBy { it.start }.map { event ->
            val start = Instant.parse(event.start)
            var lane = ends.indexOfFirst { it <= start }
            if (lane ==
                -1
            ) {
                lane = ends.size
                ends.add(Instant.parse(event.end))
            } else {
                ends[lane] =
                    Instant.parse(event.end)
            }
            LaneEvent(event, lane)
        }
    }

    private fun luminance(hex: String): Double {
        val rgb =
            hex.removePrefix("#").chunked(2).map {
                val channel = it.toInt(16) / 255.0
                if (channel <= 0.04045) channel / 12.92 else ((channel + 0.055) / 1.055).pow(2.4)
            }
        return rgb[0] * 0.2126 + rgb[1] * 0.7152 + rgb[2] * 0.0722
    }

    fun readableOn(
        background: String,
        preferred: String,
    ): String {
        val bg = luminance(background)
        val fg = luminance(preferred)
        val contrast = (maxOf(bg, fg) + 0.05) / (minOf(bg, fg) + 0.05)
        if (contrast >= 3.0) return preferred
        return if ((bg + 0.05) / 0.05 >= 1.05 / (bg + 0.05)) "#000000" else "#FFFFFF"
    }
}
