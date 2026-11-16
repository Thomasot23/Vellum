// SPDX-License-Identifier: AGPL-3.0-or-later
import { format } from "date-fns";
import { TZDate } from "@date-fns/tz";
import { DATE_LOCALES } from "./room-booking-text";

/** Calendar-day context for a boundary, independent of layout and host timezone. */
export function formatRoomBoundary(
  boundary: Date,
  now: Date,
  timezone: string,
  locale: string,
  clockSuffix = ""
): string {
  const requested = locale.toLowerCase().split(/[-_]/)[0] ?? "en";
  const language = Object.hasOwn(DATE_LOCALES, requested) ? requested : "en";
  const target = new TZDate(boundary, timezone);
  const reference = new TZDate(now, timezone);
  const sameDay = format(target, "yyyy-MM-dd") === format(reference, "yyyy-MM-dd");
  const differentYear = target.getFullYear() !== reference.getFullYear();
  const clock = format(target, "HH:mm");
  let pattern = "HH:mm";
  if (!sameDay) {
    const date =
      language === "de"
        ? `dd.MM.${differentYear ? "yyyy" : ""}`
        : language === "en"
          ? `MM/dd${differentYear ? "/yyyy" : ""}`
          : `dd/MM${differentYear ? "/yyyy" : ""}`;
    pattern = `EEE, ${date}, HH:mm`;
  }
  const text =
    format(target, pattern, { locale: DATE_LOCALES[language] ?? DATE_LOCALES.en }) + clockSuffix;
  const repeatedHour =
    sameDay &&
    boundary.getTime() > now.getTime() &&
    target.getTimezoneOffset() > reference.getTimezoneOffset() &&
    clock <= format(reference, "HH:mm");
  return repeatedHour ? `${text} (UTC${format(target, "xxx")})` : text;
}
