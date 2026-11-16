// SPDX-License-Identifier: AGPL-3.0-or-later
package org.vellum.panel

import android.app.admin.DevicePolicyManager
import android.content.Context
import android.os.Build
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.vellum.panel.data.collectPanelInfo

@RunWith(AndroidJUnit4::class)
class PanelInfoDeviceTest {
    @Test fun collectsThePlatformFromPublicApis() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val info = collectPanelInfo(context)
        assertEquals(Build.VERSION.SDK_INT, info.sdkInt)
        assertTrue(info.appVersionCode >= 1)
        assertTrue(info.networkType in setOf("ethernet", "wifi", "other", "none"))
        val policy = context.getSystemService(DevicePolicyManager::class.java)
        assertEquals(policy.isDeviceOwnerApp(context.packageName), info.deviceOwner)
        assertTrue(info.header().matches(Regex("[A-Za-z0-9_-]+")))
    }
}
