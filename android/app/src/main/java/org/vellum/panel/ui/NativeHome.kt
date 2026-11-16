// SPDX-License-Identifier: AGPL-3.0-or-later
package org.vellum.panel.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import org.vellum.panel.R
import org.vellum.panel.data.RoomState
import org.vellum.panel.data.Snapshot
import org.vellum.panel.domain.BoundaryLabels
import org.vellum.panel.domain.Moment
import org.vellum.panel.domain.RoomLogic
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale
import kotlin.math.max
import kotlin.math.min

private fun ink(hex: String) = Color(android.graphics.Color.parseColor(hex))

private fun size(
    value: Float,
    u: Float,
) = max(10, (value * u).toInt())

@Composable
internal fun NativeHome(
    snapshot: Snapshot,
    now: Instant,
    assets: Map<String, File>,
    modifier: Modifier,
) {
    val state = snapshot.room ?: return
    val moment = RoomLogic.liveMoment(snapshot, now)
    BoxWithConstraints(modifier.background(ink(state.theme.background))) {
        val wide = maxWidth > maxHeight
        val u = min(maxWidth.value, maxHeight.value) / 480f
        if (state.layout == "day-band") {
            NativeBand(snapshot, now, assets, moment, u, wide)
        } else if (wide) {
            Row(Modifier.fillMaxSize()) {
                StatusGround(snapshot, now, assets, moment, u, Modifier.weight(.4f).fillMaxHeight())
                Column(Modifier.weight(.6f).fillMaxHeight().padding((27 * u).dp)) {
                    RoomHeading(state, now, snapshot, u)
                    Spacer(Modifier.height((35 * u).dp))
                    TodayAgenda(state, now, u, Modifier.weight(1f).fillMaxWidth())
                    Spacer(Modifier.height((12 * u).dp))
                    BookingCode(snapshot, now)
                }
            }
        } else {
            Column(Modifier.fillMaxSize()) {
                StatusGround(snapshot, now, assets, moment, u, Modifier.fillMaxWidth().weight(.42f))
                Column(Modifier.fillMaxWidth().weight(.58f).padding((20 * u).dp)) {
                    RoomHeading(state, now, snapshot, u)
                    Spacer(Modifier.height((14 * u).dp))
                    TodayAgenda(state, now, u, Modifier.weight(1f).fillMaxWidth())
                    BookingCode(snapshot, now)
                }
            }
        }
    }
}

@Composable
private fun Wordmark(
    state: RoomState,
    assets: Map<String, File>,
    tint: Color,
    u: Float,
    modifier: Modifier = Modifier,
) {
    val file = state.brand.wordmarkAssetId?.let(assets::get)
    if (file != null) {
        BrandImage(file, tint, modifier.width((110 * u).dp).height((58 * u).dp))
    } else {
        Column(modifier) {
            state.brand.titleLines.forEach { Label(it, size(10f, u), tint, bold = true) }
            state.brand.subtitleLines.forEach { Label(it, size(8f, u), tint) }
        }
    }
}

@Composable
private fun RoomHeading(
    state: RoomState,
    now: Instant,
    snapshot: Snapshot,
    u: Float,
) {
    val tint = ink(state.theme.footerText)
    Label(state.room.name, size(18f, u), tint, bold = true)
    state.room.subtitle?.let { Label(it, size(10f, u), ink(state.theme.slotSecondary)) }
    Spacer(Modifier.height((9 * u).dp))
    DateHeading(state, now, snapshot, u)
}

@Composable
private fun DateHeading(
    state: RoomState,
    now: Instant,
    snapshot: Snapshot,
    u: Float,
) {
    val locale = Locale.forLanguageTag(state.locale)
    val date =
        now
            .atZone(
                ZoneId.of(state.timezone),
            ).format(DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL).withLocale(locale))
    Label(date, size(10f, u), ink(state.theme.footerText))
    val source = state.live?.sourceFetchedAt ?: snapshot.lastSuccess?.toString()
    val update = source?.let { " · " + roomResources(state).getString(R.string.updated) + " " + time(it, state) } ?: ""
    Label(time(now.toString(), state) + update, size(9f, u), ink(state.theme.slotSecondary))
}

@Composable
private fun StatusGround(
    snapshot: Snapshot,
    now: Instant,
    assets: Map<String, File>,
    moment: Moment?,
    u: Float,
    modifier: Modifier,
) {
    val state = snapshot.room ?: return
    val background =
        if (moment?.busy ==
            true
        ) {
            state.theme.busyBadge
        } else if (moment != null) {
            state.theme.freeBadge
        } else {
            state.theme.headerBg
        }
    val tint =
        ink(
            RoomLogic.readableOn(
                background,
                if (moment?.busy ==
                    false
                ) {
                    state.theme.footerText
                } else {
                    state.theme.badgeText
                },
            ),
        )
    BoxWithConstraints(modifier.background(ink(background)).testTag("status-ground")) {
        val mark = state.brand.markAssetId?.let(assets::get)
        if (mark !=
            null
        ) {
            BrandImage(
                mark,
                ink(
                    if (moment?.busy ==
                        true
                    ) {
                        state.theme.freeBadge
                    } else {
                        state.theme.background
                    },
                ),
                Modifier
                    .align(Alignment.BottomEnd)
                    .padding(
                        (
                            22 *
                                u
                        ).dp,
                    ).width(maxWidth * .52f)
                    .height(maxHeight * .43f),
            )
        }
        Column(Modifier.fillMaxSize().padding((27 * u).dp)) {
            Wordmark(state, assets, tint, u)
            Spacer(Modifier.height((27 * u).dp))
            StatusWords(snapshot, now, moment, u, tint, large = true)
        }
    }
}

