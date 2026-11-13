// SPDX-License-Identifier: AGPL-3.0-or-later
package org.vellum.panel.enroll

import kotlinx.serialization.Serializable
import org.bouncycastle.crypto.digests.SHA256Digest
import org.bouncycastle.crypto.generators.HKDFBytesGenerator
import org.bouncycastle.crypto.params.HKDFParameters
import org.bouncycastle.crypto.params.X25519PrivateKeyParameters
import org.bouncycastle.crypto.params.X25519PublicKeyParameters
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

@Serializable
data class TokenEnvelope(
    val ciphertext: String,
    val nonce: String,
    val serverPublicKey: String,
)

/** Wire-compatible with the server's encryptForDevice; no alternate plaintext-token path. */
object TokenCrypto {
    /** HKDF contexts, one per kind of secret, matching the server's `crypto.ts`. */
    const val DEVICE_TOKEN_INFO = "vellum-token-v1"
    const val STATUS_LIGHT_INFO = "vellum-status-light-v1"

    fun decrypt(
        privateKey: ByteArray,
        envelope: TokenEnvelope,
        info: String = DEVICE_TOKEN_INFO,
    ): String {
        val decoder = Base64.getDecoder()
        val peer = decoder.decode(envelope.serverPublicKey)
        val nonce = decoder.decode(envelope.nonce)
        val ciphertext = decoder.decode(envelope.ciphertext)
        require(
            privateKey.size == 32 && peer.size == 32 && nonce.size == 12 && ciphertext.size > 16,
        )
        val shared = ByteArray(32)
        val aes = ByteArray(32)
        try {
            X25519PrivateKeyParameters(
                privateKey,
                0,
            ).generateSecret(X25519PublicKeyParameters(peer, 0), shared, 0)
            val hkdf = HKDFBytesGenerator(SHA256Digest())
            hkdf.init(HKDFParameters(shared, null, info.toByteArray(Charsets.UTF_8)))
            hkdf.generateBytes(aes, 0, aes.size)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(
                Cipher.DECRYPT_MODE,
                SecretKeySpec(aes, "AES"),
                GCMParameterSpec(128, nonce),
            )
            return cipher
                .doFinal(
                    ciphertext,
                ).toString(Charsets.UTF_8)
                .also { require(it.isNotBlank()) }
        } finally {
            shared.fill(0)
            aes.fill(0)
        }
    }
}

@Serializable
data class DeviceIdentity(
    val server: String,
    val mac: String,
    val privateKey: String,
    val publicKey: String,
    val token: String? = null,
    /** Vendor secret for the panel's status light, as delivered by the server. */
    val lightToken: String? = null,
    val lightRevision: String? = null,
) {
    override fun toString(): String = "DeviceIdentity(mac=$mac)"

    companion object {
        fun create(server: String): DeviceIdentity {
            val random = SecureRandom()
            val mac = ByteArray(6).also(random::nextBytes)
            mac[0] = ((mac[0].toInt() and 0xfc) or 2).toByte()
            val key = X25519PrivateKeyParameters(random)
            val encode = Base64.getEncoder()
            return DeviceIdentity(
                server,
                mac.joinToString("") {
                    "%02X".format(it.toInt() and 255)
                },
                encode.encodeToString(
                    key.encoded,
                ),
                encode.encodeToString(key.generatePublicKey().encoded),
            )
        }
    }
}
