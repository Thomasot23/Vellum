// SPDX-License-Identifier: AGPL-3.0-or-later
package org.vellum.panel

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import org.vellum.panel.enroll.DeviceIdentity
import org.vellum.panel.enroll.androidIdentityStore
import java.io.File

@RunWith(AndroidJUnit4::class)
class DeviceKeystoreTest {
    @Test fun debugPreviewStartsOnAndroid() {
        androidx.test.core.app.ActivityScenario.launch(DemoActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                org.junit.Assert.assertTrue(
                    activity.window.attributes.flags and android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON !=
                        0,
                )
            }
        }
    }

    @Test fun mainActivityStartsOnAndroid() {
        androidx.test.core.app.ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                org.junit.Assert.assertTrue(
                    activity.window.attributes.flags and android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON !=
                        0,
                )
            }
        }
    }

    @Test fun encryptedIdentitySurvivesStoreRecreationOnAndroid() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val store = androidIdentityStore(context)
        val identity = DeviceIdentity.create("https://example.invalid").copy(token = "public-instrumentation-test")
        try {
            store.save(identity)
            val bytes = File(context.noBackupFilesDir, "identity.v1").readBytes().toString(Charsets.ISO_8859_1)
            assertFalse(bytes.contains(identity.token!!))
            assertFalse(bytes.contains(identity.privateKey))
            assertEquals(identity, androidIdentityStore(context).load())
        } finally {
            store.clear()
        }
    }
}
