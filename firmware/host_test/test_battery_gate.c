// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (c) 2026 Fabian Schmieder. All rights reserved.
#include "unity_min.h"
#include "battery_gate.h"

#define CRIT 10

void test_battery_gate_runs_on_healthy_cell(void)
{
    TEST_ASSERT_EQUAL_INT(BATTERY_GATE_RUN, battery_gate_decide(50, CRIT, false, false, false));
    TEST_ASSERT_EQUAL_INT(BATTERY_GATE_RUN, battery_gate_decide(CRIT, CRIT, false, false, false));
}

void test_battery_gate_unknown_level_runs(void)
{
    TEST_ASSERT_EQUAL_INT(BATTERY_GATE_RUN, battery_gate_decide(-1, CRIT, false, false, false));
}

void test_battery_gate_warns_below_threshold(void)
{
    TEST_ASSERT_EQUAL_INT(BATTERY_GATE_WARN_AND_SLEEP,
                          battery_gate_decide(CRIT - 1, CRIT, false, false, false));
    /* 0% is the deepest discharge, never a sentinel. */
    TEST_ASSERT_EQUAL_INT(BATTERY_GATE_WARN_AND_SLEEP,
                          battery_gate_decide(0, CRIT, false, false, false));
}

void test_battery_gate_external_power_always_runs(void)
{
    TEST_ASSERT_EQUAL_INT(BATTERY_GATE_RUN, battery_gate_decide(0, CRIT, true, true, true));
}

void test_battery_gate_brownout_warns_even_with_resting_level_ok(void)
{
    TEST_ASSERT_EQUAL_INT(BATTERY_GATE_WARN_AND_SLEEP,
                          battery_gate_decide(40, CRIT, false, true, false));
    TEST_ASSERT_EQUAL_INT(BATTERY_GATE_WARN_AND_SLEEP,
                          battery_gate_decide(-1, CRIT, false, true, false));
}

void test_battery_gate_interrupted_warning_after_brownout_stays_dark(void)
{
    TEST_ASSERT_EQUAL_INT(BATTERY_GATE_SLEEP_SILENT,
                          battery_gate_decide(3, CRIT, false, true, true));
    TEST_ASSERT_EQUAL_INT(BATTERY_GATE_SLEEP_SILENT,
                          battery_gate_decide(40, CRIT, false, true, true));
}

void test_battery_gate_interrupted_flag_alone_is_ignored(void)
{
    /* Without a brownout the flag is stale RTC memory or an older attempt; a
     * rested cell gets one more chance to draw the warning. */
    TEST_ASSERT_EQUAL_INT(BATTERY_GATE_WARN_AND_SLEEP,
                          battery_gate_decide(3, CRIT, false, false, true));
    TEST_ASSERT_EQUAL_INT(BATTERY_GATE_RUN, battery_gate_decide(60, CRIT, false, false, true));
}

void run_battery_gate_tests(void)
{
    RUN_TEST(test_battery_gate_runs_on_healthy_cell);
    RUN_TEST(test_battery_gate_unknown_level_runs);
    RUN_TEST(test_battery_gate_warns_below_threshold);
    RUN_TEST(test_battery_gate_external_power_always_runs);
    RUN_TEST(test_battery_gate_brownout_warns_even_with_resting_level_ok);
    RUN_TEST(test_battery_gate_interrupted_warning_after_brownout_stays_dark);
    RUN_TEST(test_battery_gate_interrupted_flag_alone_is_ignored);
}
