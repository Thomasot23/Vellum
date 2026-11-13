// SPDX-License-Identifier: AGPL-3.0-or-later
package org.vellum.panel.data

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

val PanelJson =
    Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

@Serializable
data class RoomState(
    val version: Int,
    val room: Room,
    val locale: String,
    val timezone: String,
    val dateFormat: String,
    val layout: String,
    val state: Occupancy,
    val events: List<RoomEvent>,
    val theme: Theme,
    val brand: Brand,
    val booking: Booking,
    val serverTime: String,
) {
    fun validated(): RoomState =
        apply {
            require(version == 1) { "Unsupported room-state version" }
            java.time.ZoneId.of(timezone)
            java.time.Instant.parse(serverTime)
            state.nextEventStart?.let(java.time.Instant::parse)
            require(layout in setOf("timeline", "stacked", "status-panel", "day-band"))
            val colors =
                listOf(
                    theme.headerBg,
                    theme.headerText,
                    theme.freeBadge,
                    theme.busyBadge,
                    theme.badgeText,
                    theme.background,
                    theme.eventBg,
                    theme.slotText,
                    theme.slotSecondary,
                    theme.footerText,
                )
            require(colors.all { it.matches(Regex("#[0-9A-Fa-f]{6}")) })
            require(events.size <= 10_000)
            events.forEach {
                require(
                    java.time.Instant.parse(it.start) < java.time.Instant.parse(it.end),
                )
            }
        }
}

@Serializable data class Room(
    val name: String,
    val subtitle: String?,
)

@Serializable data class Occupancy(
    val isRoomFree: Boolean,
    val providerUnavailable: Boolean,
    val nextEventStart: String?,
)

@Serializable data class RoomEvent(
    val start: String,
    val end: String,
    val subject: String,
    val organizer: String,
    val isPrivate: Boolean,
)

@Serializable data class Brand(
    val titleLines: List<String>,
    val subtitleLines: List<String>,
    val wordmarkAssetId: String?,
    val markAssetId: String?,
)

@Serializable data class Booking(
    val url: String?,
    val visibility: String,
    val showQr: Boolean,
)

@Serializable data class Theme(
    val name: String,
    val headerBg: String,
    val headerText: String,
    val freeBadge: String,
    val busyBadge: String,
    val badgeText: String,
    val background: String,
    val eventBg: String,
    val slotText: String,
    val slotSecondary: String,
    val footerText: String,
)