@Composable
private fun StatusWords(
    snapshot: Snapshot,
    now: Instant,
    moment: Moment?,
    u: Float,
    tint: Color,
    large: Boolean,
) {
    val state = snapshot.room ?: return
    val resources = roomResources(state)
    val word =
        when {
            state.state.providerUnavailable ->
                if (state.locale.startsWith(
                        "de",
                    )
                ) {
                    "Kalender nicht erreichbar"
                } else {
                    "Calendar unavailable"
                }
            moment == null -> resources.getString(R.string.state_unknown)
            else ->
                resources
                    .getString(
                        if (moment.busy) R.string.state_busy else R.string.state_free,
                    ).lowercase(Locale.forLanguageTag(state.locale))
                    .replaceFirstChar {
                        it.titlecase(Locale.forLanguageTag(state.locale))
                    }
        }
    Label(
        word,
        size(
            if (moment ==
                null
            ) {
                22f
            } else if (large) {
                62f
            } else {
                34f
            },
            u,
        ),
        tint,
        Modifier.testTag("status"),
        bold = true,
    )
    if (moment != null) {
        val boundary = if (moment.busy) moment.current?.end else moment.next?.start ?: state.state.nextEventStart
        boundary?.let {
            val text =
                resources.getString(R.string.until) + " " +
                    BoundaryLabels.format(
                        Instant.parse(it),
                        now,
                        state.timezone,
                        state.locale,
                        resources.getString(R.string.clockSuffix),
                    )
            Spacer(Modifier.height((8 * u).dp))
            Label(text, size(if (large) 17f else 15f, u), tint, lines = 3)
        }
    }
}

