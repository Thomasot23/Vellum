// SPDX-License-Identifier: AGPL-3.0-or-later
package org.vellum.panel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.vellum.panel.data.PanelJson
import org.vellum.panel.data.RoomState
import org.vellum.panel.data.Snapshot
import org.vellum.panel.data.Stage
import org.vellum.panel.ui.RoomScreen
import java.time.Instant

@GraphicsMode(GraphicsMode.Mode.NATIVE)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w1280dp-h800dp-land")
class RoomScreenTest {
    @get:Rule val compose = createComposeRule()
    private val now = Instant.parse("2026-11-12T10:00:00Z")
    private val fixture =
        PanelJson.decodeFromString<RoomState>(
            javaClass.getResource("/room-state.json")!!.readText(),
        )

    @Test fun bothLayoutsShowFreeSoonBusyAndOfflineInLandscape() = verifyLayouts()

    @Test
    @Config(qualifiers = "w800dp-h1280dp-port")
    fun bothLayoutsShowFreeSoonBusyAndOfflineInPortrait() = verifyLayouts()

    private fun verifyLayouts() {
        var snapshot by mutableStateOf(Snapshot(Stage.ACTIVE, fixture, now))
        compose.setContent { RoomScreen(snapshot, now) }
        for (layout in listOf("status-panel", "day-band")) {
            for (mode in listOf("free", "soon", "busy", "offline")) {
                val busy = mode == "busy"
                val events =
                    if (mode ==
                        "soon"
                    ) {
                        fixture.events.map { it.copy(start = "2026-11-12T10:05:00Z") }
                    } else {
                        emptyList()
                    }
                compose.runOnIdle {
                    snapshot =
                        Snapshot(
                            Stage.ACTIVE,
                            fixture.copy(
                                layout = layout,
                                events = events,
                                state = fixture.state.copy(isRoomFree = !busy),
                            ),
                            now,
                            if (mode ==
                                "offline"
                            ) {
                                now
                            } else {
                                null
                            },
                        )
                }
                compose
                    .onNodeWithTag(
                        "status",
                    ).assertIsDisplayed()
                    .assertTextEquals(if (busy) "Busy" else "Free")
                compose.onNodeWithText("Meeting Room").assertIsDisplayed()
                if (mode == "offline") compose.onNodeWithTag("offline").assertIsDisplayed()
            }
        }
    }

    @Test fun dayWeekNavigationAndThirtySecondReturnWork() {
        compose.setContent { RoomScreen(Snapshot(Stage.ACTIVE, fixture, now), now) }
        compose.onNodeWithTag("nav-week").performClick()
        compose.onNodeWithTag("week").assertIsDisplayed()
        compose.onNodeWithTag("week-day-0").performClick()
        compose.onNodeWithTag("day-list").assertIsDisplayed()
        compose.mainClock.advanceTimeBy(31_000)
        compose.waitForIdle()
        compose.onNodeWithTag("status").assertIsDisplayed()
    }

    @Test fun weekAndBackButtonsReplaceThePersistentMenu() {
        compose.setContent { RoomScreen(Snapshot(Stage.ACTIVE, fixture, now), now) }
        compose.onNodeWithTag("nav-back").assertDoesNotExist()
        compose.onNodeWithText("Home").assertDoesNotExist()
        compose.onNodeWithText("Day").assertDoesNotExist()
        compose.onNodeWithTag("nav-week").performClick()
        compose.onNodeWithTag("week").assertIsDisplayed()
        compose.onNodeWithTag("week-day-0").performClick()
        compose.onNodeWithTag("day-list").assertIsDisplayed()
        compose.onNodeWithTag("nav-back").performClick()
        compose.onNodeWithTag("week").assertIsDisplayed()
        compose.onNodeWithTag("nav-back").performClick()
        compose.onNodeWithTag("status").assertIsDisplayed()
        compose.onNodeWithTag("nav-back").assertDoesNotExist()
    }

