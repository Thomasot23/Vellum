// SPDX-License-Identifier: AGPL-3.0-or-later
package org.vellum.panel.kiosk

import android.app.Activity
import android.app.admin.DeviceAdminReceiver
import android.app.admin.DevicePolicyManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.WindowManager

class PanelAdmin : DeviceAdminReceiver()

/** https://developer.android.com/work/dpc/dedicated-devices/lock-task-mode */
fun enterKiosk(activity: Activity) {
    activity.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    activity.window.insetsController?.apply {
        hide(WindowInsets.Type.systemBars())
        systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
    }
    val policy = activity.getSystemService(DevicePolicyManager::class.java)
    if (policy.isDeviceOwnerApp(activity.packageName)) {
        policy.setLockTaskPackages(
            ComponentName(activity, PanelAdmin::class.java),
            arrayOf(activity.packageName),
        )
        if (policy.isLockTaskPermitted(activity.packageName)) activity.startLockTask()
    }
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val policy = context.getSystemService(DevicePolicyManager::class.java)
        if (policy.isDeviceOwnerApp(context.packageName)) {
            context.packageManager.getLaunchIntentForPackage(context.packageName)?.let {
                context.startActivity(it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
        }
    }
}
