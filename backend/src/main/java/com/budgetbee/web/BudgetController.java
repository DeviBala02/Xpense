package com.budgetbee.web;

import com.budgetbee.model.Category;
import com.budgetbee.service.BudgetService;
import com.budgetbee.service.ParserService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/budgets")
public class BudgetController {

    private final BudgetService budgets;
    private final ParserService parser;

    public BudgetController(BudgetService budgets, ParserService parser) {
        this.budgets = budgets;
        this.parser = parser;
    }

    @GetMapping
    public List<BudgetService.BudgetStatus> list() {
        return budgets.statuses();
    }

    /** { name?, category? (blank = overall), amount, startDate, endDate } */
    @PostMapping
    public BudgetService.BudgetStatus create(@RequestBody Map<String, String> body) {
        BigDecimal amount = new BigDecimal(body.getOrDefault("amount", "0"));
        if (amount.signum() <= 0) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Amount must be above 0");
        LocalDate start = LocalDate.parse(body.get("startDate"));
        LocalDate end = LocalDate.parse(body.get("endDate"));
        if (end.isBefore(start)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "End date must be after the start date");
        String catName = body.get("category");
        Category cat = null;
        if (catName != null && !catName.isBlank()) {
            cat = parser.byName(catName).orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown category"));
        }
        var b = budgets.create(body.get("name"), cat, amount, start, end);
        return budgets.status(b);
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable Long id) {
        budgets.delete(id);
    }
}
