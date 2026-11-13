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
        val fixture =
            PanelJson.decodeFromString<RoomState>(
                assets.open("room-state.json").bufferedReader().use { it.readText() },
            )
        val state =
            fixture.copy(
                layout = intent.getStringExtra("layout") ?: "status-panel",
                booking = fixture.booking.copy(url = "https://example.com/book", visibility = "always", showQr = true),
            )
        setContent {
            RoomScreen(Snapshot(Stage.ACTIVE, state, Instant.parse(state.serverTime)), Instant.parse(state.serverTime))
        }
    }

    override fun onStart() {
        super.onStart()
        enterKiosk(this)
    }
}