@Composable
private fun TodayAgenda(
    state: RoomState,
    now: Instant,
    u: Float,
    modifier: Modifier,
) {
    val today = now.atZone(ZoneId.of(state.timezone)).toLocalDate()
    val events = RoomLogic.eventsOn(state, today)
    Column(modifier.verticalScroll(rememberScrollState())) {
        Label(
            roomResources(state).getString(R.string.today).uppercase(Locale.forLanguageTag(state.locale)),
            size(10f, u),
            ink(state.theme.slotSecondary),
            bold = true,
        )
        Spacer(Modifier.height((5 * u).dp))
        if (events.isEmpty()) {
            Label(
                if (state.locale.startsWith("de")) "Keine sichtbaren Termine" else "No visible bookings",
                size(12f, u),
                ink(state.theme.slotSecondary),
            )
        }
        events.forEach { event ->
            Box(Modifier.fillMaxWidth().height(1.dp).background(ink(state.theme.slotSecondary)))
            Row(
                Modifier.fillMaxWidth().padding(vertical = (9 * u).dp),
                horizontalArrangement =
                    Arrangement.spacedBy(
                        (
                            8 *
                                u
                        ).dp,
                    ),
            ) {
                Label(
                    time(event.start, state) + " – " + time(event.end, state),
                    size(10f, u),
                    ink(state.theme.slotSecondary),
                    Modifier.width(
                        (
                            82 *
                                u
                        ).dp,
                    ),
                )
                Column(Modifier.weight(1f)) {
                    Label(event.subject, size(13f, u), ink(state.theme.footerText), bold = true)
                    if (event.organizer.isNotBlank()) {
                        Label(
                            event.organizer,
                            size(10f, u),
                            ink(state.theme.slotSecondary),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun NativeBand(
    snapshot: Snapshot,
    now: Instant,
    assets: Map<String, File>,
    moment: Moment?,
    u: Float,
    wide: Boolean,
) {
    val state = snapshot.room ?: return
    BoxWithConstraints(Modifier.fillMaxSize().padding((27 * u).dp)) {
        val mark = state.brand.markAssetId?.let(assets::get)
        val mainWidth = maxWidth * if (wide) .7f else .72f
        if (mark !=
            null
        ) {
            BrandImage(
                mark,
                ink(state.theme.freeBadge),
                Modifier.align(Alignment.TopEnd).width(maxWidth * .27f).height(maxHeight * .52f),
            )
        }
        Column(Modifier.width(mainWidth).fillMaxHeight().padding(bottom = (105 * u).dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Wordmark(state, assets, ink(state.theme.footerText), u, Modifier.weight(.4f))
                Column(Modifier.weight(.6f)) { DateHeading(state, now, snapshot, u) }
            }
            Spacer(Modifier.height((14 * u).dp))
            Label(state.room.name, size(24f, u), ink(state.theme.footerText), bold = true)
            state.room.subtitle?.let { Label(it, size(11f, u), ink(state.theme.slotSecondary)) }
            Spacer(Modifier.height((12 * u).dp))
            val bg =
                if (moment?.busy ==
                    true
                ) {
                    state.theme.busyBadge
                } else if (moment != null) {
                    state.theme.freeBadge
                } else {
                    state.theme.headerBg
                }
            Column(Modifier.background(ink(bg)).padding((10 * u).dp)) {
                StatusWords(
                    snapshot,
                    now,
                    moment,
                    u,
                    ink(
                        RoomLogic.readableOn(
                            bg,
                            if (moment?.busy ==
                                false
                            ) {
                                state.theme.footerText
                            } else {
                                state.theme.badgeText
                            },
                        ),
                    ),
                    large = false,
                )
            }
            val featured = if (moment?.busy == true) moment.current else moment?.next
            featured?.let {
                Spacer(Modifier.height((10 * u).dp))
                Label(it.subject, size(14f, u), ink(state.theme.footerText), bold = true)
                if (it.organizer.isNotBlank()) Label(it.organizer, size(11f, u), ink(state.theme.slotSecondary))
            }
        }
        Box(Modifier.align(Alignment.BottomEnd).padding(bottom = (105 * u).dp)) { BookingCode(snapshot, now) }
        Timeline(state, now, u, Modifier.fillMaxWidth().height((85 * u).dp).align(Alignment.BottomCenter))
    }
}

@Composable
private fun Timeline(
    state: RoomState,
    now: Instant,
    u: Float,
    modifier: Modifier,
) {
    val zone = ZoneId.of(state.timezone)
    val day = now.atZone(zone).toLocalDate()
    val start = day.atStartOfDay(zone).toInstant()
    val end = day.plusDays(1).atStartOfDay(zone).toInstant()
    val events = RoomLogic.lanes(RoomLogic.eventsOn(state, day))

    fun minute(instant: Instant): Float {
        if (instant <= start) return 0f
        if (instant >= end) return 1440f
        val local = instant.atZone(zone)
        return local.hour * 60f + local.minute
    }
    var from = 420f
    var to = 1200f
    events.forEach {
        from = min(from, kotlin.math.floor(minute(Instant.parse(it.event.start)) / 60) * 60)
        to =
            max(to, kotlin.math.ceil(minute(Instant.parse(it.event.end)) / 60) * 60)
    }
    from = min(from, kotlin.math.floor(minute(now) / 60) * 60)
    to = max(to, kotlin.math.ceil(minute(now) / 60) * 60)
    val span = max(60f, to - from)
    val lanes = max(1, (events.maxOfOrNull { it.lane } ?: 0) + 1)
    BoxWithConstraints(modifier.testTag("day-band")) {
        val bandHeight = maxHeight - (20 * u).dp
        Canvas(Modifier.fillMaxSize()) {
            val bottom = bandHeight.toPx()
            val tint = ink(state.theme.slotSecondary)
            drawLine(tint, Offset(0f, 0f), Offset(size.width, 0f))
            drawLine(tint, Offset(0f, bottom), Offset(size.width, bottom))
        }
        events.forEach {
            val left = ((minute(Instant.parse(it.event.start)) - from) / span).coerceIn(0f, 1f)
            val right = ((minute(Instant.parse(it.event.end)) - from) / span).coerceIn(0f, 1f)
            val bg = if (Instant.parse(it.event.end) <= now) state.theme.freeBadge else state.theme.eventBg
            val tint = ink(RoomLogic.readableOn(bg, state.theme.slotText))
            Column(
                Modifier
                    .offset(x = maxWidth * left, y = bandHeight / lanes * it.lane)
                    .width(
                        (
                            maxWidth *
                                (right - left) -
                                2.dp
                        ).coerceAtLeast(1.dp),
                    ).height(bandHeight / lanes)
                    .background(ink(bg))
                    .padding((3 * u).dp),
            ) {
                if (bandHeight / lanes >=
                    (14 * u).dp
                ) {
                    Label(time(it.event.start, state), size(8f, u), tint, bold = true, lines = 1)
                }
                if (bandHeight / lanes >= (28 * u).dp) Label(it.event.subject, size(8f, u), tint, lines = 1)
            }
        }
        Canvas(Modifier.fillMaxSize()) {
            val bottom = bandHeight.toPx()
            val x = ((minute(now) - from) / span).coerceIn(0f, 1f) * size.width
            drawLine(ink(state.theme.footerText), Offset(x, -5 * u), Offset(x, bottom + 5 * u), 2.dp.toPx())
        }
        for (hour in 0..24) {
            if (hour * 60f < from || hour * 60f > to || hour % 2 != 0) continue
            val fraction = (hour * 60f - from) / span
            val labelWidth = (36 * u).dp
            val x = (maxWidth * fraction - labelWidth / 2).coerceIn(0.dp, (maxWidth - labelWidth).coerceAtLeast(0.dp))
            Label(
                "$hour:00",
                size(8f, u),
                ink(state.theme.slotSecondary),
                Modifier.offset(x, bandHeight + (4 * u).dp).width(labelWidth),
                lines = 1,
            )
        }
    }
}
