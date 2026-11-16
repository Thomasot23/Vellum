// SPDX-License-Identifier: AGPL-3.0-or-later
package org.vellum.panel.ui

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.decode.SvgDecoder
import coil.request.ImageRequest
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import kotlinx.coroutines.delay
import org.vellum.panel.R
import org.vellum.panel.data.RoomEvent
import org.vellum.panel.data.RoomState
import org.vellum.panel.data.Snapshot
import org.vellum.panel.domain.RoomLogic
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private fun color(hex: String) = Color(android.graphics.Color.parseColor(hex))

private val inter = FontFamily(Font(R.font.inter_regular), Font(R.font.inter_bold, FontWeight.Bold))

@Composable
internal fun Label(
    text: String,
    size: Int = 22,
    tint: Color = Color.Black,
    modifier: Modifier = Modifier,
    bold: Boolean = false,
    lines: Int = 2,
) {
    Text(
        text,
        modifier,
        color = tint,
        fontSize = size.sp,
        fontFamily = inter,
        fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
        maxLines = lines,
        overflow = TextOverflow.Ellipsis,
    )
}

internal fun time(
    value: String,
    state: RoomState,
): String =
    DateTimeFormatter
        .ofPattern(
            "HH:mm",
            Locale.forLanguageTag(state.locale),
        ).withZone(ZoneId.of(state.timezone))
        .format(Instant.parse(value))

@Composable
fun RoomScreen(
    snapshot: Snapshot,
    now: Instant,
    assets: Map<String, File> = emptyMap(),
) {
    val state = snapshot.room ?: return
    val resources = roomResources(state)
    var view by rememberSaveable { mutableStateOf("home") }
    var selectedDay by rememberSaveable { mutableIntStateOf(0) }
    var activity by remember { mutableIntStateOf(0) }
    LaunchedEffect(view, activity) {
        if (view !=
            "home"
        ) {
            delay(30_000)
            view = "home"
            selectedDay = 0
        }
    }
    val today = now.atZone(ZoneId.of(state.timezone)).toLocalDate()
    MaterialTheme {
        Column(
            Modifier.fillMaxSize().background(color(state.theme.background)).pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        if (event.changes.any { it.pressed }) activity++
                    }
                }
            },
        ) {
            if (view != "home") {
                Row(
                    Modifier.fillMaxWidth().background(color(state.theme.headerBg)).padding(20.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column(Modifier.weight(1f)) {
                        Label(
                            state.room.name,
                            36,
                            color(state.theme.headerText),
                            bold = true,
                            lines = 1,
                        )
                        state.room.subtitle?.let { Label(it, 20, color(state.theme.headerText)) }
                    }
                    state.brand.wordmarkAssetId?.let { id ->
                        BrandImage(
                            assets[id],
                            color(state.theme.headerText),
                            Modifier.size(180.dp, 58.dp),
                        )
                    }
                    Spacer(Modifier.width(20.dp))
                    Label(
                        DateTimeFormatter
                            .ofPattern(
                                "HH:mm",
                            ).withZone(ZoneId.of(state.timezone))
                            .format(now),
                        36,
                        color(state.theme.headerText),
                        lines = 1,
                    )
                }
            }
            if (snapshot.offlineSince != null) {
                Label(
                    (
                        if (state.locale ==
                            "de"
                        ) {
                            "Keine Verbindung seit "
                        } else {
                            "No connection since "
                        }
                    ) +
                        time(snapshot.offlineSince.toString(), state),
                    20,
                    Color.White,
                    Modifier
                        .fillMaxWidth()
                        .background(
                            Color(0xffa34000),
                        ).padding(8.dp)
                        .testTag("offline"),
                )
            }
            BoxWithConstraints(Modifier.weight(1f).fillMaxWidth().padding(if (view == "home") 0.dp else 20.dp)) {
                val wide = maxWidth > maxHeight
                when (view) {
                    "week" ->
                        Column(
                            Modifier
                                .fillMaxSize()
                                .verticalScroll(
                                    rememberScrollState(),
                                ).testTag("week"),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            repeat(7) { offset ->
                                val day = today.plusDays(offset.toLong())
                                Row(
                                    Modifier
                                        .fillMaxWidth()
                                        .background(
                                            color(state.theme.headerBg).copy(alpha = .08f),
                                        ).clickable {
                                            selectedDay =
                                                offset
                                            ; view = "day"
                                        }.padding(16.dp)
                                        .testTag("week-day-$offset"),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                ) {
                                    Label(
                                        day.format(
                                            DateTimeFormatter.ofPattern(
                                                "EEE dd MMM",
                                                Locale.forLanguageTag(state.locale),
                                            ),
                                        ),
                                        26,
                                        color(state.theme.footerText),
                                        bold = true,
                                    )
                                    Label(
                                        "${RoomLogic.eventsOn(state, day).size} " +
                                            if (state.locale ==
                                                "de"
                                            ) {
                                                "sichtbare Termine"
                                            } else {
                                                "visible bookings"
                                            },
                                        22,
                                        color(state.theme.footerText),
                                    )
                                }
                            }
                        }
                    "day" ->
                        Events(
                            state,
                            RoomLogic.eventsOn(state, today.plusDays(selectedDay.toLong())),
                            today.plusDays(selectedDay.toLong()).toString(),
                            Modifier.fillMaxSize().testTag("day-list"),
                        )
                    else -> NativeHome(snapshot, now, assets, Modifier.fillMaxSize())
                }
            }
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                for ((id, en, de) in listOf(
                    Triple("home", "Home", "Start"),
                    Triple("day", "Day", "Tag"),
                    Triple("week", "Week", "Woche"),
                )) {
                    TextButton(onClick = {
                        view = id
                        selectedDay = 0
                        activity++
                    }) {
                        Label(
                            if (state.locale ==
                                "de"
                            ) {
                                de
                            } else {
                                en
                            },
                            20,
                            color(state.theme.footerText),
                            lines = 1,
                        )
                    }
                }
                snapshot.lastSuccess?.let {
                    Label(
                        resources.getString(R.string.updated) + " " + time(it.toString(), state),
                        16,
                        color(state.theme.footerText),
                        Modifier.padding(12.dp),
                        lines = 1,
                    )
                }
            }
        }
    }
}

