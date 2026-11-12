import { describe, expect, it } from "vitest";
import { buildRoomState, roomStateSchema, roomStateTag, referencedAssetIds } from "../room-state";
import { roomBookingConfigSchema, type RoomModel } from "../content/renderers/room-booking";
import { applyRoomPolicy } from "../calendar/policy";
import { resolveTheme } from "../theme";
const now = new Date("2026-11-12T10:00:00Z");
const event = {
  subject: "Private subject",
  organizer: "",
  startTime: now,
  endTime: new Date("2026-11-12T11:00:00Z"),
  isPrivate: true,
};
const config = roomBookingConfigSchema.parse({
  providerId: "123e4567-e89b-42d3-a456-426614174000",
  roomConfig: {},
  brandTitle: " Example\nBrand ",
  bookingQr: { visibility: "always", source: "custom", customUrl: "https://example.com/book" },
});
const model: RoomModel = {
  config,
  now,
  timezone: "UTC",
  events: [],
  isRoomFree: false,
  offline: false,
  bookingUrl: "https://example.com/book",
};
const build = (overrides: Partial<RoomModel> = {}) =>
  buildRoomState({ ...model, ...overrides }, resolveTheme(7), now);
describe("room state contract", () => {
  it.each(["Show All", "Hide Subject", "Hide All"] as const)(
    "exports only privacy-filtered fields for %s",
    (policy) => {
      const state = build({ events: applyRoomPolicy([event], policy) });
      expect(roomStateSchema.safeParse(state).success).toBe(true);
      expect(JSON.stringify(state)).not.toContain("Private subject");
      expect(state.state.isRoomFree).toBe(false);
      expect(state.events).toHaveLength(policy === "Hide All" ? 0 : 1);
      expect(state).not.toHaveProperty("config");
    }
  );
  it("sorts events without mutating the model and trims brand lines", () => {
    const earlier = { ...event, startTime: new Date("2026-11-12T09:00:00Z") };
    const events = applyRoomPolicy([event, earlier], "Hide Subject");
    const state = build({ events });
    expect(state.events.map((e) => e.start)).toEqual([
      earlier.startTime.toISOString(),
      now.toISOString(),
    ]);
    expect(events[0]!.startTime).toEqual(now);
    expect(state.brand.titleLines).toEqual(["Example", "Brand"]);
  });
  it("hides booking QR when offline and respects free-only visibility", () => {
    expect(build().booking.showQr).toBe(true);
    expect(build({ offline: true }).booking.showQr).toBe(false);
    const freeOnly = { ...config, bookingQr: { ...config.bookingQr, visibility: "free" as const } };
    expect(build({ config: freeOnly }).booking.showQr).toBe(false);
    expect(build({ config: freeOnly, isRoomFree: true }).booking.showQr).toBe(true);
  });
  it("tags state changes but ignores server time", () => {
    const state = build();
    const tag = roomStateTag(state);
    expect(
      roomStateTag({ ...state, serverTime: new Date(now.getTime() + 60000).toISOString() })
    ).toBe(tag);
    expect(roomStateTag({ ...state, layout: "day-band" })).not.toBe(tag);
    expect(roomStateTag({ ...state, state: { ...state.state, isRoomFree: true } })).not.toBe(tag);
    expect(roomStateTag({ ...state, theme: { ...state.theme, headerBg: "#123456" } })).not.toBe(
      tag
    );
  });
  it("exposes only branding asset references", () => {
    expect(referencedAssetIds(null)).toEqual([]);
    expect(
      referencedAssetIds({
        wordmarkAssetId: "a",
        brandMarkAssetId: "b",
        backgroundAssetId: "c",
        providerId: "d",
      })
    ).toEqual(["a", "b"]);
  });
});
