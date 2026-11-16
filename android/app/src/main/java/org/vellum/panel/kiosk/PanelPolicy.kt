// SPDX-License-Identifier: AGPL-3.0-or-later
package org.vellum.panel.kiosk

import java.time.LocalTime

object PanelPolicy {
    /** Without touch, a day or week view gives way to the home screen after this long. */
    const val RETURN_HOME_MS = 20_000L

    fun returnHome(idleMs: Long): Boolean = idleMs >= RETURN_HOME_MS

    fun brightness(time: LocalTime): Float = if (time.hour in 8..17) 1f else 0.15f

    fun pixelOffset(minutes: Long): Int = (minutes % 5).toInt() - 2
}
