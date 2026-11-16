// SPDX-License-Identifier: AGPL-3.0-or-later
package org.vellum.panel.domain

import org.vellum.panel.data.RoomEvent
import org.vellum.panel.data.Theme
import java.time.Duration
import java.time.Instant
import kotlin.math.roundToInt

/**
 * How bookings are coloured over time on a full-colour panel.
 *
 * The accent (`freeBadge`) says exactly one thing: free, or "this is now". A
 * booking that is over therefore fades to a quiet tint of the booking colour
 * instead of taking the accent, which would read as a highlight. E-paper gets
 * the same effect from its greyscale palette; a colour panel has to ask for it.
 */
object Palette {
    enum class Phase { PAST, NOW, AHEAD }

    data class Fill(
        val ground: String,
        val ink: String,
        /** A strip of this colour marks the booking that is running. */
        val accentEdge: String? = null,
    )

    data class RowInk(
        /** Null leaves the row on the page background. */
        val ground: String?,
        val primary: String,
        val secondary: String,
    )

    fun phase(
        event: RoomEvent,
        now: Instant,
    ): Phase =
        when {
            Instant.parse(event.end) <= now -> Phase.PAST
            Instant.parse(event.start) <= now -> Phase.NOW
            else -> Phase.AHEAD
        }

    /** `share` of `foreground` laid over `background`, as an opaque colour. */
    fun blend(
        foreground: String,
        background: String,
        share: Float,
    ): String {
        fun channels(hex: String) = hex.removePrefix("#").chunked(2).map { it.toInt(16) }
        val top = channels(foreground)
        val under = channels(background)
        return "#" +
            (0..2).joinToString("") {
                "%02X".format((top[it] * share + under[it] * (1 - share)).roundToInt().coerceIn(0, 255))
            }
    }

    /** A block in the day band. */
    fun band(
        theme: Theme,
        phase: Phase,
    ): Fill =
        if (phase == Phase.PAST) {
            val ground = blend(theme.eventBg, theme.background, .2f)
            Fill(ground, RoomLogic.readableOn(ground, theme.eventBg))
        } else {
            Fill(
                theme.eventBg,
                RoomLogic.readableOn(theme.eventBg, theme.slotText),
                if (phase == Phase.NOW) theme.freeBadge else null,
            )
        }

    /** A line in the day's agenda. */
    fun agenda(
        theme: Theme,
        phase: Phase,
    ): RowInk =
        when (phase) {
            Phase.PAST -> blend(theme.slotSecondary, theme.background, .6f).let { RowInk(null, it, it) }
            Phase.NOW -> RoomLogic.readableOn(theme.busyBadge, theme.badgeText).let { RowInk(theme.busyBadge, it, it) }
            Phase.AHEAD -> RowInk(null, theme.footerText, theme.slotSecondary)
        }

    /** Whole minutes still to go, rounded up, never negative. */
    fun minutesLeft(
        until: Instant,
        now: Instant,
    ): Long = ((Duration.between(now, until).seconds + 59) / 60).coerceAtLeast(0)

    /** How far a running booking has come, 0 to 1. */
    fun progress(
        event: RoomEvent,
        now: Instant,
    ): Float {
        val start = Instant.parse(event.start)
        val total = Duration.between(start, Instant.parse(event.end)).seconds
        if (total <= 0) return 1f
        return (Duration.between(start, now).seconds.toFloat() / total).coerceIn(0f, 1f)
    }
}
