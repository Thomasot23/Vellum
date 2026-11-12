// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (c) 2026 Fabian Schmieder. All rights reserved.
/**
 * The status layouts held to the same five invariants as the timeline.
 *
 * Swept over all four panels, both orientations where the panel has them, the
 * booking shapes of the timeline sweep and three moments of the day. Branding is
 * exercised with a neutral mark built here, so the suite needs no asset store and
 * names no organisation.
 */

import { describe, it, expect } from "vitest";
import { loadImage, type Image } from "@napi-rs/canvas";
import { drawRoom, roomBookingConfigSchema, type RoomModel } from "../room-booking";
import { BADGE_TEXT, STATUS_TEXT, stateWord } from "../room-booking-text";
import { ROOM_LAYOUTS } from "../room-booking-types";
import { applyRoomPolicy } from "@/lib/calendar/policy";
import { resolveTheme, snapThemeToPalette, type Theme } from "@/lib/theme";
import { DISPLAY_REGISTRY, type ResolvedDisplay } from "@/lib/display";
import { recordingFactory, type Recording } from "@/lib/render/surface";
import { checkFrame, type FrameExpectation } from "@/lib/render/frame-invariants";
import type { CalendarEvent } from "@/lib/types";

type PanelName = "e1001" | "e1002" | "e1003" | "d1001";
const ALL_PANELS: PanelName[] = ["e1001", "e1002", "e1003", "d1001"];
const LAYOUTS = ["status-panel", "day-band"] as const;

function panel(name: PanelName, portrait = false): ResolvedDisplay {
  const reg = DISPLAY_REGISTRY[name];
  const reserved = reg.reservedPaletteIndices ?? [];
  const long = Math.max(reg.width, reg.height);
  const short = Math.min(reg.width, reg.height);
  return {
    width: portrait ? short : long,
    height: portrait ? long : short,
    palette: reg.palette,
    reservedPaletteIndices: reserved,
    format: reg.format,
    colorMode: reg.colorMode,
    colorCount: reg.palette.length - reserved.length,
    orientation: portrait ? "portrait" : "landscape",
  };
}

function themeFor(display: ResolvedDisplay, theme?: Theme): Theme {
  return snapThemeToPalette(theme ?? resolveTheme(display.colorCount), display.palette);
}

const DAY = "2026-08-24";
const at = (hhmm: string) => new Date(`${DAY}T${hhmm}:00.000Z`);
const ev = (
  from: string,
  to: string,
  subject: string,
  organizer: string,
  isPrivate = false
): CalendarEvent => ({ subject, organizer, startTime: at(from), endTime: at(to), isPrivate });

const SHAPES: { name: string; events: CalendarEvent[] }[] = [
  { name: "empty day", events: [] },
  {
    name: "one long booking",
    events: [ev("08:00", "11:00", "Projektbesprechung", "Maria Warnking")],
  },
  { name: "one short booking", events: [ev("09:00", "09:30", "Standup", "Lukas Thiele")] },
  {
    name: "two overlapping bookings",
    events: [
      ev("08:00", "10:00", "Jour fixe", "Maria Warnking"),
      ev("08:30", "10:30", "Vergabegespräch", "Prof. Dr. Fabian Schmieder"),
    ],
  },
  {
    name: "a private booking",
    events: [ev("08:00", "10:00", "Personalgespräch", "Maria Warnking", true)],
  },
  {
    name: "a full day",
    events: [
      ev("06:00", "07:00", "Frühbesprechung", "Ana de la Cruz"),
      ev("08:00", "09:30", "Projektbesprechung", "Maria Warnking"),
      ev("10:00", "11:00", "Standup", "Lukas Thiele"),
      ev("12:00", "13:30", "Vergabegespräch", "Prof. Dr. Fabian Schmieder"),
      ev("14:00", "16:00", "Klausur", "Ana de la Cruz"),
    ],
  },
];

const CLOCK = ["06:30", "08:45", "10:30", "13:00"];
const ROOM = "1J.1.18";
const STATE_LABELS = ["FREI", "BELEGT"];

const CONFIG = {
  providerId: "00000000-0000-4000-8000-000000000000",
  roomConfig: { resourceId: "1", resourceName: ROOM },
  roomName: ROOM,
  timezone: "Europe/Berlin",
  locale: "de",
  dateFormat: "PPPP" as const,
  policy: "Show All" as const,
  cacheTtlS: 120,
  timelineShiftH: 2,
  bookingQr: { visibility: "never" as const, source: "provider" as const },
};

function model(
  layout: (typeof LAYOUTS)[number],
  events: CalendarEvent[],
  now: Date,
  over: Partial<RoomModel> = {},
  config: Partial<RoomModel["config"]> = {}
): RoomModel {
  return {
    config: { ...CONFIG, layout, ...config },
    timezone: "Europe/Berlin",
    now,
    events: applyRoomPolicy(events, "Show All", "de"),
    bookingUrl: null,
    isRoomFree: !events.some((e) => e.startTime <= now && e.endTime > now),
    offline: false,
    ...over,
  };
}

function frame(m: RoomModel, display: ResolvedDisplay, theme?: Theme): Recording {
  const { factory, recordings } = recordingFactory();
  drawRoom(m, { theme: themeFor(display, theme), display, surface: factory });
  expect(recordings).toHaveLength(1);
  return recordings[0]!;
}

function expectSound(rec: Recording, expectation: FrameExpectation, where: string) {
  expect(checkFrame(rec, expectation), where).toEqual([]);
}

/**
 * The names a status frame owes.
 *
 * The panel layout lists every booking still ahead, so it owes all of them (the
 * shapes here never hold more than fit). The band names one booking in words, the
 * running one or else the next, so that is what it owes.
 */
