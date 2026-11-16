// SPDX-License-Identifier: AGPL-3.0-or-later
/**
 * POST /api/v1/ink/key — a voucher-enrolled device registers its X25519 key once.
 * Authenticated like every device POST; see `src/lib/device-key-registration.ts`.
 */
import type { NextRequest } from "next/server";
import { z } from "zod";
import { readDeviceRequest } from "@/lib/device-request";
import { errorResponse, okResponse } from "@/lib/api-response";
import { registerDeviceKey } from "@/lib/device-key-registration";
import { macSchema } from "@/lib/validation";
import { log } from "@/lib/logger";

const keyRegistrationSchema = z.object({
  mac: macSchema,
  publicKey: z.string().regex(/^[A-Za-z0-9+/]{43}=$/),
});

export async function POST(request: NextRequest) {
  const parsed = await readDeviceRequest(request, keyRegistrationSchema);
  if (!parsed.ok) return parsed.response;
  const { mac, publicKey } = parsed.data;
  try {
    const result = await registerDeviceKey(mac, publicKey);
    if (result === "conflict") {
      return Response.json(errorResponse("Key already registered"), { status: 409 });
    }
    return Response.json(okResponse({ status: result }));
  } catch (error) {
    log.error("Device key registration failed", {
      mac,
      errorType: error instanceof Error ? error.name : "unknown",
    });
    return Response.json(errorResponse("Key registration failed"), { status: 500 });
  }
}
