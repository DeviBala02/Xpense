package com.budgetbee.service;

import com.budgetbee.model.Category;
import com.budgetbee.model.Expense;
import com.budgetbee.repo.ExpenseRepository;
import com.budgetbee.security.CurrentUser;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/** Expenses of the logged-in user. */
@Service
public class ExpenseService {

    private final ExpenseRepository expenses;

    public ExpenseService(ExpenseRepository expenses) {
        this.expenses = expenses;
    }

    public Expense add(BigDecimal amount, Category category, String merchant, String note,
                       String source, String rawText, LocalDateTime when) {
        Expense e = new Expense();
        e.userId = CurrentUser.id();
        e.amount = amount;
        e.category = category;
        e.merchant = merchant;
        e.note = note == null ? null : (note.length() > 250 ? note.substring(0, 250) : note);
        e.source = source;
        e.rawText = rawText == null ? null : (rawText.length() > 990 ? rawText.substring(0, 990) : rawText);
        e.spentAt = when == null ? LocalDateTime.now() : when;
        return expenses.save(e);
    }

    public List<Expense> list(Period.Range r) {
        return expenses.findByUserIdAndSpentAtGreaterThanEqualAndSpentAtLessThanOrderBySpentAtDesc(CurrentUser.id(), r.from(), r.to());
    }

    public List<Expense> recent() {
        return expenses.findTop10ByUserIdOrderBySpentAtDesc(CurrentUser.id());
    }

    public BigDecimal total(Period.Range r) {
        return expenses.sumBetween(CurrentUser.id(), r.from(), r.to());
    }

    public BigDecimal totalFor(Category c, Period.Range r) {
        return expenses.sumByCategoryBetween(CurrentUser.id(), c, r.from(), r.to());
    }

    public List<Object[]> byCategory(Period.Range r) {
        return expenses.totalsByCategory(CurrentUser.id(), r.from(), r.to());
    }

    public Expense deleteLast() {
        Expense last = expenses.findFirstByUserIdOrderByIdDesc(CurrentUser.id());
        if (last != null) expenses.delete(last);
        return last;
    }

    /** Deletes only if the expense belongs to the logged-in user. */
    public void delete(Long id) {
        expenses.findByIdAndUserId(id, CurrentUser.id()).ifPresent(expenses::delete);
    }
}
