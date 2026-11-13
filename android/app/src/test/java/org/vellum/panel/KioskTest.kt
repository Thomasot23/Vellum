// SPDX-License-Identifier: AGPL-3.0-or-later
package org.vellum.panel

import android.app.Activity
import android.content.Intent
import android.view.WindowManager
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.vellum.panel.kiosk.BootReceiver
import org.vellum.panel.kiosk.enterKiosk

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class KioskTest {
    @Test fun keepsScreenOnWithoutForcingAnUnmanagedDeviceIntoLockTask() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        enterKiosk(activity)
        assertTrue(activity.window.attributes.flags and WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON != 0)
    }

    @Test fun doesNotLaunchAnUnmanagedAppFromBootBroadcast() {
        val context = RuntimeEnvironment.getApplication()
        BootReceiver().onReceive(context, Intent(Intent.ACTION_BOOT_COMPLETED))
        assertNull(shadowOf(context).nextStartedActivity)
    }
}
