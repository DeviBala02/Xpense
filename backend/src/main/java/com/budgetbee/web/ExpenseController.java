package com.budgetbee.web;

import com.budgetbee.model.Expense;
import com.budgetbee.service.ExpenseService;
import com.budgetbee.service.ParserService;
import com.budgetbee.service.Period;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/expenses")
public class ExpenseController {

    private final ExpenseService expenses;
    private final ParserService parser;

    public ExpenseController(ExpenseService expenses, ParserService parser) {
        this.expenses = expenses;
        this.parser = parser;
    }

    @GetMapping
    public List<Expense> list(@RequestParam(defaultValue = "month") String period) {
        return expenses.list(Period.resolve(period));
    }

    /** Manual entry from the form: { amount, category, note, merchant, spentAt? } */
    @PostMapping
    public Expense create(@RequestBody Map<String, Object> body) {
        BigDecimal amount = new BigDecimal(String.valueOf(body.get("amount")));
        if (amount.signum() <= 0) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Amount must be above 0");
        String note = body.get("note") == null ? "" : String.valueOf(body.get("note"));
        var cat = parser.byName(body.get("category") == null ? null : String.valueOf(body.get("category")))
            .or(() -> parser.matchCategory(note))
            .orElseGet(parser::others);
        LocalDateTime when = body.get("spentAt") == null || String.valueOf(body.get("spentAt")).isBlank()
            ? LocalDateTime.now() : LocalDateTime.parse(String.valueOf(body.get("spentAt")));
        String merchant = body.get("merchant") == null ? null : String.valueOf(body.get("merchant"));
        return expenses.add(amount, cat, merchant, note, "MANUAL", note, when);
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable Long id) {
        expenses.delete(id);
    }
}
