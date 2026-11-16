// SPDX-License-Identifier: AGPL-3.0-or-later
package org.vellum.panel.data

import android.app.ActivityManager
import android.app.admin.DevicePolicyManager
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.provider.Settings
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import java.util.Base64

/**
 * What the panel reports about its own platform. Mirrors the server's
 * `panel-info-schema.ts`; both sides test against `panel-info.json`.
 *
 * Deliberately no hardware identifiers, network names or addresses.
 */
@Serializable
data class PanelInfo(
    val appVersion: String,
    val appVersionCode: Int,
    val androidRelease: String,
    val sdkInt: Int,
    val securityPatch: String?,
    val manufacturer: String,
    val model: String,
    val buildType: String,
    val buildTags: String,
    val deviceOwner: Boolean,
    val lockTask: Boolean,
    val adbEnabled: Boolean,
    val developerOptions: Boolean,
    val networkType: String,
) {
    /** The `X-Panel-Info` header value: unpadded base64url JSON. */
    fun header(): String =
        Base64
            .getUrlEncoder()
            .withoutPadding()
            .encodeToString(PanelJson.encodeToString(this).toByteArray(Charsets.UTF_8))

    companion object {
        private val PATCH = Regex("\\d{4}-\\d{2}-\\d{2}")

        /** Printable ASCII, trimmed to the server's limits, never empty. */
        fun label(
            value: String?,
            max: Int,
        ): String =
            value
                ?.filter { it in ' '..'~' }
                ?.trim()
                ?.take(max)
                ?.ifEmpty { null } ?: "unknown"

        fun patch(value: String?): String? = value?.takeIf(PATCH::matches)

        fun buildType(value: String?): String =
            value
                ?.lowercase()
                ?.filter { it in 'a'..'z' }
                ?.take(16)
                ?.ifEmpty { null } ?: "unknown"

        fun buildTags(value: String?): String =
            value?.lowercase()?.filter { it in 'a'..'z' || it in '0'..'9' || it in ",_-" }?.take(64) ?: ""
    }
}

/**
 * Decides when the report rides along with a poll: first poll, any change, and
 * otherwise every 15 minutes. A report counts as delivered only once the server
 * answered, so a failed poll resends it.
 */
class PanelInfoReporter(
    private val collect: () -> PanelInfo?,
    private val now: () -> Long = System::currentTimeMillis,
) {
    private var delivered: PanelInfo? = null
    private var deliveredAt = 0L

    /** The header to attach to the next poll, or null. Never throws. */
    fun next(): Pending? {
        val info =
            try {
                collect()
            } catch (_: Exception) {
                null
            } ?: return null
        val time = now()
        if (info == delivered && time - deliveredAt < INTERVAL_MS) return null
        return Pending(info, info.header())
    }

    fun delivered(pending: Pending) {
        delivered = pending.info
        deliveredAt = now()
    }

    data class Pending(
        val info: PanelInfo,
        val header: String,
    )

    companion object {
        const val INTERVAL_MS = 15 * 60_000L
    }
}

/** Collects [PanelInfo] from public Android APIs; needs no runtime permission. */
fun collectPanelInfo(context: Context): PanelInfo {
    val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
    val resolver = context.contentResolver
    val policy = context.getSystemService(DevicePolicyManager::class.java)
    val activities = context.getSystemService(ActivityManager::class.java)
    val connectivity = context.getSystemService(ConnectivityManager::class.java)
    val capabilities = connectivity?.getNetworkCapabilities(connectivity.activeNetwork)
    return PanelInfo(
        appVersion = PanelInfo.label(packageInfo.versionName, 32),
        appVersionCode = packageInfo.longVersionCode.coerceIn(1, 2_100_000_000).toInt(),
        androidRelease = PanelInfo.label(Build.VERSION.RELEASE, 16),
        sdkInt = Build.VERSION.SDK_INT,
        securityPatch = PanelInfo.patch(Build.VERSION.SECURITY_PATCH),
        manufacturer = PanelInfo.label(Build.MANUFACTURER, 64),
        model = PanelInfo.label(Build.MODEL, 64),
        buildType = PanelInfo.buildType(Build.TYPE),
        buildTags = PanelInfo.buildTags(Build.TAGS),
        deviceOwner = policy?.isDeviceOwnerApp(context.packageName) == true,
        lockTask = activities?.lockTaskModeState?.let { it != ActivityManager.LOCK_TASK_MODE_NONE } == true,
        adbEnabled = Settings.Global.getInt(resolver, Settings.Global.ADB_ENABLED, 0) == 1,
        developerOptions =
            Settings.Global.getInt(resolver, Settings.Global.DEVELOPMENT_SETTINGS_ENABLED, 0) == 1,
        networkType =
            when {
                capabilities == null -> "none"
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "ethernet"
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "wifi"
                else -> "other"
            },
    )
}
