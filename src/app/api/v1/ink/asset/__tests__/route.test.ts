import { beforeEach, describe, expect, it, vi } from "vitest";
import { NextRequest } from "next/server";
const state = vi.hoisted(() => ({ rows: [] as unknown[][] }));
vi.mock("@/db", () => ({
  db: {
    select: vi.fn(() => ({
      from: () => ({ where: () => ({ limit: () => state.rows.shift() ?? [] }) }),
    })),
  },
  withDbRead: async (fn: () => unknown) => fn(),
}));
vi.mock("@/lib/auth", () => ({ validateToken: vi.fn() }));
vi.mock("@/lib/rate-limit", () => ({
  apiLimiter: {},
  getClientIp: () => "local",
  applyRateLimit: () => null,
}));
import { validateToken } from "@/lib/auth";
import { GET } from "../[id]/route";
const id = "123e4567-e89b-42d3-a456-426614174000";
const device = { siteId: null, contentInstanceId: id };
const instance = { typeSlug: "room-booking", config: { wordmarkAssetId: id } };
const asset = { id, mimeType: "image/svg+xml", data: Buffer.from("<svg/>") };
const context = { params: Promise.resolve({ id }) };
const request = (etag?: string) =>
  new NextRequest("http://localhost/api/v1/ink/asset/" + id + "?mac=AABBCCDDEEFF", {
    headers: { "x-device-token": "test-token", ...(etag ? { "if-none-match": etag } : {}) },
  });
beforeEach(() => {
  vi.clearAllMocks();
  vi.mocked(validateToken).mockResolvedValue(true);
  state.rows = [[device], [instance], [asset]];
});
describe("GET device asset", () => {
  it("rejects an invalid token", async () => {
    vi.mocked(validateToken).mockResolvedValue(false);
    expect((await GET(request(), context)).status).toBe(401);
  });
  it("serves a referenced image with private revalidation and nosniff", async () => {
    const response = await GET(request(), context);
    expect(response.status).toBe(200);
    expect(await response.text()).toBe("<svg/>");
    expect(response.headers.get("content-type")).toBe("image/svg+xml");
    expect(response.headers.get("cache-control")).toBe("private, no-cache");
    expect(response.headers.get("x-content-type-options")).toBe("nosniff");
  });
  it("revalidates an authorized asset", async () => {
    const etag = (await GET(request(), context)).headers.get("etag")!;
    state.rows = [[device], [instance], [asset]];
    expect((await GET(request(etag), context)).status).toBe(304);
  });
  it("denies unreferenced assets even when the caller has a cached copy", async () => {
    state.rows = [[device], [{ ...instance, config: {} }]];
    expect((await GET(request("*"), context)).status).toBe(404);
  });
  it("allows the mark and content inherited from a site", async () => {
    state.rows = [
      [{ ...device, siteId: id, contentInstanceId: null }],
      [{ contentInstanceId: id }],
      [{ ...instance, config: { brandMarkAssetId: id } }],
      [asset],
    ];
    expect((await GET(request(), context)).status).toBe(200);
  });
  it("returns 404 for missing device, content or asset", async () => {
    for (const rows of [
      [[]],
      [[device], []],
      [[device], [instance], []],
      [[{ ...device, contentInstanceId: null }]],
    ]) {
      state.rows = rows;
      expect((await GET(request(), context)).status).toBe(404);
    }
  });
  it("rejects malformed asset ids", async () => {
    expect((await GET(request(), { params: Promise.resolve({ id: "bad" }) })).status).toBe(400);
  });
});
