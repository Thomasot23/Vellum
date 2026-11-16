import { describe, expect, it } from "vitest";
import { helloRequestSchema } from "@/lib/validation";

describe("hello public key validation", () => {
  it("accepts a raw 32-byte base64 key or the legacy absent key", () => {
    expect(helloRequestSchema.safeParse({ mac: "AABBCCDDEEFF" }).success).toBe(true);
    expect(
      helloRequestSchema.safeParse({
        mac: "AABBCCDDEEFF",
        publicKey: Buffer.alloc(32, 4).toString("base64"),
      }).success
    ).toBe(true);
  });
  it("rejects malformed, truncated and oversized keys before persistence", () => {
    for (const publicKey of [
      "x",
      "AAAA",
      "!".repeat(44),
      "A".repeat(44),
      "A".repeat(45),
      Buffer.alloc(31, 4).toString("base64"),
    ]) {
      expect(helloRequestSchema.safeParse({ mac: "AABBCCDDEEFF", publicKey }).success).toBe(false);
    }
  });
});
