package com.budgetbee.service;

import com.budgetbee.model.Budget;
import com.budgetbee.model.Category;
import com.budgetbee.repo.BudgetRepository;
import com.budgetbee.repo.ExpenseRepository;
import com.budgetbee.security.CurrentUser;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

@Service
public class BudgetService {

    public record BudgetStatus(Long id, String name, String category, String icon, BigDecimal limit,
                               BigDecimal spent, BigDecimal remaining, int percent, String state,
                               LocalDate startDate, LocalDate endDate, long daysLeft, BigDecimal dailyAllowance) {}

    private final BudgetRepository budgets;
    private final ExpenseRepository expenses;

    public BudgetService(BudgetRepository budgets, ExpenseRepository expenses) {
        this.budgets = budgets;
        this.expenses = expenses;
    }

    public Budget create(String name, Category category, BigDecimal amount, LocalDate start, LocalDate end) {
        Budget b = new Budget();
        b.userId = CurrentUser.id();
        b.category = category;
        b.amount = amount;
        b.startDate = start;
        b.endDate = end;
        b.name = (name == null || name.isBlank())
            ? (category == null ? "Overall budget" : category.name + " budget")
            : name;
        return budgets.save(b);
    }

    /** Deletes only if the budget belongs to the logged-in user. */
    public void delete(Long id) {
        budgets.findByIdAndUserId(id, CurrentUser.id()).ifPresent(budgets::delete);
    }

    /** All budgets that have not ended yet, each with how much is spent so far. */
    public List<BudgetStatus> statuses() {
        List<BudgetStatus> out = new ArrayList<>();
        for (Budget b : budgets.findByUserIdAndEndDateGreaterThanEqualOrderByEndDateAsc(CurrentUser.id(), LocalDate.now())) {
            out.add(status(b));
        }
        return out;
    }

    public BudgetStatus status(Budget b) {
        var from = b.startDate.atStartOfDay();
        var to = b.endDate.plusDays(1).atStartOfDay();
        BigDecimal spent = b.category == null
            ? expenses.sumBetween(b.userId, from, to)
            : expenses.sumByCategoryBetween(b.userId, b.category, from, to);
        BigDecimal remaining = b.amount.subtract(spent);
        int percent = b.amount.signum() == 0 ? 0
            : spent.multiply(BigDecimal.valueOf(100)).divide(b.amount, 0, RoundingMode.HALF_UP).intValue();
        String state = percent >= 100 ? "EXCEEDED" : percent >= 80 ? "WARNING" : "OK";
        LocalDate today = LocalDate.now();
        long daysLeft = Math.max(0, ChronoUnit.DAYS.between(today, b.endDate) + 1);
        BigDecimal daily = (remaining.signum() > 0 && daysLeft > 0)
            ? remaining.divide(BigDecimal.valueOf(daysLeft), 0, RoundingMode.DOWN)
            : BigDecimal.ZERO;
        return new BudgetStatus(b.id, b.name,
            b.category == null ? "Overall" : b.category.name,
            b.category == null ? "🎯" : b.category.icon,
            b.amount, spent, remaining, percent, state, b.startDate, b.endDate, daysLeft, daily);
    }

    /** Warnings to show right after an expense lands in this category. */
    public List<String> alertsFor(Category cat) {
        List<String> alerts = new ArrayList<>();
        for (Budget b : budgets.findByUserIdAndEndDateGreaterThanEqualOrderByEndDateAsc(CurrentUser.id(), LocalDate.now())) {
            if (b.startDate.isAfter(LocalDate.now())) continue;
            if (b.category != null && !b.category.id.equals(cat.id)) continue;
            BudgetStatus s = status(b);
            if (s.state().equals("EXCEEDED")) {
                alerts.add("🚨 " + s.name() + " is over the limit: " + Money.fmt(s.spent()) + " spent of " + Money.fmt(s.limit()) + ".");
            } else if (s.state().equals("WARNING")) {
                alerts.add("⚠️ " + s.name() + " is " + s.percent() + "% used (" + Money.fmt(s.spent()) + " of " + Money.fmt(s.limit()) + ").");
            }
        }
        return alerts;
    }
}
