// SPDX-License-Identifier: AGPL-3.0-or-later
package org.vellum.panel

import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.vellum.panel.data.PanelJson
import org.vellum.panel.enroll.DeviceIdentity
import org.vellum.panel.enroll.EncryptedIdentityStore
import org.vellum.panel.enroll.IdentityStore
import org.vellum.panel.enroll.SecretBox
import org.vellum.panel.enroll.TokenCrypto
import org.vellum.panel.enroll.TokenEnvelope
import java.nio.file.Files
import java.util.Base64
import javax.crypto.spec.SecretKeySpec

class CryptoTest {
    @org.junit.Test fun refusesToReportResetWhenIdentityDeletionFails() {
        val directory =
            java.nio.file.Files
                .createTempDirectory("panel-nonempty")
                .toFile()
        java.io.File(directory, "child").writeText("public test")
        val store =
            EncryptedIdentityStore(
                directory,
                SecretBox(javax.crypto.spec.SecretKeySpec(ByteArray(32), "AES")),
            )
        org.junit.Assert.assertThrows(java.io.IOException::class.java) { store.clear() }
    }

    private val scalar =
        ("77076d0a7318a57d3c16c17251b26645df" + "4c2f87ebc0992ab177fba51db92c2a")
            .chunked(
                2,
            ).map {
                it.toInt(16).toByte()
            }.toByteArray()

    private fun fixture(): TokenEnvelope {
        val json =
            PanelJson
                .parseToJsonElement(
                    javaClass.getResource("/token-envelope.json")!!.readText(),
                ).jsonObject
        return TokenEnvelope(
            json.getValue("ciphertext").jsonPrimitive.content,
            json.getValue("nonce").jsonPrimitive.content,
            json.getValue("serverPublicKey").jsonPrimitive.content,
        )
    }

    @Test fun decryptsRealServerEnvelope() {
        assertEquals("public-interoperability-test", TokenCrypto.decrypt(scalar, fixture()))
    }

    @Test fun rejectsTamperedEnvelope() {
        val envelope = fixture()
        val bytes = Base64.getDecoder().decode(envelope.ciphertext)
        bytes[0] = (bytes[0].toInt() xor 1).toByte()
        assertThrows(Exception::class.java) {
            TokenCrypto.decrypt(
                scalar,
                envelope.copy(ciphertext = Base64.getEncoder().encodeToString(bytes)),
            )
        }
    }

    @Test fun rejectsInvalidNonceAndPeerKey() {
        assertThrows(
            Exception::class.java,
        ) { TokenCrypto.decrypt(scalar, fixture().copy(nonce = "AA==")) }
        assertThrows(Exception::class.java) {
            TokenCrypto.decrypt(
                scalar,
                fixture().copy(serverPublicKey = Base64.getEncoder().encodeToString(ByteArray(32))),
            )
        }
    }

    @Test fun generatesLocallyAdministeredUnicastIdentities() {
        val identities = List(30) { DeviceIdentity.create("https://example.com") }
        assertEquals(30, identities.map { it.mac }.toSet().size)
        identities.forEach {
            assertTrue(it.mac.matches(Regex("[0-9A-F]{12}")))
            assertEquals(2, it.mac.take(2).toInt(16) and 3)
            assertEquals(32, Base64.getDecoder().decode(it.privateKey).size)
        }
    }

    @Test fun persistsOnlyAuthenticatedCiphertextAndUsesFreshNonces() {
        val key = SecretKeySpec(ByteArray(32) { 7 }, "AES")
        val box = SecretBox(key)
        val file = Files.createTempDirectory("panel-vault").resolve("identity").toFile()
        val store = EncryptedIdentityStore(file, box)
        val identity =
            DeviceIdentity
                .create(
                    "https://example.com",
                ).copy(token = "public-test-token")
        store.save(identity)
        assertEquals(identity, store.load())
        assertFalse(file.readText(Charsets.ISO_8859_1).contains("public-test-token"))
        val first = file.readBytes()
        store.save(identity)
        assertFalse(first.contentEquals(file.readBytes()))
        val broken = file.readBytes()
        broken[broken.lastIndex] =
            (broken.last().toInt() xor 1).toByte()
        file.writeBytes(broken)
        assertThrows(Exception::class.java) { store.load() }
    }
}
