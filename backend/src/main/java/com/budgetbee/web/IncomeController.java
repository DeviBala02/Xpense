package com.budgetbee.web;

import com.budgetbee.model.Income;
import com.budgetbee.model.IncomeCategory;
import com.budgetbee.repo.IncomeCategoryRepository;
import com.budgetbee.security.CurrentUser;
import com.budgetbee.service.IncomeService;
import com.budgetbee.service.ParserService;
import com.budgetbee.service.Period;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Money received: list, choose a category for pending credits, add by hand, manage income categories. */
@RestController
@RequestMapping("/api/income")
public class IncomeController {

    public record CategoryTotal(String name, String icon, BigDecimal amount, int percent) {}
    public record IncomeSummary(String label, BigDecimal total, long pendingCount, List<CategoryTotal> byCategory) {}

    private final IncomeService income;
    private final IncomeCategoryRepository categories;
    private final ParserService parser;

    public IncomeController(IncomeService income, IncomeCategoryRepository categories, ParserService parser) {
        this.income = income;
        this.categories = categories;
        this.parser = parser;
    }

    @GetMapping
    public List<Income> list(@RequestParam(defaultValue = "month") String period) {
        return income.list(Period.resolve(period));
    }

    /** Credits that still need a category. */
    @GetMapping("/pending")
    public List<Income> pending() {
        return income.pending();
    }

    @GetMapping("/summary")
    public IncomeSummary summary(@RequestParam(defaultValue = "month") String period) {
        Period.Range r = Period.resolve(period);
        BigDecimal total = income.total(r);
        Map<String, IncomeCategory> byName = categories.findByUserId(CurrentUser.id()).stream()
            .collect(Collectors.toMap(c -> c.name, Function.identity()));
        List<CategoryTotal> rows = new ArrayList<>();
        for (Object[] row : income.byCategory(r)) {
            String name = row[0] == null ? "Needs a category" : (String) row[0];
            BigDecimal amt = (BigDecimal) row[1];
            int pct = total.signum() == 0 ? 0
                : amt.multiply(BigDecimal.valueOf(100)).divide(total, 0, RoundingMode.HALF_UP).intValue();
            IncomeCategory c = byName.get(name);
            rows.add(new CategoryTotal(name, row[0] == null ? "❓" : (c == null ? "📥" : c.icon), amt, pct));
        }
        return new IncomeSummary(r.label(), total, income.pendingCount(), rows);
    }

    /** Manual entry: { amount, sender?, category?, note?, receivedAt? } */
    @PostMapping
    public Income create(@RequestBody Map<String, Object> body) {
        BigDecimal amount = new BigDecimal(String.valueOf(body.get("amount")));
        if (amount.signum() <= 0) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Amount must be above 0");
        String sender = text(body.get("sender"));
        String note = text(body.get("note"));
        IncomeCategory cat = income.byName(text(body.get("category"))).orElse(null);
        String when = text(body.get("receivedAt"));
        LocalDateTime at = (when == null || when.isBlank()) ? LocalDateTime.now() : LocalDateTime.parse(when);
        return income.add(amount, sender, cat, note, "MANUAL", note, at);
    }

    /** { category, remember? } - the user's choice for a credit. */
    @PutMapping("/{id}/category")
    public Income categorize(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        boolean remember = "true".equalsIgnoreCase(String.valueOf(body.get("remember")));
        return income.categorize(id, text(body.get("category")), remember);
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable Long id) {
        income.delete(id);
    }

    // ---- income categories ----

    @GetMapping("/categories")
    public List<IncomeCategory> allCategories() {
        return categories.findByUserId(CurrentUser.id());
    }

    @PostMapping("/categories")
    public IncomeCategory addCategory(@RequestBody IncomeCategory c) {
        if (c.name == null || c.name.isBlank()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Name is required");
        if (categories.findByUserIdAndNameIgnoreCase(CurrentUser.id(), c.name.trim()).isPresent())
            throw new ResponseStatusException(HttpStatus.CONFLICT, "A category with this name already exists");
        c.id = null;
        c.userId = CurrentUser.id();
        c.name = c.name.trim();
        if (c.icon == null || c.icon.isBlank()) c.icon = "📥";
        return categories.save(c);
    }

    @DeleteMapping("/categories/{id}")
    public void deleteCategory(@PathVariable Long id) {
        IncomeCategory c = categories.findByIdAndUserId(id, CurrentUser.id())
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Category not found"));
        if (income.categoryInUse(id))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Some credits use this category, so it cannot be deleted");
        try {
            categories.delete(c);
            categories.flush();
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "This category is in use by a saved sender rule, so it cannot be deleted");
        }
    }

    private static String text(Object o) {
        return o == null ? null : String.valueOf(o);
    }
}