    @Test fun bookingsAreColouredByPhaseInBothLayouts() {
        val event = fixture.events.first()
        val events =
            listOf(
                event.copy(start = "2026-11-12T08:00:00Z", end = "2026-11-12T09:00:00Z", subject = "Earlier"),
                event.copy(start = "2026-11-12T09:30:00Z", end = "2026-11-12T10:30:00Z", subject = "Running"),
                event.copy(start = "2026-11-12T12:00:00Z", end = "2026-11-12T13:00:00Z", subject = "Later"),
            )
        var room by mutableStateOf(
            fixture.copy(layout = "day-band", events = events, state = fixture.state.copy(isRoomFree = false)),
        )
        compose.setContent { RoomScreen(Snapshot(Stage.ACTIVE, room, now), now) }
        for (tag in listOf("band-past", "band-now", "band-ahead")) compose.onNodeWithTag(tag).assertIsDisplayed()
        compose.onNodeWithText("Running · 30 min left").assertIsDisplayed()
        compose.runOnIdle { room = room.copy(layout = "status-panel") }
        for (tag in listOf("agenda-past", "agenda-now", "agenda-ahead")) compose.onNodeWithTag(tag).assertIsDisplayed()
        compose.onNodeWithTag("busy-progress").assertExists()
        compose.onNodeWithText("30 min left").assertIsDisplayed()
    }

    @Test fun announcesABookingThatStartsSoon() {
        val event = fixture.events.first()
        val room =
            fixture.copy(
                layout = "status-panel",
                events =
                    listOf(
                        event.copy(start = "2026-11-12T10:05:00Z", end = "2026-11-12T11:00:00Z", subject = "Next up"),
                    ),
                state = fixture.state.copy(isRoomFree = true),
            )
        compose.setContent { RoomScreen(Snapshot(Stage.ACTIVE, room, now), now) }
        compose.onNodeWithTag("status").assertTextEquals("Free")
        compose.onNodeWithTag("soon").assertTextEquals("5 min left")
        compose.onNodeWithText("from 10:05").assertIsDisplayed()
    }

    @Test fun countsVisibleBookingsGrammatically() {
        assertEquals(
            "1 sichtbarer Termin",
            org.vellum.panel.ui
                .bookingCount(1, "de"),
        )
        assertEquals(
            "3 sichtbare Termine",
            org.vellum.panel.ui
                .bookingCount(3, "de"),
        )
        assertEquals(
            "keine sichtbaren Termine",
            org.vellum.panel.ui
                .bookingCount(0, "de"),
        )
        assertEquals(
            "1 visible booking",
            org.vellum.panel.ui
                .bookingCount(1, "en"),
        )
    }

    @Test fun qrRoundTripsAndRespectsServerVisibility() {
        val url = "https://example.com/book"
        var snapshot by mutableStateOf(
            Snapshot(
                Stage.ACTIVE,
                fixture.copy(
                    booking = fixture.booking.copy(url = url, visibility = "always", showQr = true),
                ),
                now,
            ),
        )
        compose.setContent { RoomScreen(snapshot, now) }
        compose.onNodeWithTag("booking-qr").assertIsDisplayed()
        val image =
            org.vellum.panel.ui
                .bookingQrBitmap(url)
        val pixels = IntArray(image.width * image.height)
        image.getPixels(pixels, 0, image.width, 0, 0, image.width, image.height)
        val source = com.google.zxing.RGBLuminanceSource(image.width, image.height, pixels)
        val decoded =
            com.google.zxing.MultiFormatReader().decode(
                com.google.zxing.BinaryBitmap(
                    com.google.zxing.common
                        .HybridBinarizer(source),
                ),
            )
        org.junit.Assert.assertEquals(url, decoded.text)
        compose.runOnIdle {
            snapshot =
                snapshot.copy(
                    room =
                        snapshot.room!!.copy(
                            booking = snapshot.room!!.booking.copy(showQr = false),
                        ),
                )
        }
        compose.onNodeWithTag("booking-qr").assertDoesNotExist()
    }

    @Test fun providerOutageDoesNotClaimBusyOrFree() {
        compose.setContent {
            RoomScreen(
                Snapshot(
                    Stage.ACTIVE,
                    fixture.copy(state = fixture.state.copy(providerUnavailable = true)),
                    now,
                ),
                now,
            )
        }
        compose.onNodeWithTag("status").assertTextEquals("Calendar unavailable")
    }
}
