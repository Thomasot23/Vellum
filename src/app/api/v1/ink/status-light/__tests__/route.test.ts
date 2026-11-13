import { beforeEach, describe, expect, it, vi } from "vitest";
import crypto from "node:crypto";
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
  applyRateLimit: vi.fn(() => null),
}));
import { db } from "@/db";
import { validateToken } from "@/lib/auth";
import { sealStatusLightToken } from "@/lib/status-light";
import { GET } from "../route";

const SPKI_PREFIX = Buffer.from("302a300506032b656e032100", "hex");
const panel = crypto.generateKeyPairSync("x25519");
const publicKey = panel.publicKey
  .export({ type: "spki", format: "der" })
  .subarray(-32)
  .toString("base64");
const device = { mac: "AABBCCDDEEFF", publicKey };

/** What the panel does with the envelope; mirrors `encryptForDevice`. */
function openAsPanel(
  envelope: { ciphertext: string; nonce: string; serverPublicKey: string },
  info = "vellum-status-light-v1"
) {
  const serverKey = crypto.createPublicKey({
    key: Buffer.concat([SPKI_PREFIX, Buffer.from(envelope.serverPublicKey, "base64")]),
    format: "der",
    type: "spki",
  });
  const shared = crypto.diffieHellman({ privateKey: panel.privateKey, publicKey: serverKey });
  const key = crypto.hkdfSync("sha256", shared, Buffer.alloc(0), Buffer.from(info), 32);
  const sealed = Buffer.from(envelope.ciphertext, "base64");
  const decipher = crypto.createDecipheriv(
    "aes-256-gcm",
    Buffer.from(key),
    Buffer.from(envelope.nonce, "base64")
  );
  decipher.setAuthTag(sealed.subarray(sealed.length - 16));
  return Buffer.concat([
    decipher.update(sealed.subarray(0, sealed.length - 16)),
    decipher.final(),
  ]).toString("utf-8");
}

function request(mac = "AA:BB:CC:DD:EE:FF") {
  return new NextRequest(`http://localhost/api/v1/ink/status-light?mac=${mac}`, {
    headers: { "x-device-token": "test-token" },
  });
}

beforeEach(() => {
  vi.clearAllMocks();
  state.rows = [];
  vi.mocked(validateToken).mockResolvedValue(true);
});

describe("GET status-light", () => {
  it("rejects malformed identities and invalid tokens before reading anything", async () => {
    expect((await GET(request("bad"))).status).toBe(400);
    vi.mocked(validateToken).mockResolvedValue(false);
    expect((await GET(request())).status).toBe(401);
    expect(db.select).not.toHaveBeenCalled();
  });

  it("answers 204 when the device has no secret", async () => {
    state.rows = [[device], []];
    const response = await GET(request());
    expect(response.status).toBe(204);
    expect(response.headers.get("cache-control")).toBe("no-store");
  });

  it("seals the secret to the enrolled device and never sends it in the clear", async () => {
    const record = sealStatusLightToken("synthetic-vendor-token");
    state.rows = [[device], [{ value: record }]];
    const response = await GET(request());
    expect(response.status).toBe(200);
    expect(response.headers.get("cache-control")).toBe("no-store");
    const text = await response.text();
    expect(text).not.toContain("synthetic-vendor-token");
    const body = JSON.parse(text);
    expect(body).toMatchObject({ version: 1, revision: record.revision });
    expect(openAsPanel(body.encryptedToken)).toBe("synthetic-vendor-token");
    // Domain separation: not readable as an enrollment-token envelope.
    expect(() => openAsPanel(body.encryptedToken, "vellum-token-v1")).toThrow();
  });

  it("refuses a device that never enrolled a key", async () => {
    state.rows = [[{ ...device, publicKey: null }], [{ value: sealStatusLightToken("t") }]];
    expect((await GET(request())).status).toBe(409);
  });

  it("fails closed when the stored record cannot be opened", async () => {
    const record = sealStatusLightToken("synthetic-vendor-token");
    state.rows = [[device], [{ value: { ...record, encrypted: "AAAA" } }]];
    const response = await GET(request());
    expect(response.status).toBe(500);
    expect(await response.text()).not.toContain("synthetic-vendor-token");
  });
});
