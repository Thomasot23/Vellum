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
        compose.onNodeWithText("Week").performClick()
        compose.onNodeWithTag("week").assertIsDisplayed()
        compose.onNodeWithTag("week-day-0").performClick()
        compose.onNodeWithTag("day-list").assertIsDisplayed()
        compose.mainClock.advanceTimeBy(31_000)
        compose.waitForIdle()
        compose.onNodeWithTag("status").assertIsDisplayed()
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
