// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (c) 2026 Fabian Schmieder. All rights reserved.
/**
 * Room-booking shared constants — safe to import from client components.
 */

/** Room privacy policies — SSOT for schema + UI */
export const ROOM_POLICIES = ["Show All", "Hide Subject", "Hide All"] as const;

/** Room-booking layouts — SSOT for schema + UI. */
export const ROOM_LAYOUTS = ["timeline", "stacked", "status-panel", "day-band"] as const;
export type RoomLayout = (typeof ROOM_LAYOUTS)[number];

/** The layouts that can carry a brand title, subtitle and mark. */
export const STATUS_LAYOUTS: readonly RoomLayout[] = ["status-panel", "day-band"];
