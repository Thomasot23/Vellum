// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (c) 2026 Fabian Schmieder. All rights reserved.
/**
 * The words a room display puts on the panel, per locale.
 *
 * One table for every room-booking layout, so that the state labels the frame
 * invariants look for (`FREI`, `BELEGT`, ...) cannot drift between the timeline
 * and the status layouts. A layout that wrote its own "Frei" would pass its own
 * tests and fail the operator, who reads the same word in two spellings.
 *
 * Strings that sit on the same frame as a state label must not CONTAIN one: the
 * state invariant matches by substring, so a hint such as "Raum freigeben" would
 * make every frame read as free. Nothing below does.
 */

import { de, fr, it, es, enUS } from "date-fns/locale";
import type { Locale as DateLocale } from "date-fns";

/* `en` is the fallback for every lookup, so the types carry that guarantee. */
export const DATE_LOCALES: Record<string, DateLocale> & { en: DateLocale } = {
  en: enUS,
  de,
  fr,
  it,
  es,
};

export interface BadgeText {
  free: string;
  busy: string;
}
export const BADGE_TEXT: Record<string, BadgeText> & { en: BadgeText } = {
  en: { free: "FREE", busy: "BUSY" },
  de: { free: "FREI", busy: "BELEGT" },
  fr: { free: "LIBRE", busy: "OCCUPÉ" },
  it: { free: "LIBERO", busy: "OCCUPATO" },
  es: { free: "LIBRE", busy: "OCUPADO" },
};

export const UPDATED_TEXT: Record<string, string> & { en: string } = {
  en: "updated",
  de: "aktualisiert",
  fr: "mis à jour",
  it: "aggiornato",
  es: "actualizado",
};

export function bookingLabel(locale: string): string {
  return (
    { en: "Book room", de: "Jetzt buchen", fr: "Réserver", it: "Prenota", es: "Reservar" }[
      locale
    ] ?? "Book room"
  );
}

/** Words only the status layouts use. */
export interface StatusText {
  /** "until 11:30" */
  until: string;
  /** Heading over the next booking while the room is still free. */
  next: string;
  /** Heading over the day's list. */
  today: string;
  /** The list is empty from here on. */
  noneLeft: string;
  /** Under the booking QR code. */
  scanHint: string;
  /** Appended to a clock time, where the locale says it. */
  clockSuffix: string;
}

export const STATUS_TEXT: Record<string, StatusText> & { en: StatusText } = {
  en: {
    until: "until",
    next: "Next",
    today: "Today",
    noneLeft: "No more bookings today",
    scanHint: "Scan the code to book",
    clockSuffix: "",
  },
  de: {
    until: "bis",
    next: "Als Nächstes",
    today: "Heute",
    noneLeft: "Heute keine weiteren Buchungen",
    scanHint: "Code mit dem Handy scannen",
    clockSuffix: " Uhr",
  },
  fr: {
    until: "jusqu’à",
    next: "Ensuite",
    today: "Aujourd’hui",
    noneLeft: "Aucune autre réservation aujourd’hui",
    scanHint: "Scannez le code pour réserver",
    clockSuffix: "",
  },
  it: {
    until: "fino alle",
    next: "Successivo",
    today: "Oggi",
    noneLeft: "Nessun’altra prenotazione oggi",
    scanHint: "Scansiona il codice per prenotare",
    clockSuffix: "",
  },
  es: {
    until: "hasta",
    next: "Siguiente",
    today: "Hoy",
    noneLeft: "No hay más reservas hoy",
    scanHint: "Escanea el código para reservar",
    clockSuffix: "",
  },
};

/** "FREI" → "Frei". The status layouts set the state as a word, not a badge. */
export function stateWord(locale: string, busy: boolean): string {
  const badge = BADGE_TEXT[locale] ?? BADGE_TEXT.en;
  const word = busy ? badge.busy : badge.free;
  return word.charAt(0) + word.slice(1).toLocaleLowerCase(locale);
}
