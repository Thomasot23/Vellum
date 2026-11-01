// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (c) 2026 Fabian Schmieder. All rights reserved.
#include "battery_gate.h"

battery_gate_t battery_gate_decide(int level_pct, int critical_pct,
                                   bool external_power, bool brownout_reset,
                                   bool warning_interrupted)
{
    if (external_power) return BATTERY_GATE_RUN;

    /* The warning refresh itself pulled the rail under the brownout level.
     * Retrying straight away would loop reset -> refresh -> reset and drain the
     * cell to nothing. Stay dark; the next timer wake re-samples a rested cell
     * and may try once more. */
    if (brownout_reset && warning_interrupted) return BATTERY_GATE_SLEEP_SILENT;

    /* 0% is the deepest discharge, not a sentinel; negative means unknown. */
    if (level_pct >= 0 && level_pct < critical_pct) return BATTERY_GATE_WARN_AND_SLEEP;

    /* A brownout on battery means the cell could not carry the last load,
     * whatever the resting voltage claims now. Running Wi-Fi on it again would
     * brown out again, typically mid-way through a refresh, which is exactly how
     * a half-drawn screen ends up on the panel. Warn while a refresh is the
     * only load. */
    if (brownout_reset) return BATTERY_GATE_WARN_AND_SLEEP;

    return BATTERY_GATE_RUN;
}
