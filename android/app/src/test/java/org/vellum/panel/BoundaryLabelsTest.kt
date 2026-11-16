// SPDX-License-Identifier: AGPL-3.0-or-later
package org.vellum.panel

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.vellum.panel.domain.BoundaryLabels
import java.time.Instant

class BoundaryLabelsTest {
    @Test fun keepsTheSameLocalDayCompact() {
        assertEquals(
            "13:00 Uhr",
            BoundaryLabels.format(
                Instant.parse("2026-11-16T12:00:00Z"),
                Instant.parse("2026-11-16T11:10:00Z"),
                "Europe/Berlin",
                "de",
                " Uhr",
            ),
        )
    }

    @Test fun qualifiesTomorrowEvenWhenLessThan24HoursAway() {
        assertEquals(
            "Di., 17.11., 01:30 Uhr",
            BoundaryLabels.format(
                Instant.parse("2026-11-17T00:30:00Z"),
                Instant.parse("2026-11-16T22:50:00Z"),
                "Europe/Berlin",
                "de",
                " Uhr",
            ),
        )
    }

    @Test fun qualifiesTheNextDay() {
        assertEquals(
            "Di., 17.11., 13:00 Uhr",
            BoundaryLabels.format(
                Instant.parse("2026-11-17T12:00:00Z"),
                Instant.parse("2026-11-16T11:10:00Z"),
                "Europe/Berlin",
                "de",
                " Uhr",
            ),
        )
    }

    @Test fun qualifiesALaterDayInTheSameYear() {
        assertEquals(
            "Fr., 20.11., 09:00 Uhr",
            BoundaryLabels.format(
                Instant.parse("2026-11-20T08:00:00Z"),
                Instant.parse("2026-11-16T11:10:00Z"),
                "Europe/Berlin",
                "de",
                " Uhr",
            ),
        )
    }

    @Test fun usesTheCalendarYearAcrossNewYear() {
        assertEquals(
            "Fr., 01.01.2027, 09:00 Uhr",
            BoundaryLabels.format(
                Instant.parse("2027-01-01T08:00:00Z"),
                Instant.parse("2026-12-31T21:00:00Z"),
                "Europe/Berlin",
                "de",
                " Uhr",
            ),
        )
    }

    @Test fun qualifiesDifferentLocalDatesOnTheSameUtcDate() {
        assertEquals(
            "Di., 17.11., 00:30 Uhr",
            BoundaryLabels.format(
                Instant.parse("2026-11-16T23:30:00Z"),
                Instant.parse("2026-11-16T22:30:00Z"),
                "Europe/Berlin",
                "de",
                " Uhr",
            ),
        )
    }

    @Test fun keepsOneLocalDateCompactAcrossDifferentUtcDates() {
        assertEquals(
            "01:30 Uhr",
            BoundaryLabels.format(
                Instant.parse("2026-11-17T00:30:00Z"),
                Instant.parse("2026-11-16T23:10:00Z"),
                "Europe/Berlin",
                "de",
                " Uhr",
            ),
        )
    }

    @Test fun usesTheRoomZoneWestOfUtc() {
        assertEquals(
            "23:30 Uhr",
            BoundaryLabels.format(
                Instant.parse("2026-11-17T07:30:00Z"),
                Instant.parse("2026-11-17T06:10:00Z"),
                "America/Los_Angeles",
                "de",
                " Uhr",
            ),
        )
    }

    @Test fun formatsTheActualInstantAcrossSpringForward() {
        assertEquals(
            "03:10 Uhr",
            BoundaryLabels.format(
                Instant.parse("2026-03-29T01:10:00Z"),
                Instant.parse("2026-03-29T00:50:00Z"),
                "Europe/Berlin",
                "de",
                " Uhr",
            ),
        )
    }

    @Test fun qualifiesLeapDay() {
        assertEquals(
            "Di., 29.02., 09:00 Uhr",
            BoundaryLabels.format(
                Instant.parse("2028-02-29T08:00:00Z"),
                Instant.parse("2028-02-28T23:00:00+01:00"),
                "Europe/Berlin",
                "de",
                " Uhr",
            ),
        )
    }

    @Test fun placesTheRepeatedHourOffsetAfterTheSuffix() {
        assertEquals(
            "02:10 Uhr (UTC+01:00)",
            BoundaryLabels.format(
                Instant.parse("2026-10-25T01:10:00Z"),
                Instant.parse("2026-10-25T00:50:00Z"),
                "Europe/Berlin",
                "de",
                " Uhr",
            ),
        )
    }

    @Test fun omitsTheOffsetWhenTheFutureOverlapClockIsLater() {
        assertEquals(
            "02:50 Uhr",
            BoundaryLabels.format(
                Instant.parse("2026-10-25T01:50:00Z"),
                Instant.parse("2026-10-25T00:10:00Z"),
                "Europe/Berlin",
                "de",
                " Uhr",
            ),
        )
    }