function owed(layout: (typeof LAYOUTS)[number], events: CalendarEvent[], now: Date): string[] {
  const sorted = [...events].sort((a, b) => a.startTime.getTime() - b.startTime.getTime());
  const ahead = sorted.filter((e) => e.endTime > now);
  if (layout === "status-panel") return ahead.slice(0, 3).map((e) => e.organizer);
  const current = sorted.find((e) => e.startTime <= now && e.endTime > now);
  const next = sorted.find((e) => e.startTime > now);
  const featured = current ?? next;
  return featured ? [featured.organizer] : [];
}

describe("status layouts: every panel, every shape, through the day", () => {
  for (const layout of LAYOUTS)
    for (const p of ALL_PANELS)
      for (const shape of SHAPES)
        for (const hhmm of CLOCK)
          it(`${layout} / ${p} / ${shape.name} / ${hhmm}Z`, () => {
            const now = at(hhmm);
            const rec = frame(model(layout, shape.events, now), panel(p));
            expectSound(
              rec,
              { mustRead: [ROOM, ...owed(layout, shape.events, now)], exactlyOneOf: STATE_LABELS },
              `${layout} ${shape.name} at ${hhmm}Z on ${p}`
            );
          });
});

describe("status layouts: portrait", () => {
  for (const layout of LAYOUTS)
    for (const p of ["e1003", "d1001"] as const)
      it(`${layout} / ${p} portrait`, () => {
        const now = at("08:45");
        const events = SHAPES[3]!.events;
        const rec = frame(model(layout, events, now), panel(p, true));
        expectSound(
          rec,
          { mustRead: [ROOM, ...owed(layout, events, now)], exactlyOneOf: STATE_LABELS },
          `${layout} portrait on ${p}`
        );
      });
});

describe("status layouts: a booked room under 'Hide All' still reads as taken", () => {
  for (const layout of LAYOUTS)
    it(layout, () => {
      const now = at("08:30");
      const events = applyRoomPolicy(SHAPES[1]!.events, "Hide All", "de");
      const rec = frame(model(layout, [], now, { events, isRoomFree: false }), panel("e1003"));
      expectSound(rec, { mustRead: [ROOM], exactlyOneOf: STATE_LABELS }, `${layout} hidden`);
      expect(rec.texts.some((t) => t.text === stateWord("de", true))).toBe(true);
    });
});

/* A neutral mark: two bars. Nothing here may resemble anyone's trademark. */
async function neutralMark(): Promise<Image> {
  const svg =
    '<svg xmlns="http://www.w3.org/2000/svg" width="100" height="200" viewBox="0 0 100 200">' +
    '<rect x="0" y="0" width="40" height="200" fill="#123456"/>' +
    '<rect x="60" y="40" width="40" height="160" fill="#123456"/></svg>';
  return loadImage(Buffer.from(svg));
}

const BRAND_THEME: Theme = {
  name: "Brand test",
  headerBg: "#3A3A3A",
  headerText: "#FFFFFF",
  freeBadge: "#F2C200",
  busyBadge: "#3A3A3A",
  badgeText: "#FFFFFF",
  background: "#FFFFFF",
  eventBg: "#3A3A3A",
  slotText: "#FFFFFF",
  slotSecondary: "#3A3A3A",
  footerText: "#000000",
};

describe("status layouts: branding", () => {
  for (const layout of LAYOUTS)
    for (const p of ALL_PANELS)
      for (const hhmm of ["08:45", "10:30"])
        it(`${layout} / ${p} / ${hhmm}Z draws the wordmark and the mark`, async () => {
          const now = at(hhmm);
          const events = SHAPES[5]!.events;
          const m = model(
            layout,
            events,
            now,
            {
              brandMark: await neutralMark(),
              bookingUrl: "https://example.org/book/1",
            },
            {
              brandTitle: "Example\nUniversity",
              brandSubtitle: "Faculty of\nExamples",
              roomSubtitle: "Main Campus · Building A",
              bookingQr: { visibility: "always", source: "provider" },
            }
          );
          const rec = frame(m, panel(p), BRAND_THEME);
          expectSound(
            rec,
            {
              mustRead: [ROOM, "EXAMPLE", "UNIVERSITY", ...owed(layout, events, now).slice(0, 1)],
              exactlyOneOf: STATE_LABELS,
            },
            `${layout} branded on ${p}`
          );
          expect(rec.images, "the mark is drawn").toBeGreaterThanOrEqual(1);
        });
});

describe("status layouts: config", () => {
  it("accepts every layout the UI offers", () => {
    for (const layout of ROOM_LAYOUTS) {
      const parsed = roomBookingConfigSchema.parse({ ...CONFIG, layout });
      expect(parsed.layout).toBe(layout);
    }
  });

  it("treats a cleared brand mark field as no mark", () => {
    const parsed = roomBookingConfigSchema.parse({ ...CONFIG, brandMarkAssetId: "" });
    expect(parsed.brandMarkAssetId).toBeUndefined();
  });

  it("rejects a brand mark reference that is not an asset id", () => {
    expect(() =>
      roomBookingConfigSchema.parse({ ...CONFIG, brandMarkAssetId: "../../etc/passwd" })
    ).toThrow();
  });
});

describe("status text never contains a state label", () => {
  /* The state invariant matches by substring, so a hint that contained "frei"
   * would make every frame read as free. This keeps the table honest. */
  for (const [locale, text] of Object.entries(STATUS_TEXT))
    it(locale, () => {
      const badge = BADGE_TEXT[locale] ?? BADGE_TEXT.en;
      for (const value of Object.values(text))
        for (const label of [badge.free, badge.busy])
          expect(value.toLowerCase()).not.toContain(label.toLowerCase());
    });
});
