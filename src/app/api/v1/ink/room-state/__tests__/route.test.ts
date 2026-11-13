import { beforeEach, describe, expect, it, vi } from "vitest";
import { PgDialect } from "drizzle-orm/pg-core";
import type { SQL } from "drizzle-orm";
import { NextRequest } from "next/server";
import { readFileSync, writeFileSync, mkdirSync } from "node:fs";
import { roomBookingConfigSchema, type RoomModel } from "@/lib/content/renderers/room-booking";
import { roomStateSchema } from "@/lib/room-state";
import { resolveTheme } from "@/lib/theme";

const state = vi.hoisted(() => ({
  rows: [] as unknown[][],
  presence: {} as Record<string, unknown>,
  failPresence: false,
  presenceFilter: undefined as SQL | undefined,
}));
vi.mock("@/db", () => ({
  db: {
    update: () => ({
      set: (values: Record<string, unknown>) => ({
        where: (filter: SQL) => {
          state.presenceFilter = filter;
          if (state.failPresence) throw new Error("test write failure");
          state.presence = values;
        },
      }),
    }),
    select: vi.fn(() => ({
      from: () => ({ where: () => ({ limit: () => state.rows.shift() ?? [] }) }),
    })),
  },
  withDbWrite: async (fn: () => unknown) => fn(),
  withDbRead: async (fn: () => unknown) => fn(),
}));
vi.mock("@/lib/auth", () => ({ validateToken: vi.fn() }));
vi.mock("@/lib/rate-limit", () => ({
  apiLimiter: {},
  getClientIp: () => "local",
  applyRateLimit: vi.fn(() => null),
}));
vi.mock("@/lib/content/renderers/room-booking", async (original) => ({
  ...(await original<typeof import("@/lib/content/renderers/room-booking")>()),
  loadRoomModel: vi.fn(),
}));
vi.mock("@/lib/status-light", async (original) => ({
  ...(await original<typeof import("@/lib/status-light")>()),
  readStatusLightRecord: vi.fn(),
}));
import { readStatusLightRecord } from "@/lib/status-light";
import { validateToken } from "@/lib/auth";
import { loadRoomModel } from "@/lib/content/renderers/room-booking";
import { GET } from "../route";

