// SPDX-License-Identifier: AGPL-3.0-or-later
/**
 * The room as data, for displays that draw it themselves.
 *
 * An e-paper display gets pixels from `/render`; a display with its own renderer
 * (the Android panel app) gets this instead: the same model `/render` draws from,
 * after the privacy policy and with the theme the device would be drawn with. The
 * client decides layout details, never what is shown: anything the policy hides is
 * already gone here.
 *
 * The schema is the contract. The Android app parses exactly this shape, and both
 * sides test against the fixture in `android/app/src/test/resources/room-state.json`.
 * Change it only by adding optional fields, or by bumping `version`.
 */

import { createHash } from "node:crypto";
import { z } from "zod";
import { themeSchema, type Theme } from "@/lib/theme";
import type { RoomModel } from "@/lib/content/renderers/room-booking";
import { ROOM_LAYOUTS } from "@/lib/content/renderers/room-booking-types";
import { BOOKING_QR_VISIBILITIES, shouldShowBookingQr } from "@/lib/content/renderers/booking-qr";

export const ROOM_STATE_VERSION = 1;

const roomEventSchema = z.object({
  start: z.iso.datetime(),
  end: z.iso.datetime(),
  /** Already filtered by the room's privacy policy. */
  subject: z.string(),
  organizer: z.string(),
  isPrivate: z.boolean(),
});

export const roomStateSchema = z.object({
  version: z.literal(ROOM_STATE_VERSION),
  room: z.object({
    name: z.string(),
    subtitle: z.string().nullable(),
  }),
  locale: z.string(),
  timezone: z.string(),
  dateFormat: z.string(),
  layout: z.enum(ROOM_LAYOUTS),
  state: z.object({
    /** As the provider reported it, before privacy filtering. */
    isRoomFree: z.boolean(),
    /** The calendar provider could not be reached; `events` is empty. */
    providerUnavailable: z.boolean(),
    nextEventStart: z.iso.datetime().nullable(),
  }),
  events: z.array(roomEventSchema),
  theme: themeSchema,
  brand: z.object({
    titleLines: z.array(z.string()),
    subtitleLines: z.array(z.string()),
    wordmarkAssetId: z.uuid().nullable(),
    markAssetId: z.uuid().nullable(),
  }),
  booking: z.object({
    url: z.string().nullable(),
    visibility: z.enum(BOOKING_QR_VISIBILITIES),
    /** Whether the QR code belongs on screen right now. */
    showQr: z.boolean(),
  }),
  /** The server's clock when this was built. Not part of the ETag. */
  serverTime: z.iso.datetime(),
});

export type RoomState = z.infer<typeof roomStateSchema>;

function lines(text: string | undefined): string[] {
  return (text ?? "")
    .split("\n")
    .map((l) => l.trim())
    .filter(Boolean);
}

/** The model as the contract shape. Pure: same model and theme, same result. */
export function buildRoomState(model: RoomModel, theme: Theme, serverTime: Date): RoomState {
  const cfg = model.config;
  const url = model.bookingUrl;
  return {
    version: ROOM_STATE_VERSION,
    room: { name: cfg.roomName, subtitle: cfg.roomSubtitle?.trim() || null },
    locale: cfg.locale,
    timezone: model.timezone,
    dateFormat: cfg.dateFormat,
    layout: cfg.layout,
    state: {
      isRoomFree: model.isRoomFree,
      providerUnavailable: model.offline,
      nextEventStart: model.nextEventStart?.toISOString() ?? null,
    },
    events: [...model.events]
      .sort((a, b) => a.startTime.getTime() - b.startTime.getTime())
      .map((e) => ({
        start: e.startTime.toISOString(),
        end: e.endTime.toISOString(),
        subject: e.displaySubject,
        organizer: e.organizer,
        isPrivate: e.isPrivate,
      })),
    theme,
    brand: {
      titleLines: lines(cfg.brandTitle),
      subtitleLines: lines(cfg.brandSubtitle),
      wordmarkAssetId: cfg.wordmarkAssetId ?? null,
      markAssetId: cfg.brandMarkAssetId ?? null,
    },
    booking: {
      url,
      visibility: cfg.bookingQr.visibility,
      showQr:
        !model.offline && shouldShowBookingQr(cfg.bookingQr.visibility, model.isRoomFree, url),
    },
    serverTime: serverTime.toISOString(),
  };
}

/**
 * Entity tag over everything except `serverTime`, so a poll that would bring
 * nothing new is answered with 304 and costs the panel no redraw.
 */
export function roomStateTag(state: RoomState): string {
  const { serverTime: _ignored, ...stable } = state;
  void _ignored;
  const digest = createHash("sha256").update(JSON.stringify(stable)).digest("hex").slice(0, 16);
  return `W/"rs-${digest}"`;
}

/** The asset ids a room-state may hand to its device, and nothing else. */
export function referencedAssetIds(config: unknown): string[] {
  const c = (config ?? {}) as { wordmarkAssetId?: unknown; brandMarkAssetId?: unknown };
  return [c.wordmarkAssetId, c.brandMarkAssetId].filter(
    (v): v is string => typeof v === "string" && v.length > 0
  );
}

/** GET validators use weak comparison; authorization must precede this check. */
export function matchesEntityTag(header: string | null, etag: string): boolean {
  const opaque = etag.replace(/^W\//, "");
  return (
    header
      ?.split(",")
      .some(
        (candidate) => candidate.trim() === "*" || candidate.trim().replace(/^W\//, "") === opaque
      ) ?? false
  );
}
