import { expect, it, vi } from "vitest";
import { nativeIngressLimiter, nativeDeviceLimiter } from "../rate-limit";
it("admits a normal shared-address fleet while retaining a per-device ceiling", () => {
  vi.useFakeTimers();
  vi.setSystemTime(new Date("2026-11-16T00:00:00Z"));
  try {
    for (let n = 0; n < 400; n++)
      expect(nativeIngressLimiter.check("fixture-shared-ip").allowed).toBe(true);
    for (let n = 0; n < 60; n++)
      expect(nativeDeviceLimiter.check("fixture-one").allowed).toBe(true);
    expect(nativeDeviceLimiter.check("fixture-one").allowed).toBe(false);
    expect(nativeDeviceLimiter.check("fixture-two").allowed).toBe(true);
  } finally {
    vi.useRealTimers();
  }
});
