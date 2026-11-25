package com.budgetbee.service;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlanCalculatorTest {

    private static BigDecimal d(long v) { return BigDecimal.valueOf(v); }

    /** Income 30000, goal reserve 5000, spent 14820 this month (185 today), 13 days left of 30. */
    private PlanCalculator.Plan midMonth() {
        return PlanCalculator.compute(new PlanCalculator.Inputs(d(30000), d(5000), d(14820), d(185), 13, 30));
    }

    @Test
    void dailyAllowanceIsRemainingAtStartOfDayDividedByDaysLeft() {
        PlanCalculator.Plan p = midMonth();
        assertEquals(25000, p.pool().intValue());
        assertEquals(10180, p.remaining().intValue());
        assertEquals(797, p.dailyAllowance().intValue());   // (10180 + 185) / 13, rounded down
        assertEquals(612, p.leftToday().intValue());        // 797 - 185
        assertEquals("GOOD", p.status());
    }

    @Test
    void allowanceStaysSteadyWhileSpendingDuringTheDay() {
        PlanCalculator.Plan before = PlanCalculator.compute(new PlanCalculator.Inputs(d(30000), d(5000), d(14635), d(0), 13, 30));
        PlanCalculator.Plan after = PlanCalculator.compute(new PlanCalculator.Inputs(d(30000), d(5000), d(14820), d(185), 13, 30));
        assertEquals(before.dailyAllowance().intValue(), after.dailyAllowance().intValue());
    }

    @Test
    void projectedSpendFollowsCurrentPace() {
        PlanCalculator.Plan p = midMonth();
        assertEquals(24700, p.projectedSpend().intValue());  // 14820 / 18 days * 30
        assertTrue(p.onTrack());
    }

    @Test
    void spendingOverTheDailyLimitIsOverStatus() {
        PlanCalculator.Plan p = PlanCalculator.compute(new PlanCalculator.Inputs(d(30000), d(5000), d(15700), d(1065), 13, 30));
        assertTrue(p.leftToday().signum() < 0);
        assertEquals("OVER", p.status());
    }

    @Test
    void closeToTheLimitIsCareful() {
        // allowance 797; spend 700 today leaves 97, which is below 25% of the allowance
        PlanCalculator.Plan p = PlanCalculator.compute(new PlanCalculator.Inputs(d(30000), d(5000), d(14635 + 700), d(700), 13, 30));
        assertEquals("CAREFUL", p.status());
    }

    @Test
    void noIncomeMeansSetup() {
        PlanCalculator.Plan p = PlanCalculator.compute(new PlanCalculator.Inputs(d(0), d(0), d(500), d(100), 10, 30));
        assertEquals("SETUP", p.status());
    }

    @Test
    void reserveBiggerThanIncomeMeansNoPlan() {
        PlanCalculator.Plan p = PlanCalculator.compute(new PlanCalculator.Inputs(d(10000), d(12000), d(0), d(0), 10, 30));
        assertEquals("NO_PLAN", p.status());
    }

    @Test
    void allowanceNeverGoesNegative() {
        PlanCalculator.Plan p = PlanCalculator.compute(new PlanCalculator.Inputs(d(30000), d(5000), d(26000), d(0), 5, 30));
        assertEquals(0, p.dailyAllowance().intValue());
    }

    @Test
    void affordableSpendDoesNotTouchSavings() {
        PlanCalculator.WhatIf w = PlanCalculator.whatIf(midMonth(), d(500));
        assertTrue(w.affordable());
        assertEquals(112, w.leftTodayAfter().intValue());
        assertEquals(0, w.overBy().intValue());
        assertEquals(null, w.delayDays());
    }

    @Test
    void overspendingIsPaidFromSavingsAndDelaysTheGoal() {
        PlanCalculator.WhatIf w = PlanCalculator.whatIf(midMonth(), d(1000));
        assertFalse(w.affordable());
        assertEquals(388, w.overBy().intValue());            // 1000 - 612
        assertEquals(765, w.newDailyAllowance().intValue()); // (10180 - 1000) / 12
        assertEquals("2.3", w.delayDays().toPlainString());  // 388 / (5000 / 30)
    }

    @Test
    void noGoalMeansNoDelayNumber() {
        PlanCalculator.Plan p = PlanCalculator.compute(new PlanCalculator.Inputs(d(30000), d(0), d(14820), d(185), 13, 30));
        PlanCalculator.WhatIf w = PlanCalculator.whatIf(p, d(5000));
        assertEquals(null, w.delayDays());
    }
}
