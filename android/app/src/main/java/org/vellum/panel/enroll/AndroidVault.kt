// SPDX-License-Identifier: AGPL-3.0-or-later
package org.vellum.panel.enroll

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.io.File
import java.security.KeyStore
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey

/** https://developer.android.com/privacy-and-security/keystore */
fun androidIdentityStore(context: Context): IdentityStore {
    val alias = "vellum-panel-identity-v1"
    val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
    val key =
        (store.getKey(alias, null) as? SecretKey)
            ?: KeyGenerator
                .getInstance("AES", "AndroidKeyStore")
                .apply {
                    init(
                        KeyGenParameterSpec
                            .Builder(
                                alias,
                                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
                            ).setKeySize(256)
                            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                            .build(),
                    )
                }.generateKey()
    return EncryptedIdentityStore(File(context.noBackupFilesDir, "identity.v1"), SecretBox(key))
}
