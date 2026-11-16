// SPDX-License-Identifier: AGPL-3.0-or-later
/**
 * One-time key registration for devices enrolled without `/hello`.
 *
 * A voucher-enrolled device is approved the moment it first authenticates, so it
 * never registered an X25519 key and nothing can be sealed to it. Holding a valid
 * device token, it may register exactly one key. A registered key is never
 * replaced here: that would let a leaked token redirect every future secret.
 */

import { and, eq, isNull } from "drizzle-orm";
import { db, withDbTransaction } from "@/db";
import { auditLogs, devices } from "@/db/schema";
import { keyFingerprint } from "@/lib/key-fingerprint";

export type KeyRegistrationResult = "registered" | "unchanged" | "conflict";

export async function registerDeviceKey(
  mac: string,
  publicKey: string
): Promise<KeyRegistrationResult> {
  const fingerprint = keyFingerprint(publicKey);
  if (!fingerprint) return "conflict";
  return withDbTransaction(
    () =>
      db.transaction(
        async (tx) => {
          const [device] = await tx
            .select({ status: devices.status, publicKey: devices.publicKey })
            .from(devices)
            .where(eq(devices.mac, mac))
            .limit(1);
          if (device?.status !== "approved") return "conflict";
          // A retry after a lost response must succeed without changing anything.
          if (device.publicKey) return device.publicKey === publicKey ? "unchanged" : "conflict";
          const updated = await tx
            .update(devices)
            .set({ publicKey })
            .where(and(eq(devices.mac, mac), isNull(devices.publicKey)))
            .returning({ mac: devices.mac });
          if (updated.length === 0) return "conflict";
          await tx.insert(auditLogs).values({
            actorType: "device",
            actorId: mac,
            action: "device.key.register",
            targetType: "device",
            targetId: mac,
            metadata: { fingerprint },
          });
          return "registered";
        },
        { isolationLevel: "serializable" }
      ),
    "register-device-key"
  );
}
