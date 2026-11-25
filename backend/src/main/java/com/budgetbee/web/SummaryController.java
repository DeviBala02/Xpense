package com.budgetbee.web;

import com.budgetbee.model.Category;
import com.budgetbee.repo.CategoryRepository;
import com.budgetbee.security.CurrentUser;
import com.budgetbee.service.ExpenseService;
import com.budgetbee.service.IncomeService;
import com.budgetbee.service.Period;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/summary")
public class SummaryController {

    public record CategoryTotal(String name, String icon, BigDecimal amount, int percent) {}
    public record DayTotal(String date, String label, BigDecimal amount) {}
    public record Summary(String period, String label, BigDecimal total, BigDecimal previousTotal, BigDecimal today,
                          BigDecimal income, long pendingIncome,
                          List<CategoryTotal> byCategory, List<DayTotal> last7Days) {}

    private final ExpenseService expenses;
    private final CategoryRepository categories;
    private final IncomeService income;

    public SummaryController(ExpenseService expenses, CategoryRepository categories, IncomeService income) {
        this.expenses = expenses;
        this.categories = categories;
        this.income = income;
    }

    @GetMapping
    public Summary summary(@RequestParam(defaultValue = "month") String period) {
        Period.Range r = Period.resolve(period);
        BigDecimal total = expenses.total(r);

        String prevKey = switch (r.key()) {
            case "month" -> "last_month";
            case "week" -> "last_week";
            case "today" -> "yesterday";
            default -> null;
        };
        BigDecimal prev = prevKey == null ? null : expenses.total(Period.resolve(prevKey));

        Map<String, Category> byName = categories.findByUserId(CurrentUser.id()).stream()
            .collect(Collectors.toMap(c -> c.name, Function.identity()));
        List<CategoryTotal> cats = new ArrayList<>();
        for (Object[] row : expenses.byCategory(r)) {
            String name = (String) row[0];
            BigDecimal amt = (BigDecimal) row[1];
            int pct = total.signum() == 0 ? 0 : amt.multiply(BigDecimal.valueOf(100)).divide(total, 0, java.math.RoundingMode.HALF_UP).intValue();
            Category c = byName.get(name);
            cats.add(new CategoryTotal(name, c == null ? "🏷️" : c.icon, amt, pct));
        }

        List<DayTotal> days = new ArrayList<>();
        LocalDate today = LocalDate.now();
        for (int i = 6; i >= 0; i--) {
            LocalDate d = today.minusDays(i);
            days.add(new DayTotal(d.toString(), d.getDayOfWeek().toString().substring(0, 3),
                expenses.total(Period.resolve("today", d))));
        }
        return new Summary(r.key(), r.label(), total, prev, expenses.total(Period.resolve("today")),
            income.total(r), income.pendingCount(), cats, days);
    }
}
