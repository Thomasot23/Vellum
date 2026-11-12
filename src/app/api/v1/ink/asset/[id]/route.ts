// SPDX-License-Identifier: AGPL-3.0-or-later
import { createHash } from "node:crypto";
import type { NextRequest } from "next/server";
import { eq } from "drizzle-orm";
import { db, withDbRead } from "@/db";
import { assets } from "@/db/schema";
import { readDeviceRoom } from "@/lib/device-room";
import { matchesEntityTag, referencedAssetIds } from "@/lib/room-state";
import { UUID_RE } from "@/lib/validation";
import { errorResponse } from "@/lib/api-response";

export async function GET(request: NextRequest, { params }: { params: Promise<{ id: string }> }) {
  const context = await readDeviceRoom(request);
  if (!context.ok) return context.response;
  const { id } = await params;
  if (!UUID_RE.test(id)) return Response.json(errorResponse("Invalid asset ID"), { status: 400 });
  const { instance } = context;
  // Check current ownership before considering a conditional response.
  if (instance?.typeSlug !== "room-booking" || !referencedAssetIds(instance.config).includes(id)) {
    return Response.json(errorResponse("Asset not found"), { status: 404 });
  }
  const [asset] = await withDbRead(
    () => db.select().from(assets).where(eq(assets.id, id)).limit(1),
    "device-room-get-asset"
  );
  if (!asset) return Response.json(errorResponse("Asset not found"), { status: 404 });
  const etag =
    '"' + createHash("sha256").update(asset.mimeType).update(asset.data).digest("hex") + '"';
  const headers = {
    "Content-Type": asset.mimeType,
    "Cache-Control": "private, no-cache",
    "X-Content-Type-Options": "nosniff",
    "Content-Security-Policy": "default-src 'none'; style-src 'unsafe-inline'",
    "Content-Disposition": asset.mimeType === "image/svg+xml" ? "attachment" : "inline",
    ETag: etag,
  };
  if (matchesEntityTag(request.headers.get("if-none-match"), etag)) {
    return new Response(null, { status: 304, headers });
  }
  return new Response(new Uint8Array(asset.data), { headers });
}
