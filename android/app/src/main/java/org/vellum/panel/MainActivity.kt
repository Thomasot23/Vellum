// SPDX-License-Identifier: AGPL-3.0-or-later
package org.vellum.panel

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.vellum.panel.data.AssetCache
import org.vellum.panel.data.FileRoomCache
import org.vellum.panel.data.LocalHttpStatusLight
import org.vellum.panel.data.PanelClient
import org.vellum.panel.data.PanelRepository
import org.vellum.panel.data.Stage
import org.vellum.panel.domain.LightPolicy
import org.vellum.panel.domain.LightSignal
import org.vellum.panel.domain.NoStatusLight
import org.vellum.panel.domain.StatusLight
import org.vellum.panel.enroll.IdentityStore
import org.vellum.panel.enroll.androidIdentityStore
import org.vellum.panel.kiosk.PanelPolicy
import org.vellum.panel.kiosk.enterKiosk
import org.vellum.panel.ui.EnrollmentScreen
import org.vellum.panel.ui.RoomScreen
import java.io.File
import java.time.Instant
import java.time.ZoneId

class MainActivity : ComponentActivity() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutex = Mutex()
    private var polling: Job? = null
    private var signalling: Job? = null
    private var light: StatusLight = NoStatusLight
    private lateinit var identities: IdentityStore
    private lateinit var repository: PanelRepository
    private lateinit var assetCache: AssetCache
    private val images = MutableStateFlow<Map<String, File>>(emptyMap())
    private val error = MutableStateFlow<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            identities = androidIdentityStore(this)
            repository =
                PanelRepository(
                    { PanelClient(it) },
                    identities,
                    FileRoomCache(File(noBackupFilesDir, "room.json")),
                )
            assetCache = AssetCache(File(noBackupFilesDir, "brand"))
            light = LocalHttpStatusLight({ repository.statusLightToken() })
        } catch (_: Exception) {
            setContent {
                androidx.compose.material3.Text(
                    "Device storage is unavailable. Reset app storage in Android settings to enroll again.",
                )
            }
            return
        }
        setContent {
            val snapshot by repository.snapshot.collectAsState()
            val assets by images.collectAsState()
            val message by error.collectAsState()
            var now by remember { mutableStateOf(Instant.now()) }
            LaunchedEffect(snapshot.room?.timezone) {
                while (true) {
                    now = Instant.now()
                    val zone = ZoneId.of(snapshot.room?.timezone ?: "UTC")
                    window.attributes =
                        window.attributes.apply {
                            screenBrightness =
                                PanelPolicy.brightness(now.atZone(zone).toLocalTime())
                        }
                    delay(1000)
                }
            }
            val shift = PanelPolicy.pixelOffset(now.epochSecond / 60).dp
            Box(Modifier.offset(shift, shift)) {
                if (snapshot.room != null) {
                    RoomScreen(snapshot, now, assets)
                } else {
                    EnrollmentScreen(snapshot, message, { address ->
                        scope.launch {
                            mutex.withLock {
                                try {
                                    repository.configure(address)
                                    error.value = null
                                    tick()
                                } catch (
                                    _: Exception,
                                ) {
                                    error.value =
                                        "Enter an HTTPS server origin without a path or credentials."
                                }
                            }
                        }
                    }, { scope.launch { mutex.withLock { tick() } } })
                }
            }
        }
    }

    private fun tick() {
        repository.tick()
        val identity = identities.load()
        val room = repository.snapshot.value.room
        images.value =
            if (identity?.token != null &&
                room != null
            ) {
                assetCache.sync(PanelClient(identity.server), identity, room)
            } else {
                assetCache.clear()
                emptyMap()
            }
    }

    override fun onStart() {
        super.onStart()
        enterKiosk(this)
        if (!::repository.isInitialized) return
        polling =
            scope.launch {
                while (isActive) {
                    mutex.withLock { tick() }
                    delay(if (repository.snapshot.value.stage == Stage.PENDING) 5000 else 60_000)
                }
            }
        signalling =
            scope.launch {
                while (isActive) {
                    signal()
                    delay(5000)
                }
            }
    }

    /** Follows the same state and the same night dimming as the screen. */
    private fun signal() {
        val snapshot = repository.snapshot.value
        val now = Instant.now()
        val zone =
            try {
                ZoneId.of(snapshot.room?.timezone ?: "UTC")
            } catch (_: Exception) {
                ZoneId.of("UTC")
            }
        val dimmed = PanelPolicy.brightness(now.atZone(zone).toLocalTime()) < 1f
        light.show(LightPolicy.signal(snapshot, now, dimmed))
    }

    override fun onStop() {
        signalling?.cancel()
        polling?.cancel()
        // Nothing keeps the state current once the activity stops, so the LEDs
        // must not keep claiming it. Own thread: onDestroy may cancel the scope.
        val stopping = light
        Thread { stopping.show(LightSignal.OFF) }.start()
        super.onStop()
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }
}