@Composable
internal fun Events(
    state: RoomState,
    events: List<RoomEvent>,
    heading: String,
    modifier: Modifier,
) {
    val foreground = color(state.theme.footerText)
    Column(
        modifier.verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Label(heading, 28, foreground, bold = true)
        if (events.isEmpty()) {
            Label(
                if (state.locale ==
                    "de"
                ) {
                    "Keine sichtbaren Termine"
                } else {
                    "No visible bookings"
                },
                22,
                foreground,
            )
        }
        events.forEach {
            Column(Modifier.fillMaxWidth().background(color(state.theme.eventBg)).padding(16.dp)) {
                val text = color(RoomLogic.readableOn(state.theme.eventBg, state.theme.slotText))
                Label("${time(it.start, state)} – ${time(it.end, state)}", 20, text)
                Label(it.subject, 26, text, bold = true)
                if (it.organizer.isNotBlank()) Label(it.organizer, 18, text)
            }
        }
    }
}

@Composable
internal fun BrandImage(
    file: File?,
    tint: Color,
    modifier: Modifier,
) {
    if (file !=
        null
    ) {
        AsyncImage(
            model =
                ImageRequest
                    .Builder(
                        LocalContext.current,
                    ).data(file)
                    .decoderFactory(SvgDecoder.Factory())
                    .build(),
            contentDescription = null,
            modifier = modifier,
            colorFilter = ColorFilter.tint(tint),
        )
    }
}

@Composable
internal fun BookingCode(
    snapshot: Snapshot,
    now: Instant,
) {
    val state = snapshot.room ?: return
    val moment = RoomLogic.liveMoment(snapshot, now) ?: return
    val url = state.booking.url
    if (url == null || (state.live == null && !state.booking.showQr) || state.state.providerUnavailable ||
        state.booking.visibility == "never" ||
        (state.booking.visibility == "free" && moment.busy)
    ) {
        return
    }
    val resources = roomResources(state)
    val bitmap = remember(url) { bookingQrBitmap(url) }
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Image(
            bitmap.asImageBitmap(),
            contentDescription = resources.getString(R.string.book),
            modifier = Modifier.size(116.dp).testTag("booking-qr"),
        )
        Label(
            resources.getString(R.string.scanHint),
            18,
            color(state.theme.footerText),
            Modifier.width(160.dp),
        )
    }
}

internal fun bookingQrBitmap(url: String): Bitmap {
    val matrix =
        QRCodeWriter().encode(
            url,
            BarcodeFormat.QR_CODE,
            256,
            256,
            mapOf(EncodeHintType.MARGIN to 4),
        )
    return Bitmap.createBitmap(256, 256, Bitmap.Config.ARGB_8888).apply {
        for (x in 0 until 256) {
            for (y in 0 until 256) {
                setPixel(
                    x,
                    y,
                    if (matrix[x, y]) android.graphics.Color.BLACK else android.graphics.Color.WHITE,
                )
            }
        }
    }
}

@Composable
internal fun roomResources(state: RoomState): android.content.res.Resources {
    val context = LocalContext.current
    val configuration =
        android.content.res.Configuration(LocalConfiguration.current).apply {
            setLocale(Locale.forLanguageTag(state.locale))
        }
    return context.createConfigurationContext(configuration).resources
}
