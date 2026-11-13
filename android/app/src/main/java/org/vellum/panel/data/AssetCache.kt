// SPDX-License-Identifier: AGPL-3.0-or-later
package org.vellum.panel.data

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import org.vellum.panel.enroll.DeviceIdentity
import org.vellum.panel.enroll.atomicWrite
import java.io.File
import java.security.MessageDigest

@Serializable private data class AssetMetadata(
    val mime: String,
    val etag: String?,
)

class AssetCache(
    private val directory: File,
) {
    fun clear() {
        directory.deleteRecursively()
    }

    fun sync(
        api: PanelClient,
        identity: DeviceIdentity,
        state: RoomState,
    ): Map<String, File> {
        val binding =
            MessageDigest
                .getInstance(
                    "SHA-256",
                ).digest((identity.server + identity.mac).toByteArray())
                .joinToString("") {
                    "%02x".format(it.toInt() and 255)
                }
        val marker = File(directory, "binding")
        if (!marker.exists() || marker.readText() != binding) {
            clear()
            directory.mkdirs()
            marker.writeText(binding)
        }
        val ids =
            listOfNotNull(state.brand.wordmarkAssetId, state.brand.markAssetId)
                .filter {
                    it.matches(Regex("[0-9a-fA-F-]{36}"))
                }.toSet()
        directory
            .listFiles()
            ?.filter {
                it.name != "binding" && it.name.substringBefore('.') !in ids
            }?.forEach { it.delete() }
        val result = mutableMapOf<String, File>()
        for (id in ids) {
            val metadataFile = File(directory, "$id.json")
            var metadata =
                try {
                    PanelJson.decodeFromString<AssetMetadata>(metadataFile.readText())
                } catch (
                    _: Exception,
                ) {
                    null
                }

            fun image() =
                File(
                    directory,
                    "$id." + if (metadata?.mime == "image/svg+xml") "svg" else "image",
                )
            try {
                val response =
                    api.asset(
                        identity,
                        id,
                        if (image().exists()) metadata?.etag else null,
                    )
                if (response.status == 200) {
                    val previous = image()
                    metadata = AssetMetadata(requireNotNull(response.mime), response.etag)
                    if (previous != image()) previous.delete()
                    atomicWrite(image(), requireNotNull(response.bytes))
                    atomicWrite(metadataFile, PanelJson.encodeToString(metadata).toByteArray())
                } else if (response.status != 304) {
                    image().delete()
                    metadataFile.delete()
                    metadata = null
                }
            } catch (_: Exception) {
                // A missing brand image must not hide the room.
            }
            if (metadata != null && image().exists()) result[id] = image()
        }
        return result
    }
}
