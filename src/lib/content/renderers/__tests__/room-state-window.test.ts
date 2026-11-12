import { expect, it, vi } from "vitest";
vi.mock("@/lib/calendar/source", () => ({ fetchResourceEvents: vi.fn(async () => []) }));
import { fetchResourceEvents } from "@/lib/calendar/source";
import { loadRoomModel } from "../room-booking";
it("loads seven local calendar days across daylight saving for native clients", async () => {
  await loadRoomModel({
    config: {
      providerId: "123e4567-e89b-42d3-a456-426614174000",
      roomConfig: {},
      timezone: "Europe/Paris",
    },
    now: new Date("2026-10-24T12:00:00Z"),
    calendarDays: 7,
  });
  expect(fetchResourceEvents).toHaveBeenLastCalledWith(
    expect.objectContaining({
      windowStart: new Date("2026-10-23T22:00:00Z"),
      windowEnd: new Date("2026-10-30T23:00:00Z"),
      ttlS: 60,
    })
  );
});
