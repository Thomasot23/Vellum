// SPDX-License-Identifier: AGPL-3.0-or-later
package org.vellum.panel

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import org.vellum.panel.data.PanelJson
import org.vellum.panel.data.RoomState
import org.vellum.panel.data.Snapshot
import org.vellum.panel.data.Stage
import org.vellum.panel.kiosk.enterKiosk
import org.vellum.panel.ui.RoomScreen
import java.time.Instant

/** Synthetic offline preview, compiled only into debug builds. */
class DemoActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestedOrientation =
            if (intent.getBooleanExtra(
                    "portrait",
                    false,
                )
            ) {
                android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            } else {
                android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
            }
        val fixture =
            PanelJson.decodeFromString<RoomState>(
                assets.open("room-state.json").bufferedReader().use { it.readText() },
            )
        val now = Instant.parse(fixture.serverTime)
        val mode = intent.getStringExtra("mode") ?: "free"
        val start =
            if (mode ==
                "busy"
            ) {
                now.minusSeconds(1800)
            } else {
                now.plusSeconds(if (mode == "later") 86400 else 3600)
            }
        val markId = "00000000-0000-4000-8000-000000000001"
        val mark =
            java.io.File(cacheDir, "fixture-mark.svg").apply {
                writeText(
                    "<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"80\" height=\"120\" viewBox=\"0 0 80 120\"><rect x=\"4\" y=\"4\" width=\"72\" height=\"72\" rx=\"12\"/><circle cx=\"40\" cy=\"102\" r=\"14\"/></svg>",
                )
            }
        val state =
            fixture.copy(
                layout = intent.getStringExtra("layout") ?: "status-panel",
                state = fixture.state.copy(isRoomFree = mode != "busy", nextEventStart = start.toString()),
                events =
                    listOf(
                        org.vellum.panel.data.RoomEvent(
                            start.toString(),
                            start.plusSeconds(3600).toString(),
                            "Project planning",
                            "Example team",
                            false,
                        ),
                    ),
                brand = fixture.brand.copy(titleLines = listOf("EXAMPLE", "ROOM DISPLAY"), markAssetId = markId),
                booking = fixture.booking.copy(url = "https://example.com/book", visibility = "always", showQr = true),
            )
        setContent {
            RoomScreen(Snapshot(Stage.ACTIVE, state, now), now, mapOf(markId to mark))
        }
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        stopLockTask()
        setIntent(intent)
        recreate()
    }

    override fun onStart() {
        super.onStart()
        enterKiosk(this)
    }
}