    @Test fun omitsTheOffsetForAPastEarlierClock() {
        assertEquals(
            "02:10 Uhr",
            BoundaryLabels.format(
                Instant.parse("2026-10-25T00:10:00Z"),
                Instant.parse("2026-10-25T01:50:00Z"),
                "Europe/Berlin",
                "de",
                " Uhr",
            ),
        )
    }

    @Test fun normalizesDeDEToGerman() {
        assertEquals(
            "Di., 17.11., 13:00 Uhr",
            BoundaryLabels.format(
                Instant.parse("2026-11-17T12:00:00Z"),
                Instant.parse("2026-11-16T11:10:00Z"),
                "Europe/Berlin",
                "de-DE",
                " Uhr",
            ),
        )
    }

    @Test fun defaultsToAnEmptyClockSuffix() {
        assertEquals(
            "13:00",
            BoundaryLabels.format(
                Instant.parse("2026-11-16T12:00:00Z"),
                Instant.parse("2026-11-16T11:10:00Z"),
                "Europe/Berlin",
                "de",
            ),
        )
    }

    @Test fun preservesCallerSuffixBeforeOverlapOffset() {
        assertEquals(
            "02:10 clock (UTC+01:00)",
            BoundaryLabels.format(
                Instant.parse("2026-10-25T01:10:00Z"),
                Instant.parse("2026-10-25T00:50:00Z"),
                "Europe/Berlin",
                "en",
                " clock",
            ),
        )
    }

    @Test fun retainsDateYearAndClockForEn() {
        val label =
            BoundaryLabels.format(
                Instant.parse("2027-01-01T08:00:00Z"),
                Instant.parse("2026-12-31T21:00:00Z"),
                "Europe/Berlin",
                "en",
            )
        assertTrue(label.lowercase(java.util.Locale.ROOT).contains("fri"))
        assertTrue(label.contains("09:00"))
        assertTrue(label.contains("2027"))
        val dateNumbers =
            Regex("[0-9]+")
                .findAll(label.replace("09:00", "").replace("2027", ""))
                .map { it.value.toInt() }
                .toList()
        assertEquals(listOf(1, 1), dateNumbers)
        assertFalse(label.contains("UTC"))
    }

    @Test fun retainsDateYearAndClockForFr() {
        val label =
            BoundaryLabels.format(
                Instant.parse("2027-01-01T08:00:00Z"),
                Instant.parse("2026-12-31T21:00:00Z"),
                "Europe/Berlin",
                "fr",
            )
        assertTrue(label.lowercase(java.util.Locale.ROOT).contains("ven"))
        assertTrue(label.contains("09:00"))
        assertTrue(label.contains("2027"))
        val dateNumbers =
            Regex("[0-9]+")
                .findAll(label.replace("09:00", "").replace("2027", ""))
                .map { it.value.toInt() }
                .toList()
        assertEquals(listOf(1, 1), dateNumbers)
        assertFalse(label.contains("UTC"))
    }

    @Test fun retainsDateYearAndClockForIt() {
        val label =
            BoundaryLabels.format(
                Instant.parse("2027-01-01T08:00:00Z"),
                Instant.parse("2026-12-31T21:00:00Z"),
                "Europe/Berlin",
                "it",
            )
        assertTrue(label.lowercase(java.util.Locale.ROOT).contains("ven"))
        assertTrue(label.contains("09:00"))
        assertTrue(label.contains("2027"))
        val dateNumbers =
            Regex("[0-9]+")
                .findAll(label.replace("09:00", "").replace("2027", ""))
                .map { it.value.toInt() }
                .toList()
        assertEquals(listOf(1, 1), dateNumbers)
        assertFalse(label.contains("UTC"))
    }

    @Test fun retainsDateYearAndClockForEs() {
        val label =
            BoundaryLabels.format(
                Instant.parse("2027-01-01T08:00:00Z"),
                Instant.parse("2026-12-31T21:00:00Z"),
                "Europe/Berlin",
                "es",
            )
        assertTrue(label.lowercase(java.util.Locale.ROOT).contains("vie"))
        assertTrue(label.contains("09:00"))
        assertTrue(label.contains("2027"))
        val dateNumbers =
            Regex("[0-9]+")
                .findAll(label.replace("09:00", "").replace("2027", ""))
                .map { it.value.toInt() }
                .toList()
        assertEquals(listOf(1, 1), dateNumbers)
        assertFalse(label.contains("UTC"))
    }

    @Test fun fallsBackToEnglishForUnknownLanguage() {
        val boundary = Instant.parse("2026-11-17T12:00:00Z")
        val now = Instant.parse("2026-11-16T11:10:00Z")
        val english = BoundaryLabels.format(boundary, now, "Europe/Berlin", "en")
        assertTrue(english.lowercase(java.util.Locale.ROOT).contains("tue"))
        assertEquals(english, BoundaryLabels.format(boundary, now, "Europe/Berlin", "zz"))
    }
}
