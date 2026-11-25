package com.budgetbee.service;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * The "safe to spend" maths. Pure Java (no database, no Spring) so it is easy to test.
 *
 * Idea: money you can spend this month = income - money reserved for savings goals.
 * Divide what is left by the days left, and you get a daily allowance that updates itself
 * every time you spend. Spending more than the allowance is paid for out of your savings,
 * so we also show how many days that delays your goal.
 */
public final class PlanCalculator {

    private PlanCalculator() {}

    public record Inputs(BigDecimal incomeBasis, BigDecimal savingsReserve, BigDecimal spentMonth,
                         BigDecimal spentToday, int daysLeft, int daysInMonth) {}

    /** status: SETUP (no income known), NO_PLAN (reserve is bigger than income), GOOD, CAREFUL or OVER */
    public record Plan(BigDecimal incomeBasis, BigDecimal savingsReserve, BigDecimal pool, BigDecimal spentMonth,
                       BigDecimal spentToday, BigDecimal remaining, int daysLeft, BigDecimal dailyAllowance,
                       BigDecimal leftToday, BigDecimal projectedSpend, boolean onTrack, String status,
                       BigDecimal reservePerDay) {}

    public record WhatIf(BigDecimal amount, boolean affordable, BigDecimal leftTodayAfter, BigDecimal overBy,
                         BigDecimal newDailyAllowance, BigDecimal delayDays) {}

    public static Plan compute(Inputs in) {
        BigDecimal pool = in.incomeBasis().subtract(in.savingsReserve());
        BigDecimal remaining = pool.subtract(in.spentMonth());
        // What was left when today began. Keeps the daily allowance steady while you spend during the day.
        BigDecimal remainingAtStart = remaining.add(in.spentToday());
        int days = Math.max(1, in.daysLeft());

        BigDecimal allowance = remainingAtStart.signum() > 0
            ? remainingAtStart.divide(BigDecimal.valueOf(days), 0, RoundingMode.DOWN)
            : BigDecimal.ZERO;
        BigDecimal leftToday = allowance.subtract(in.spentToday());

        int elapsed = Math.max(1, in.daysInMonth() - days + 1);
        BigDecimal projected = in.spentMonth().multiply(BigDecimal.valueOf(in.daysInMonth()))
            .divide(BigDecimal.valueOf(elapsed), 0, RoundingMode.HALF_UP);
        boolean onTrack = projected.compareTo(pool) <= 0;

        String status;
        if (in.incomeBasis().signum() <= 0) status = "SETUP";
        else if (pool.signum() <= 0) status = "NO_PLAN";
        else if (leftToday.signum() < 0) status = "OVER";
        else if (leftToday.compareTo(allowance.multiply(new BigDecimal("0.25"))) < 0) status = "CAREFUL";
        else status = "GOOD";

        BigDecimal reservePerDay = in.savingsReserve()
            .divide(BigDecimal.valueOf(Math.max(1, in.daysInMonth())), 2, RoundingMode.HALF_UP);

        return new Plan(in.incomeBasis(), in.savingsReserve(), pool, in.spentMonth(), in.spentToday(), remaining,
            days, allowance, leftToday, projected, onTrack, status, reservePerDay);
    }

    /** "Can I afford this?" Shows what the spend does to today's limit, the rest of the month and your goal. */
    public static WhatIf whatIf(Plan p, BigDecimal amount) {
        BigDecimal leftAfter = p.leftToday().subtract(amount);
        BigDecimal usable = p.leftToday().max(BigDecimal.ZERO);
        BigDecimal overBy = amount.subtract(usable).max(BigDecimal.ZERO);

        BigDecimal newAllowance = BigDecimal.ZERO;
        if (p.daysLeft() > 1) {
            BigDecimal rest = p.remaining().subtract(amount);
            if (rest.signum() > 0) {
                newAllowance = rest.divide(BigDecimal.valueOf(p.daysLeft() - 1), 0, RoundingMode.DOWN);
            }
        }
        BigDecimal delay = null;
        if (p.reservePerDay().signum() > 0 && overBy.signum() > 0) {
            delay = overBy.divide(p.reservePerDay(), 1, RoundingMode.HALF_UP);
        }
        return new WhatIf(amount, overBy.signum() == 0, leftAfter, overBy, newAllowance, delay);
    }
}
