// SPDX-License-Identifier: AGPL-3.0-or-later
package org.vellum.panel.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import org.vellum.panel.enroll.DeviceIdentity
import org.vellum.panel.enroll.IdentityStore
import org.vellum.panel.enroll.TokenCrypto
import org.vellum.panel.enroll.atomicWrite
import java.io.File
import java.time.Instant
import java.util.Base64

enum class Stage { SETUP, PENDING, ACTIVE, UNASSIGNED, UNSUPPORTED, ERROR }

data class Snapshot(
    val stage: Stage = Stage.SETUP,
    val room: RoomState? = null,
    val lastSuccess: Instant? = null,
    val offlineSince: Instant? = null,
    val mac: String? = null,
)

@Serializable data class CachedRoom(
    val server: String,
    val mac: String,
    val etag: String?,
    val lastSuccess: String,
    val room: RoomState,
)

interface CacheStore {
    fun load(): CachedRoom?

    fun save(room: CachedRoom)

    fun clear()
}

class FileRoomCache(
    private val file: File,
) : CacheStore {
    override fun load(): CachedRoom? =
        try {
            if (file.exists()) PanelJson.decodeFromString<CachedRoom>(file.readText()) else null
        } catch (
            _: Exception,
        ) {
            null
        }

    override fun save(room: CachedRoom) = atomicWrite(file, PanelJson.encodeToString(room).toByteArray())

    override fun clear() {
        file.delete()
    }
}

/** All calls are serialized by the owning IO coroutine, including configuration. */
class PanelRepository(
    private val client: (String) -> PanelClient,
    private val identities: IdentityStore,
    private val cache: CacheStore,
    private val now: () -> Instant = Instant::now,
) {
    private var etag: String? = null

    /** Read by the status light every few seconds, so kept here instead of reloaded. */
    @Volatile private var lightToken: String? = null
    private val mutable = MutableStateFlow(restore())
    val snapshot = mutable.asStateFlow()

    private fun restore(): Snapshot {
        val identity = identities.load() ?: return Snapshot()
        lightToken = identity.lightToken
        val cached = cache.load()
        if (identity.token != null && cached?.server == identity.server &&
            cached.mac == identity.mac
        ) {
            try {
                cached.room.validated()
                val time = Instant.parse(cached.lastSuccess)
                etag = cached.etag
                return Snapshot(Stage.ACTIVE, cached.room, time, time, identity.mac)
            } catch (_: Exception) {
                cache.clear()
            }
        }
        return Snapshot(
            if (identity.token ==
                null
            ) {
                Stage.PENDING
            } else {
                Stage.UNASSIGNED
            },
            mac = identity.mac,
        )
    }

    fun configure(server: String) {
        client(server) // Validate before replacing an existing identity.
        val identity = DeviceIdentity.create(server.trim().trimEnd('/'))
        identities.save(identity)
        cache.clear()
        etag = null
        lightToken = null
        mutable.value = Snapshot(Stage.PENDING, mac = identity.mac)
    }

    fun reset() {
        lightToken = null
        identities.clear()
        cache.clear()
        etag = null
        mutable.value = Snapshot()
    }

    fun tick() {
        try {
            var identity = identities.load() ?: return
            val api = client(identity.server)
            if (identity.token == null) {
                val hello = api.hello(identity)
                if (hello.status ==
                    "pending"
                ) {
                    mutable.value = Snapshot(Stage.PENDING, mac = identity.mac)
                    return
                }
                require(hello.status == "approved")
                val key = Base64.getDecoder().decode(identity.privateKey)
                val token =
                    try {
                        TokenCrypto.decrypt(key, requireNotNull(hello.encryptedToken))
                    } finally {
                        key.fill(0)
                    }
                identity = identity.copy(token = token)
                identities.save(identity)
            }
            val reply = api.room(identity, etag)
            if (reply.status in setOf(200, 204, 304)) {
                identity = syncStatusLight(api, identity, reply.lightRevision)
            }
            when (reply.status) {
                200 -> {
                    val room = requireNotNull(reply.room)
                    val time = now()
                    cache.save(
                        CachedRoom(
                            identity.server,
                            identity.mac,
                            reply.etag,
                            time.toString(),
                            room,
                        ),
                    )
                    etag = reply.etag
                    mutable.value = Snapshot(Stage.ACTIVE, room, time, mac = identity.mac)
                }
                304 -> {
                    val current = mutable.value
                    val room = current.room
                    if (room == null) {
                        etag = null
                        throw IllegalStateException("No cached room")
                    }
                    val time = now()
                    cache.save(
                        CachedRoom(identity.server, identity.mac, etag, time.toString(), room),
                    )
                    mutable.value = current.copy(lastSuccess = time, offlineSince = null)
                }
                401 -> {
                    identities.save(identity.copy(token = null, lightToken = null, lightRevision = null))
                    lightToken = null
                    clearRoom(Stage.PENDING, identity.mac)
                }
                204 -> clearRoom(Stage.UNASSIGNED, identity.mac)
                422 -> clearRoom(Stage.UNSUPPORTED, identity.mac)
                else -> throw IllegalStateException("Room request failed")
            }
        } catch (_: Exception) {
            val previous = mutable.value
            mutable.value =
                previous.copy(
                    stage =
                        if (previous.room ==
                            null
                        ) {
                            Stage.ERROR
                        } else {
                            previous.stage
                        },
                    offlineSince =
                        previous.offlineSince ?: previous.lastSuccess ?: now(),
                )
        }
    }

    /** The vendor secret for the status light, or null when the panel has none. */
    fun statusLightToken(): String? = lightToken

    /**
     * Follow the server's revision of the status-light secret. Never fatal: a panel
     * that cannot fetch or open the secret still shows its room, and tries again on
     * the next poll because the stored revision is left unchanged.
     */
    private fun syncStatusLight(
        api: PanelClient,
        identity: DeviceIdentity,
        revision: String?,
    ): DeviceIdentity {
        if (revision == null || revision == identity.lightRevision) return identity
        return try {
            val updated =
                if (revision == LIGHT_REVISION_NONE) {
                    identity.copy(lightToken = null, lightRevision = LIGHT_REVISION_NONE)
                } else {
                    val reply = api.statusLight(identity)
                    when (reply.status) {
                        204 -> identity.copy(lightToken = null, lightRevision = LIGHT_REVISION_NONE)
                        200 -> {
                            val key = Base64.getDecoder().decode(identity.privateKey)
                            val token =
                                try {
                                    TokenCrypto.decrypt(
                                        key,
                                        requireNotNull(reply.envelope),
                                        TokenCrypto.STATUS_LIGHT_INFO,
                                    )
                                } finally {
                                    key.fill(0)
                                }
                            identity.copy(lightToken = token, lightRevision = reply.revision)
                        }
                        else -> return identity
                    }
                }
            identities.save(updated)
            lightToken = updated.lightToken
            updated
        } catch (_: Exception) {
            identity
        }
    }

    private fun clearRoom(
        stage: Stage,
        mac: String,
    ) {
        cache.clear()
        etag = null
        mutable.value = Snapshot(stage, mac = mac)
    }
}
