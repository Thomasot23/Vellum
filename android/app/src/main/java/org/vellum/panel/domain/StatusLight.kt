// SPDX-License-Identifier: AGPL-3.0-or-later
package org.vellum.panel.domain

import org.vellum.panel.data.Snapshot
import org.vellum.panel.data.Stage
import java.time.Duration
import java.time.Instant

/** What the panel's LEDs say. Fixed signals, not theme colours. */
enum class LightSignal { OFF, FREE, BUSY }

/** A panel's LEDs. Implementations must never throw and never block for long. */
interface StatusLight {
    fun show(signal: LightSignal)
}

/** Panels without LEDs, or without a way to reach them. */
object NoStatusLight : StatusLight {
    override fun show(signal: LightSignal) = Unit
}

object LightPolicy {
    /** Three missed polls. Beyond that the panel no longer knows the room's state. */
    val staleAfter: Duration = Duration.ofSeconds(180)

    /**
     * Off whenever the panel cannot vouch for the state: not showing a room, the
     * calendar unreachable, the last answer too old, or the screen dimmed for the
     * night. A dark light is honest; a green one over a stale state is not.
     */
    fun signal(
        snapshot: Snapshot,
        now: Instant,
        dimmed: Boolean,
    ): LightSignal {
        val room = snapshot.room
        val last = snapshot.lastSuccess
        if (snapshot.stage != Stage.ACTIVE || room == null || last == null || dimmed) {
            return LightSignal.OFF
        }
        if (room.state.providerUnavailable) return LightSignal.OFF
        val age = Duration.between(last, now)
        // A negative age means the wall clock jumped back: the age is unknown.
        if (age.isNegative || age > staleAfter) return LightSignal.OFF
        val moment = RoomLogic.moment(room.events, now, room.state.isRoomFree)
        return if (moment.busy) LightSignal.BUSY else LightSignal.FREE
    }
}
