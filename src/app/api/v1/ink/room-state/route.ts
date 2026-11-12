// SPDX-License-Identifier: AGPL-3.0-or-later
import type { NextRequest } from "next/server";
import { readDeviceRoom } from "@/lib/device-room";
import { resolveDeviceTheme } from "@/lib/device-theme";
import { completeDisplayCaps, mergeReportedCaps, resolveDisplayCaps } from "@/lib/display";
import { loadRoomModel } from "@/lib/content/renderers/room-booking";
import { buildRoomState, matchesEntityTag, roomStateTag } from "@/lib/room-state";
import { errorResponse } from "@/lib/api-response";

export async function GET(request: NextRequest) {
  const context = await readDeviceRoom(request);
  if (!context.ok) return context.response;
  const { device, settings, instance } = context;
  const headers = { "Cache-Control": "private, no-cache" };
  if (!instance) return new Response(null, { status: 204, headers });
  if (instance.typeSlug !== "room-booking") {
    return Response.json(errorResponse("Assigned content is not a room"), { status: 422, headers });
  }
  try {
    const stored = device.displayCaps as { model?: string } | null;
    const completed = completeDisplayCaps(stored, stored?.model ?? "unknown") ?? stored;
    const { caps } = mergeReportedCaps(completed, request.headers.get("x-display-caps"));
    const orientation =
      device.orientationOverride === "portrait" || device.orientationOverride === "landscape"
        ? device.orientationOverride
        : undefined;
    const display = resolveDisplayCaps(caps, orientation);
    const theme = await resolveDeviceTheme(settings.values.themeId, display);
    const now = new Date();
    const model = await loadRoomModel({
      config: instance.config,
      now,
      timezone: settings.values.timezone ?? undefined,
    });
    const state = buildRoomState(model, theme, now);
    const etag = roomStateTag(state);
    const cacheHeaders = { ...headers, ETag: etag };
    if (matchesEntityTag(request.headers.get("if-none-match"), etag)) {
      return new Response(null, { status: 304, headers: cacheHeaders });
    }
    return Response.json(state, { headers: cacheHeaders });
  } catch {
    return Response.json(errorResponse("Room state unavailable"), { status: 500, headers });
  }
}
