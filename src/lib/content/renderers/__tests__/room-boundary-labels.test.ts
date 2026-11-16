import { describe, it, expect } from "vitest";
import { formatRoomBoundary } from "../room-boundary-labels";

describe("formatRoomBoundary", () => {
  it("keeps the same local day compact", () => {
    expect(
      formatRoomBoundary(
        new Date("2026-11-16T12:00:00Z"),
        new Date("2026-11-16T11:10:00Z"),
        "Europe/Berlin",
        "de",
        " Uhr"
      )
    ).toBe("13:00 Uhr");
  });

  it("qualifies tomorrow even when less than 24 hours away", () => {
    expect(
      formatRoomBoundary(
        new Date("2026-11-17T00:30:00Z"),
        new Date("2026-11-16T22:50:00Z"),
        "Europe/Berlin",
        "de",
        " Uhr"
      )
    ).toBe("Di., 17.11., 01:30 Uhr");
  });

  it("qualifies the next day", () => {
    expect(
      formatRoomBoundary(
        new Date("2026-11-17T12:00:00Z"),
        new Date("2026-11-16T11:10:00Z"),
        "Europe/Berlin",
        "de",
        " Uhr"
      )
    ).toBe("Di., 17.11., 13:00 Uhr");
  });

  it("qualifies a later day in the same year", () => {
    expect(
      formatRoomBoundary(
        new Date("2026-11-20T08:00:00Z"),
        new Date("2026-11-16T11:10:00Z"),
        "Europe/Berlin",
        "de",
        " Uhr"
      )
    ).toBe("Fr., 20.11., 09:00 Uhr");
  });

  it("uses the calendar year across New Year", () => {
    expect(
      formatRoomBoundary(
        new Date("2027-01-01T08:00:00Z"),
        new Date("2026-12-31T21:00:00Z"),
        "Europe/Berlin",
        "de",
        " Uhr"
      )
    ).toBe("Fr., 01.01.2027, 09:00 Uhr");
  });

  it("qualifies different local dates on the same UTC date", () => {
    expect(
      formatRoomBoundary(
        new Date("2026-11-16T23:30:00Z"),
        new Date("2026-11-16T22:30:00Z"),
        "Europe/Berlin",
        "de",
        " Uhr"
      )
    ).toBe("Di., 17.11., 00:30 Uhr");
  });

  it("keeps one local date compact across different UTC dates", () => {
    expect(
      formatRoomBoundary(
        new Date("2026-11-17T00:30:00Z"),
        new Date("2026-11-16T23:10:00Z"),
        "Europe/Berlin",
        "de",
        " Uhr"
      )
    ).toBe("01:30 Uhr");
  });

  it("uses the room zone west of UTC", () => {
    expect(
      formatRoomBoundary(
        new Date("2026-11-17T07:30:00Z"),
        new Date("2026-11-17T06:10:00Z"),
        "America/Los_Angeles",
        "de",
        " Uhr"
      )
    ).toBe("23:30 Uhr");
  });

  it("formats the actual instant across spring forward", () => {
    expect(
      formatRoomBoundary(
        new Date("2026-03-29T01:10:00Z"),
        new Date("2026-03-29T00:50:00Z"),
        "Europe/Berlin",
        "de",
        " Uhr"
      )
    ).toBe("03:10 Uhr");
  });

  it("qualifies leap day", () => {
    expect(
      formatRoomBoundary(
        new Date("2028-02-29T08:00:00Z"),
        new Date("2028-02-28T23:00:00+01:00"),
        "Europe/Berlin",
        "de",
        " Uhr"
      )
    ).toBe("Di., 29.02., 09:00 Uhr");
  });

  it("places the repeated hour offset after the suffix", () => {
    expect(
      formatRoomBoundary(
        new Date("2026-10-25T01:10:00Z"),
        new Date("2026-10-25T00:50:00Z"),
        "Europe/Berlin",
        "de",
        " Uhr"
      )
    ).toBe("02:10 Uhr (UTC+01:00)");
  });

  it("omits the offset when the future overlap clock is later", () => {
    expect(
      formatRoomBoundary(
        new Date("2026-10-25T01:50:00Z"),
        new Date("2026-10-25T00:10:00Z"),
        "Europe/Berlin",
        "de",
        " Uhr"
      )
    ).toBe("02:50 Uhr");
  });

  it("omits the offset for a past earlier clock", () => {
    expect(
      formatRoomBoundary(
        new Date("2026-10-25T00:10:00Z"),
        new Date("2026-10-25T01:50:00Z"),
        "Europe/Berlin",
        "de",
        " Uhr"
      )
    ).toBe("02:10 Uhr");
  });

  it("normalizes de-DE to German", () => {
    expect(
      formatRoomBoundary(
        new Date("2026-11-17T12:00:00Z"),
        new Date("2026-11-16T11:10:00Z"),
        "Europe/Berlin",
        "de-DE",
        " Uhr"
      )
    ).toBe("Di., 17.11., 13:00 Uhr");
  });

  it("defaults to an empty clock suffix", () => {
    expect(
      formatRoomBoundary(
        new Date("2026-11-16T12:00:00Z"),
        new Date("2026-11-16T11:10:00Z"),
        "Europe/Berlin",
        "de"
      )
    ).toBe("13:00");
  });

  it("preserves a caller suffix before the overlap offset", () => {
    expect(
      formatRoomBoundary(
        new Date("2026-10-25T01:10:00Z"),
        new Date("2026-10-25T00:50:00Z"),
        "Europe/Berlin",
        "en",
        " clock"
      )
    ).toBe("02:10 clock (UTC+01:00)");
  });

  it("retains date, calendar year and HH:mm for en", () => {
    const label = formatRoomBoundary(
      new Date("2027-01-01T08:00:00Z"),
      new Date("2026-12-31T21:00:00Z"),
      "Europe/Berlin",
      "en"
    );
    expect(label.toLowerCase()).toContain("fri");
    expect(label).toContain("09:00");
    expect(label).toContain("2027");
    expect(label.replace("09:00", "").replace("2027", "").match(/\d+/g)?.map(Number)).toEqual([
      1, 1,
    ]);
    expect(label).not.toContain("UTC");
  });

  it("retains date, calendar year and HH:mm for fr", () => {
    const label = formatRoomBoundary(
      new Date("2027-01-01T08:00:00Z"),
      new Date("2026-12-31T21:00:00Z"),
      "Europe/Berlin",
      "fr"
    );
    expect(label.toLowerCase()).toContain("ven");
    expect(label).toContain("09:00");
    expect(label).toContain("2027");
    expect(label.replace("09:00", "").replace("2027", "").match(/\d+/g)?.map(Number)).toEqual([
      1, 1,
    ]);
    expect(label).not.toContain("UTC");
  });

  it("retains date, calendar year and HH:mm for it", () => {
    const label = formatRoomBoundary(
      new Date("2027-01-01T08:00:00Z"),
      new Date("2026-12-31T21:00:00Z"),
      "Europe/Berlin",
      "it"
    );
    expect(label.toLowerCase()).toContain("ven");
    expect(label).toContain("09:00");
    expect(label).toContain("2027");
    expect(label.replace("09:00", "").replace("2027", "").match(/\d+/g)?.map(Number)).toEqual([
      1, 1,
    ]);
    expect(label).not.toContain("UTC");
  });

  it("retains date, calendar year and HH:mm for es", () => {
    const label = formatRoomBoundary(
      new Date("2027-01-01T08:00:00Z"),
      new Date("2026-12-31T21:00:00Z"),
      "Europe/Berlin",
      "es"
    );
    expect(label.toLowerCase()).toContain("vie");
    expect(label).toContain("09:00");
    expect(label).toContain("2027");
    expect(label.replace("09:00", "").replace("2027", "").match(/\d+/g)?.map(Number)).toEqual([
      1, 1,
    ]);
    expect(label).not.toContain("UTC");
  });

  it("falls back to English for an unknown language", () => {
    const boundary = new Date("2026-11-17T12:00:00Z");
    const now = new Date("2026-11-16T11:10:00Z");
    const english = formatRoomBoundary(boundary, now, "Europe/Berlin", "en");
    expect(english.toLowerCase()).toContain("tue");
    expect(formatRoomBoundary(boundary, now, "Europe/Berlin", "zz")).toBe(english);
  });
});
