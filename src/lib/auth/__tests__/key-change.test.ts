import { describe, expect, it, vi } from "vitest";
import { handleHello, type DeviceRecord, type DeviceRepository } from "@/lib/auth";

function repo(device: DeviceRecord | null, over: Partial<DeviceRepository> = {}): DeviceRepository {
  return {
    findByMac: vi.fn(async () => device),
    insertPending: vi.fn(async () => {}),
    updatePublicKey: vi.fn(async () => true),
    updateDisplayCaps: vi.fn(async () => {}),
    updateApproved: vi.fn(async () => {}),
    updateLastSeen: vi.fn(async () => {}),
    claimVoucherAndEnroll: vi.fn(async () => false),
    recordKeyChange: vi.fn(async () => {}),
    ...over,
  };
}

const first = Buffer.alloc(32, 1).toString("base64");
const second = Buffer.alloc(32, 2).toString("base64");
const pending = (publicKey: string | null): DeviceRecord => ({
  mac: "AABBCCDDEEFF",
  status: "pending",
  token: null,
  publicKey,
});

describe("handleHello key history", () => {
  it("records a replaced key of a pending device, then stores the new one", async () => {
    const r = repo(pending(first));
    expect(await handleHello("AABBCCDDEEFF", second, undefined, r)).toEqual({
      status: "pending",
    });
    expect(r.recordKeyChange).toHaveBeenCalledWith("AABBCCDDEEFF", first);
    expect(r.updatePublicKey).toHaveBeenCalledWith("AABBCCDDEEFF", second);
  });

  it("does not count a first registration or an unchanged key", async () => {
    const empty = repo(pending(null));
    await handleHello("AABBCCDDEEFF", first, undefined, empty);
    expect(empty.recordKeyChange).not.toHaveBeenCalled();
    const same = repo(pending(first));
    await handleHello("AABBCCDDEEFF", first, undefined, same);
    expect(same.recordKeyChange).not.toHaveBeenCalled();
  });

  it("still registers the key when recording the change fails", async () => {
    const r = repo(pending(first), {
      recordKeyChange: vi.fn(async () => {
        throw new Error("test write failure");
      }),
    });
    expect((await handleHello("AABBCCDDEEFF", second, undefined, r)).status).toBe("pending");
    expect(r.updatePublicKey).toHaveBeenCalledWith("AABBCCDDEEFF", second);
  });
});
