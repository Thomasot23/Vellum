// SPDX-License-Identifier: AGPL-3.0-or-later
import type { NextRequest } from "next/server";
import { eq } from "drizzle-orm";
import { db, withDbRead } from "@/db";
import { devices, contentInstances } from "@/db/schema";
import { validateToken } from "@/lib/auth";
import { errorResponse, validateRequest } from "@/lib/api-response";
import { renderQuerySchema } from "@/lib/validation";
import { apiLimiter, applyRateLimit, getClientIp } from "@/lib/rate-limit";
import { settingsForDevice } from "@/lib/settings/for-device";

/** Shared authenticated lookup, including site-inherited assignments. */
export async function readDeviceRoom(request: NextRequest) {
  const limited = applyRateLimit(apiLimiter, getClientIp(request));
  if (limited) return { ok: false as const, response: limited };
  const validation = validateRequest(renderQuerySchema, {
    mac: request.nextUrl.searchParams.get("mac"),
  });
  if (!validation.success) return { ok: false as const, response: validation.response };
  if (!(await validateToken(validation.data.mac, request.headers.get("x-device-token") ?? ""))) {
    return {
      ok: false as const,
      response: Response.json(errorResponse("Unauthorized"), { status: 401 }),
    };
  }
  const [device] = await withDbRead(
    () => db.select().from(devices).where(eq(devices.mac, validation.data.mac)).limit(1),
    "device-room-get-device"
  );
  if (!device)
    return {
      ok: false as const,
      response: Response.json(errorResponse("Device not found"), { status: 404 }),
    };
  const settings = await settingsForDevice(device);
  const contentId = settings.values.contentInstanceId;
  const [instance] = contentId
    ? await withDbRead(
        () => db.select().from(contentInstances).where(eq(contentInstances.id, contentId)).limit(1),
        "device-room-get-content"
      )
    : [];
  if (contentId && !instance)
    return {
      ok: false as const,
      response: Response.json(errorResponse("Content not found"), { status: 404 }),
    };
  return { ok: true as const, device, settings, instance };
}
