// SPDX-License-Identifier: AGPL-3.0-or-later
package org.vellum.panel.kiosk

import java.time.LocalTime

object PanelPolicy {
    fun returnHome(idleMs: Long): Boolean = idleMs >= 30_000

    fun brightness(time: LocalTime): Float = if (time.hour in 8..17) 1f else 0.15f

    fun pixelOffset(minutes: Long): Int = (minutes % 5).toInt() - 2
}
