// SPDX-License-Identifier: AGPL-3.0-or-later
/**
 * Hands an enrolled panel the secret for its status light.
 *
 * The secret never travels in `room-state`, which the panel caches and hashes.
 * `room-state` only names the current revision in a header; when that changes,
 * the panel asks here. The answer is sealed to the public key the device enrolled
 * with, in the same envelope as its enrollment token, so a response that leaks
 * from a log or a proxy is useless to anyone but that panel. Its own HKDF context
 * keeps it from being accepted as an enrollment token, and vice versa.
 */
import type { NextRequest } from "next/server";
import { encryptForDevice, STATUS_LIGHT_INFO } from "@/lib/crypto";
import { readAuthenticatedDevice } from "@/lib/device-room";
import { errorResponse } from "@/lib/api-response";
import { log } from "@/lib/logger";
import { openStatusLightToken, readStatusLightRecord } from "@/lib/status-light";

const STATUS_LIGHT_VERSION = 1;

export async function GET(request: NextRequest) {
  const context = await readAuthenticatedDevice(request);
  if (!context.ok) return context.response;
  const { device } = context;
  const headers = { "Cache-Control": "no-store" };
  try {
    const record = await readStatusLightRecord(device.mac);
    if (!record) return new Response(null, { status: 204, headers });
    if (!device.publicKey) {
      return Response.json(errorResponse("Device has no enrollment key"), { status: 409, headers });
    }
    return Response.json(
      {
        version: STATUS_LIGHT_VERSION,
        revision: record.revision,
        encryptedToken: encryptForDevice(
          openStatusLightToken(record),
          device.publicKey,
          STATUS_LIGHT_INFO
        ),
      },
      { headers }
    );
  } catch {
    // Typically a changed ENCRYPTION_KEY: the entry exists but cannot be opened.
    log.warn("Status light secret unavailable", { mac: device.mac });
    return Response.json(errorResponse("Status light unavailable"), { status: 500, headers });
  }
}
