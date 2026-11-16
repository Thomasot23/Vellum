import { beforeEach, describe, expect, it, vi } from "vitest";
import { NextRequest } from "next/server";

vi.mock("@/lib/auth", () => ({ validateToken: vi.fn() }));
vi.mock("@/lib/rate-limit", () => ({
  apiLimiter: {},
  nativeIngressLimiter: {},
  nativeDeviceLimiter: {},
  getClientIp: () => "local",
  applyRateLimit: vi.fn(() => null),
}));
vi.mock("@/lib/device-key-registration", () => ({ registerDeviceKey: vi.fn() }));
vi.mock("@/lib/logger", () => ({ log: { error: vi.fn(), warn: vi.fn(), info: vi.fn() } }));

import { validateToken } from "@/lib/auth";
import { registerDeviceKey } from "@/lib/device-key-registration";
import { POST } from "../route";

const publicKey = Buffer.alloc(32, 3).toString("base64");

function request(body: unknown, token = "test-token") {
  return new NextRequest("http://localhost/api/v1/ink/key", {
    method: "POST",
    headers: { "content-type": "application/json", "x-device-token": token },
    body: JSON.stringify(body),
  });
}

beforeEach(() => {
  vi.clearAllMocks();
  vi.mocked(validateToken).mockResolvedValue(true);
  vi.mocked(registerDeviceKey).mockResolvedValue("registered");
});

describe("POST /api/v1/ink/key", () => {
  it("registers the key of an authenticated device", async () => {
    const response = await POST(request({ mac: "AA:BB:CC:DD:EE:FF", publicKey }));
    expect(response.status).toBe(200);
    expect(registerDeviceKey).toHaveBeenCalledWith("AABBCCDDEEFF", publicKey);
  });

  it("refuses an invalid token before touching the device", async () => {
    vi.mocked(validateToken).mockResolvedValue(false);
    expect((await POST(request({ mac: "AABBCCDDEEFF", publicKey }))).status).toBe(401);
    expect(registerDeviceKey).not.toHaveBeenCalled();
  });

  it("refuses malformed keys", async () => {
    for (const bad of ["", "AAAA", publicKey.slice(1), `${publicKey}x`]) {
      expect((await POST(request({ mac: "AABBCCDDEEFF", publicKey: bad }))).status).toBe(400);
    }
    expect(registerDeviceKey).not.toHaveBeenCalled();
  });

  it("answers 409 when a key is already registered or the device is not approved", async () => {
    vi.mocked(registerDeviceKey).mockResolvedValue("conflict");
    expect((await POST(request({ mac: "AABBCCDDEEFF", publicKey }))).status).toBe(409);
  });

  it("treats a retry with the same key as success", async () => {
    vi.mocked(registerDeviceKey).mockResolvedValue("unchanged");
    const response = await POST(request({ mac: "AABBCCDDEEFF", publicKey }));
    expect(response.status).toBe(200);
    expect(await response.json()).toMatchObject({ data: { status: "unchanged" } });
  });

  it("does not leak details when registration fails", async () => {
    vi.mocked(registerDeviceKey).mockRejectedValue(new Error("db down"));
    const response = await POST(request({ mac: "AABBCCDDEEFF", publicKey }));
    expect(response.status).toBe(500);
    expect(await response.text()).not.toContain("db down");
  });
});
