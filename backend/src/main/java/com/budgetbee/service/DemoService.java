package com.budgetbee.service;

import com.budgetbee.model.Category;
import com.budgetbee.model.SavingsGoal;
import com.budgetbee.repo.SavingsGoalRepository;
import com.budgetbee.security.CurrentUser;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Random;

/** Fills an empty app with a month of believable sample data so you can try or demo it straight away. */
@Service
public class DemoService {

    private record Item(String category, String note, int min, int max) {}

    private static final Item[] ITEMS = {
        new Item("Snacks", "tea", 15, 40), new Item("Snacks", "samosa", 25, 70), new Item("Snacks", "juice", 30, 90),
        new Item("Food", "lunch", 90, 220), new Item("Food", "dinner", 120, 360), new Item("Food", "biryani", 180, 320),
        new Item("Travel", "auto fare", 40, 160), new Item("Travel", "bus", 15, 60),
        new Item("Groceries", "vegetables", 90, 380), new Item("Groceries", "milk", 30, 80),
        new Item("Shopping", "clothes", 400, 1200), new Item("Bills", "recharge", 199, 299),
    };

    private final ExpenseService expenses;
    private final IncomeService income;
    private final BudgetService budgets;
    private final ParserService parser;
    private final PlanService plan;
    private final SavingsGoalRepository goals;

    public DemoService(ExpenseService expenses, IncomeService income, BudgetService budgets, ParserService parser,
                       PlanService plan, SavingsGoalRepository goals) {
        this.expenses = expenses;
        this.income = income;
        this.budgets = budgets;
        this.parser = parser;
        this.plan = plan;
        this.goals = goals;
    }

    public String seed() {
        if (!expenses.recent().isEmpty()) return "Sample data was not loaded because you already have expenses.";

        LocalDate today = LocalDate.now();
        LocalDate first = today.withDayOfMonth(1);
        Random rnd = new Random(7);

        plan.setExpectedIncome(new BigDecimal("30000"));

        income.add(new BigDecimal("25000"), "ACME Pvt Ltd", income.byName("Salary").orElse(null),
            "Salary", "MANUAL", "Salary", first.atTime(10, 0));
        LocalDate pocketDay = first.plusDays(Math.min(4, today.getDayOfMonth() - 1));
        income.add(new BigDecimal("3000"), "Amma", income.byName("Pocket money").orElse(null),
            "Pocket money", "MANUAL", "Pocket money", pocketDay.atTime(9, 10));
        income.add(new BigDecimal("1200"), "Karthik", null, "₹1,200 credited by UPI from Karthik", "SMS",
            "₹1,200 credited by UPI from Karthik", today.minusDays(1).atTime(10, 15));

        SavingsGoal g = new SavingsGoal();
        g.userId = CurrentUser.id();
        g.name = "New laptop";
        g.targetAmount = new BigDecimal("40000");
        g.savedAmount = new BigDecimal("10000");
        g.deadline = today.plusMonths(6);
        goals.save(g);

        Period.Range month = Period.resolve("month");
        budgets.create(null, parser.byName("Snacks").orElse(null), new BigDecimal("2000"), month.startDate(), month.endDate());
        budgets.create(null, parser.byName("Food").orElse(null), new BigDecimal("6000"), month.startDate(), month.endDate());

        for (LocalDate d = first; !d.isAfter(today); d = d.plusDays(1)) {
            int count = 2 + rnd.nextInt(3);
            for (int i = 0; i < count; i++) {
                Item it = ITEMS[rnd.nextInt(ITEMS.length)];
                // rare, bigger items so the month looks natural
                if ((it.category().equals("Shopping") || it.category().equals("Bills")) && rnd.nextInt(6) != 0) continue;
                int amount = it.min() + rnd.nextInt(it.max() - it.min() + 1);
                LocalDateTime when = d.atTime(LocalTime.of(8 + rnd.nextInt(13), rnd.nextInt(60)));
                if (when.isAfter(LocalDateTime.now())) when = LocalDateTime.now().minusMinutes(1 + rnd.nextInt(120));
                Category c = parser.byName(it.category()).orElseGet(parser::others);
                expenses.add(BigDecimal.valueOf(amount), c, null, it.note(), "MANUAL", it.note(), when);
            }
        }
        return "Sample data loaded: one month of expenses, income, two budgets and a savings goal.";
    }
}
