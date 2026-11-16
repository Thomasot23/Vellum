// SPDX-License-Identifier: AGPL-3.0-or-later
package org.vellum.panel.domain

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

object BoundaryLabels {
    fun format(
        boundary: Instant,
        now: Instant,
        timezone: String,
        locale: String,
        clockSuffix: String = "",
    ): String {
        val base = locale.lowercase(Locale.ROOT).substringBefore('-').substringBefore('_')
        val language = base.takeIf { it in setOf("en", "de", "fr", "it", "es") } ?: "en"
        val target = boundary.atZone(ZoneId.of(timezone))
        val reference = now.atZone(target.zone)
        val sameDay = target.toLocalDate() == reference.toLocalDate()
        val differentYear = target.year != reference.year
        val date =
            when (language) {
                "de" -> "dd.MM." + if (differentYear) "uuuu" else ""
                "en" -> "MM/dd" + if (differentYear) "/uuuu" else ""
                else -> "dd/MM" + if (differentYear) "/uuuu" else ""
            }
        val pattern = if (sameDay) "HH:mm" else "EEE, $date, HH:mm"
        val text = target.format(DateTimeFormatter.ofPattern(pattern, Locale.forLanguageTag(language))) + clockSuffix
        val repeatedHour =
            sameDay && boundary > now && target.offset.totalSeconds < reference.offset.totalSeconds &&
                target.toLocalTime() <= reference.toLocalTime()
        val offset = target.format(DateTimeFormatter.ofPattern("xxx"))
        return if (repeatedHour) "$text (UTC$offset)" else text
    }
}
