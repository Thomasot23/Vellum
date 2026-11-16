import { describe, expect, it, vi } from "vitest";
import { handleHello, type DeviceRecord, type DeviceRepository } from "../index";

const first = Buffer.alloc(32, 4).toString("base64");
const replacement = Buffer.alloc(32, 5).toString("base64");

function scenario(status = "pending", token: string | null = null) {
  const device: DeviceRecord = {
    mac: "AABBCCDDEEFF",
    status,
    token,
    publicKey: first,
  };
  const writer = vi.fn(async (_mac: string, publicKey: string) => {
    if (device.status !== "pending") return false;
    device.publicKey = publicKey;
    return true;
  });
  const history = vi.fn(async () => {});
  const repo: DeviceRepository = {
    findByMac: async () => ({ ...device }),
    insertPending: async () => {},
    updateLastSeen: async () => {},
    updateDisplayCaps: async () => {},
    updatePublicKey: writer,
    recordKeyChange: history,
    updateApproved: async () => {},
    claimVoucherAndEnroll: async () => false,
  };
  return { device, repo, writer, history };
}

describe("hello and approval interleavings", () => {
  it("keeps the approved key when approval wins after hello's pending read", async () => {
    const s = scenario();
    s.repo.updateLastSeen = async () => {
      s.device.status = "approved";
      s.device.token = "synthetic-test-token";
    };
    const reply = await handleHello(s.device.mac, replacement, undefined, s.repo);
    expect(s.device.publicKey).toBe(first);
    expect(s.device.status).toBe("approved");
    expect(reply.encryptedToken).toBeUndefined();
    expect(reply.token).toBeUndefined();
    expect(s.history).not.toHaveBeenCalled();
  });

  it("never starts a key replacement for a rejected device", async () => {
    const s = scenario("rejected");
    expect(await handleHello(s.device.mac, replacement, undefined, s.repo)).toEqual({
      status: "rejected",
    });
    expect(s.writer).not.toHaveBeenCalled();
    expect(s.history).not.toHaveBeenCalled();
    expect(s.device.publicKey).toBe(first);
  });

  it("does not treat an approved row with a missing token as pending", async () => {
    const s = scenario("approved");
    const reply = await handleHello(s.device.mac, replacement, undefined, s.repo);
    expect(s.writer).not.toHaveBeenCalled();
    expect(s.device.publicKey).toBe(first);
    expect(reply.encryptedToken).toBeUndefined();
  });

  it("records a successful pending replacement and tolerates history failure", async () => {
    const s = scenario();
    s.repo.recordKeyChange = vi.fn(async () => {
      throw new Error("synthetic-history-failure");
    });
    expect(await handleHello(s.device.mac, replacement, undefined, s.repo)).toEqual({
      status: "pending",
    });
    expect(s.device.publicKey).toBe(replacement);
    expect(s.repo.recordKeyChange).toHaveBeenCalledWith(s.device.mac, first);
  });
});
