// SPDX-License-Identifier: AGPL-3.0-or-later
package org.vellum.panel.enroll

import java.security.MessageDigest
import java.util.Base64

/**
 * The comparable name of the panel's handshake key, shown while it waits for
 * approval. Same function as the server's `key-fingerprint.ts`.
 */
object Fingerprint {
    fun of(publicKeyBase64: String): String? {
        val raw =
            try {
                Base64.getDecoder().decode(publicKeyBase64)
            } catch (_: IllegalArgumentException) {
                return null
            }
        if (raw.size != 32) return null
        return MessageDigest
            .getInstance("SHA-256")
            .digest(raw)
            .copyOfRange(0, 10)
            .joinToString("") { "%02X".format(it.toInt() and 0xff) }
            .chunked(4)
            .joinToString("-")
    }
}