const id = "123e4567-e89b-42d3-a456-426614174000";
const config = roomBookingConfigSchema.parse({
  providerId: id,
  roomConfig: {},
  roomName: "Meeting Room",
  layout: "status-panel",
});
const model: RoomModel = {
  config,
  timezone: "UTC",
  now: new Date("2026-11-12T10:00:00Z"),
  events: [
    {
      displaySubject: "Planning",
      organizer: "",
      startTime: new Date("2026-11-12T09:00:00Z"),
      endTime: new Date("2026-11-12T11:00:00Z"),
      isPrivate: false,
      showLockIcon: false,
    },
  ],
  bookingUrl: null,
  isRoomFree: false,
  offline: false,
  nextEventStart: null,
};
const device = {
  mac: "AABBCCDDEEFF",
  siteId: null,
  contentInstanceId: id,
  themeId: null,
  timezone: null,
  displayCaps: { model: "android-panel" },
};
const instance = { id, typeSlug: "room-booking", config };
function request(etag?: string, mac = "AA:BB:CC:DD:EE:FF") {
  return new NextRequest(`http://localhost/api/v1/ink/room-state?mac=${mac}`, {
    headers: { "x-device-token": "test-token", ...(etag ? { "if-none-match": etag } : {}) },
  });
}
beforeEach(() => {
  vi.clearAllMocks();
  state.rows = [[device], [instance], []];
  state.presence = {};
  state.failPresence = false;
  state.presenceFilter = undefined;
  vi.mocked(validateToken).mockResolvedValue(true);
  vi.mocked(loadRoomModel).mockResolvedValue(model);
  vi.mocked(readStatusLightRecord).mockResolvedValue(null);
});
describe("GET room-state", () => {
  it("names the status-light revision on full, cached and unassigned answers", async () => {
    const header = "x-status-light-revision";
    const first = await GET(request());
    expect(first.headers.get(header)).toBe("none");
    vi.mocked(readStatusLightRecord).mockResolvedValue({
      encrypted: "sealed",
      revision: "0123456789abcdef",
    });
    state.rows = [[device], [instance], []];
    const cached = await GET(request(first.headers.get("etag")!));
    expect(cached.status).toBe(304);
    expect(cached.headers.get(header)).toBe("0123456789abcdef");
    state.rows = [[{ ...device, contentInstanceId: null }]];
    const unassigned = await GET(request());
    expect(unassigned.status).toBe(204);
    expect(unassigned.headers.get(header)).toBe("0123456789abcdef");
  });
  it("omits the revision when it cannot be read, and never the secret", async () => {
    vi.mocked(readStatusLightRecord).mockRejectedValue(new Error("test read failure"));
    const response = await GET(request());
    expect(response.status).toBe(200);
    expect(response.headers.has("x-status-light-revision")).toBe(false);
    expect(await response.text()).not.toContain("sealed");
  });
  it("records authenticated presence and cadence for full and cached polls", async () => {
    const first = await GET(request());
    expect(new PgDialect().sqlToQuery(state.presenceFilter!).params).toEqual([device.mac]);
    expect(state.presence).toMatchObject({
      lastSeen: expect.any(Date),
      expectedIntervalS: 60,
      expectedDisplayState: "on",
      expectedDeviceState: "awake",
      expectedWakeAt: null,
    });
    state.presence = {};
    state.rows = [[device], [instance], []];
    expect((await GET(request(first.headers.get("etag")!))).status).toBe(304);
    expect(state.presence.lastSeen).toBeInstanceOf(Date);
  });
  it("still serves room state when the presence write fails", async () => {
    state.failPresence = true;
    expect((await GET(request())).status).toBe(200);
    state.rows = [[{ ...device, contentInstanceId: null }]];
    expect((await GET(request())).status).toBe(204);
  });
  it("does not record presence for an unauthenticated poll", async () => {
    vi.mocked(validateToken).mockResolvedValue(false);
    await GET(request());
    expect(state.presence).toEqual({});
  });
  it("rejects missing or malformed device identities", async () => {
    expect((await GET(request(undefined, ""))).status).toBe(400);
    expect((await GET(request(undefined, "bad"))).status).toBe(400);
  });
  it("rejects an invalid token before reading content", async () => {
    vi.mocked(validateToken).mockResolvedValue(false);
    expect((await GET(request())).status).toBe(401);
    expect(loadRoomModel).not.toHaveBeenCalled();
  });
  it("returns a contract-valid model and keeps hidden busy state", async () => {
    vi.mocked(loadRoomModel).mockResolvedValue({ ...model, events: [] });
    const response = await GET(request());
    expect(response.status).toBe(200);
    const body = roomStateSchema.parse(await response.json());
    expect(body.state.isRoomFree).toBe(false);
    expect(body.events).toEqual([]);
    expect(body.layout).toBe("status-panel");
    expect(response.headers.get("cache-control")).toBe("private, no-cache");
    expect(response.headers.get("etag")).toBeTruthy();
  });
  it("revalidates even with a different server clock and supports weak/list validators", async () => {
    const first = await GET(request());
    const etag = first.headers.get("etag")!;
    state.rows = [[device], [instance], []];
    const second = await GET(request(`"other", ${etag}`));
    expect(second.status).toBe(304);
    expect(await second.text()).toBe("");
    expect(second.headers.get("etag")).toBe(etag);
  });
  it("invalidates the tag when the provider goes offline", async () => {
    const etag = (await GET(request())).headers.get("etag")!;
    state.rows = [[device], [instance], []];
    vi.mocked(loadRoomModel).mockResolvedValue({ ...model, offline: true });
    const response = await GET(request(etag));
    expect(response.status).toBe(200);
    expect((await response.json()).state.providerUnavailable).toBe(true);
  });
  it("returns 404 for a removed device or content", async () => {
    state.rows = [[]];
    expect((await GET(request())).status).toBe(404);
    state.rows = [[device], []];
    expect((await GET(request())).status).toBe(404);
  });
  it("returns 204 when no content is assigned", async () => {
    state.rows = [[{ ...device, contentInstanceId: null }]];
    expect((await GET(request())).status).toBe(204);
    expect(state.presence.lastSeen).toBeInstanceOf(Date);
    expect(state.presence.expectedIntervalS).toBe(60);
  });
  it("refuses content types that are not rooms", async () => {
    state.rows = [[device], [{ ...instance, typeSlug: "image" }]];
    expect((await GET(request())).status).toBe(422);
    expect(loadRoomModel).not.toHaveBeenCalled();
  });
  it("inherits content, theme and timezone from the device site", async () => {
    state.rows = [
      [{ ...device, siteId: id, contentInstanceId: null }],
      [{ contentInstanceId: id, themeId: id, timezone: "Europe/Paris" }],
      [instance],
      [{ config: resolveTheme(7) }],
    ];
    expect((await GET(request())).status).toBe(200);
    expect(loadRoomModel).toHaveBeenCalledWith(
      expect.objectContaining({ config, timezone: "Europe/Paris" })
    );
  });
  it("returns the assigned full-color theme without rounding", async () => {
    const theme = { ...resolveTheme(7), headerBg: "#123456", freeBadge: "#234567" };
    state.rows = [[{ ...device, themeId: id }], [instance], [{ config: theme }]];
    const response = await GET(request());
    expect((await response.json()).theme).toEqual(theme);
  });
  it("does not bypass authentication for cached state", async () => {
    vi.mocked(validateToken).mockResolvedValue(false);
    expect((await GET(request("*"))).status).toBe(401);
  });
  it("returns a generic failure without leaking provider errors", async () => {
    vi.mocked(loadRoomModel).mockRejectedValue(new Error("private provider details"));
    const response = await GET(request());
    expect(response.status).toBe(500);
    expect(await response.text()).not.toContain("private provider details");
  });
  it("matches the Android contract fixture", async () => {
    const response = await GET(request());
    const actual = await response.json();
    const path = "android/app/src/test/resources/room-state.json";
    if (process.env.UPDATE_ROOM_STATE_FIXTURE === "1") {
      mkdirSync("android/app/src/test/resources", { recursive: true });
      writeFileSync(
        path,
        JSON.stringify({ ...actual, serverTime: model.now.toISOString() }, null, 2) + "\n"
      );
    }
    const fixture = JSON.parse(readFileSync(path, "utf8"));
    expect({ ...actual, serverTime: fixture.serverTime }).toEqual(roomStateSchema.parse(fixture));
  });
});
