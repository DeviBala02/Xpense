package com.budgetbee.service;

import com.budgetbee.model.SavingsGoal;
import com.budgetbee.model.Settings;
import com.budgetbee.repo.SavingsGoalRepository;
import com.budgetbee.repo.SettingsRepository;
import com.budgetbee.security.CurrentUser;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * Gathers the numbers from the database and feeds them to PlanCalculator.
 * This is what powers "Safe to spend today".
 */
@Service
public class PlanService {

    public record PlanView(PlanCalculator.Plan plan, BigDecimal incomeReceived, BigDecimal expectedIncome,
                           String goalName, BigDecimal goalPerMonth, String topCategory, int topCategoryPercent) {}

    public record WhatIfView(PlanCalculator.WhatIf whatIf, String goalName) {}

    private final ExpenseService expenses;
    private final IncomeService income;
    private final SavingsGoalRepository goals;
    private final SettingsRepository settings;

    public PlanService(ExpenseService expenses, IncomeService income, SavingsGoalRepository goals, SettingsRepository settings) {
        this.expenses = expenses;
        this.income = income;
        this.goals = goals;
        this.settings = settings;
    }

    public Settings settings() {
        Long uid = CurrentUser.id();
        return settings.findByUserId(uid).orElseGet(() -> {
            Settings s = new Settings();
            s.userId = uid;
            return settings.save(s);
        });
    }

    public Settings setExpectedIncome(BigDecimal value) {
        Settings s = settings();
        s.expectedIncome = value == null || value.signum() < 0 ? BigDecimal.ZERO : value;
        return settings.save(s);
    }

    public PlanView today() {
        LocalDate today = LocalDate.now();
        Period.Range month = Period.resolve("month");
        Period.Range day = Period.resolve("today");

        BigDecimal received = income.total(month);
        BigDecimal expected = settings().expectedIncome;
        BigDecimal basis = received.max(expected);

        // Money set aside every month for savings goals that have a deadline.
        BigDecimal reserve = BigDecimal.ZERO;
        String goalName = null;
        BigDecimal goalPerMonth = null;
        LocalDate nearest = null;
        List<SavingsGoal> all = goals.findByUserId(CurrentUser.id());
        for (SavingsGoal g : all) {
            BigDecimal need = monthlyNeed(g, today);
            if (need.signum() > 0) {
                reserve = reserve.add(need);
                if (nearest == null || g.deadline.isBefore(nearest)) {
                    nearest = g.deadline;
                    goalName = g.name;
                    goalPerMonth = need;
                }
            }
        }

        int daysInMonth = today.lengthOfMonth();
        int daysLeft = daysInMonth - today.getDayOfMonth() + 1;
        BigDecimal spentMonth = expenses.total(month);
        PlanCalculator.Plan plan = PlanCalculator.compute(new PlanCalculator.Inputs(
            basis, reserve, spentMonth, expenses.total(day), daysLeft, daysInMonth));

        String topCat = null;
        int topPct = 0;
        List<Object[]> rows = expenses.byCategory(month);
        if (!rows.isEmpty() && spentMonth.signum() > 0) {
            topCat = (String) rows.get(0)[0];
            topPct = ((BigDecimal) rows.get(0)[1]).multiply(BigDecimal.valueOf(100))
                .divide(spentMonth, 0, RoundingMode.HALF_UP).intValue();
        }
        return new PlanView(plan, received, expected, goalName, goalPerMonth, topCat, topPct);
    }

    public WhatIfView whatIf(BigDecimal amount) {
        PlanView v = today();
        return new WhatIfView(PlanCalculator.whatIf(v.plan(), amount), v.goalName());
    }

    /** How much per month a goal needs to be finished by its deadline. Goals without a deadline reserve nothing. */
    static BigDecimal monthlyNeed(SavingsGoal g, LocalDate today) {
        if (g.deadline == null) return BigDecimal.ZERO;
        BigDecimal left = g.targetAmount.subtract(g.savedAmount);
        if (left.signum() <= 0) return BigDecimal.ZERO;
        long months = Math.max(1, ChronoUnit.MONTHS.between(today, g.deadline));
        return left.divide(BigDecimal.valueOf(months), 0, RoundingMode.UP);
    }
}
