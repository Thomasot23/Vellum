// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (c) 2026 Fabian Schmieder. All rights reserved.
/**
 * @file battery_gate.h
 * @brief Boot-time low-battery decision, kept pure so the host tests cover it.
 *
 * The low-battery screen is the last thing a display draws before it stops, and
 * on e-paper it has to survive until somebody reads it. A full refresh is the
 * heaviest load the cell sees without Wi-Fi, so the decision of whether to run,
 * to warn, or to stay dark must account for a cell that already failed to carry
 * a refresh (brownout) and for a warning that was cut off half-drawn.
 */
#pragma once

#include <stdbool.h>

typedef enum {
    /** Battery is fine or external power is present: boot normally. */
    BATTERY_GATE_RUN = 0,
    /** Draw the low-battery screen once, then sleep for the critical interval. */
    BATTERY_GATE_WARN_AND_SLEEP,
    /** Stay dark and sleep: the last warning attempt browned out mid-refresh. */
    BATTERY_GATE_SLEEP_SILENT,
} battery_gate_t;

/**
 * @param level_pct            Battery percentage, or negative when unavailable.
 * @param critical_pct         Shutdown threshold in percent.
 * @param external_power       USB / adapter power confirmed present.
 * @param brownout_reset       This boot is the result of a brownout reset.
 * @param warning_interrupted  The previous boot started drawing the low-battery
 *                             screen and never finished it.
 */
battery_gate_t battery_gate_decide(int level_pct, int critical_pct,
                                   bool external_power, bool brownout_reset,
                                   bool warning_interrupted);
