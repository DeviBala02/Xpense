package com.budgetbee.service;

import com.budgetbee.model.Income;
import com.budgetbee.model.IncomeCategory;
import com.budgetbee.model.SenderRule;
import com.budgetbee.repo.IncomeCategoryRepository;
import com.budgetbee.repo.IncomeRepository;
import com.budgetbee.repo.SenderRuleRepository;
import com.budgetbee.security.CurrentUser;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

/** Money received by the logged-in user: saving credits, choosing a category, remembering senders. */
@Service
public class IncomeService {

    private final IncomeRepository incomes;
    private final IncomeCategoryRepository categories;
    private final SenderRuleRepository rules;

    public IncomeService(IncomeRepository incomes, IncomeCategoryRepository categories, SenderRuleRepository rules) {
        this.incomes = incomes;
        this.categories = categories;
        this.rules = rules;
    }

    /** Saves a credit. With no category it stays "pending" unless the sender was remembered before. */
    public Income add(BigDecimal amount, String sender, IncomeCategory category, String note,
                      String source, String rawText, LocalDateTime when) {
        Long uid = CurrentUser.id();
        Income i = new Income();
        i.userId = uid;
        i.amount = amount;
        i.sender = (sender == null || sender.isBlank()) ? null : sender.trim();
        if (category == null && i.sender != null) {
            category = rules.findByUserIdAndSenderIgnoreCase(uid, i.sender).map(r -> r.category).orElse(null);
        }
        i.category = category;
        i.note = note == null ? null : (note.length() > 250 ? note.substring(0, 250) : note);
        i.source = source;
        i.rawText = rawText == null ? null : (rawText.length() > 990 ? rawText.substring(0, 990) : rawText);
        i.receivedAt = when == null ? LocalDateTime.now() : when;
        return incomes.save(i);
    }

    /** User picks a category. With remember=true, later credits from the same sender follow automatically. */
    @Transactional
    public Income categorize(Long id, String categoryName, boolean remember) {
        Long uid = CurrentUser.id();
        Income i = incomes.findByIdAndUserId(id, uid)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Credit not found"));
        IncomeCategory c = byName(categoryName)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Choose a category"));
        i.category = c;
        incomes.save(i);
        if (remember && i.sender != null) {
            SenderRule rule = rules.findByUserIdAndSenderIgnoreCase(uid, i.sender).orElseGet(SenderRule::new);
            rule.userId = uid;
            rule.sender = i.sender.toLowerCase(Locale.ROOT);
            rule.category = c;
            rules.save(rule);
            for (Income other : incomes.findByUserIdAndSenderIgnoreCaseAndCategoryIsNull(uid, i.sender)) {
                other.category = c;
                incomes.save(other);
            }
        }
        return i;
    }

    public Optional<IncomeCategory> byName(String name) {
        if (name == null || name.isBlank()) return Optional.empty();
        return categories.findByUserIdAndNameIgnoreCase(CurrentUser.id(), name.trim());
    }

    /** Finds a category whose name appears in the text, e.g. "received 5000 salary". */
    public Optional<IncomeCategory> matchCategory(String text) {
        if (text == null) return Optional.empty();
        String t = text.toLowerCase(Locale.ROOT);
        for (IncomeCategory c : categories.findByUserId(CurrentUser.id())) {
            if (c.name.equalsIgnoreCase("Other")) continue;
            String n = c.name.toLowerCase(Locale.ROOT);
            if (Pattern.compile("(?<![a-z0-9])" + Pattern.quote(n) + "(?![a-z0-9])").matcher(t).find()) {
                return Optional.of(c);
            }
        }
        return Optional.empty();
    }

    public List<Income> list(Period.Range r) {
        return incomes.findByUserIdAndReceivedAtGreaterThanEqualAndReceivedAtLessThanOrderByReceivedAtDesc(CurrentUser.id(), r.from(), r.to());
    }

    public List<Income> pending() {
        return incomes.findByUserIdAndCategoryIsNullOrderByReceivedAtDesc(CurrentUser.id());
    }

    public long pendingCount() {
        return incomes.countByUserIdAndCategoryIsNull(CurrentUser.id());
    }

    public BigDecimal total(Period.Range r) {
        return incomes.sumBetween(CurrentUser.id(), r.from(), r.to());
    }

    public List<Object[]> byCategory(Period.Range r) {
        return incomes.totalsByCategory(CurrentUser.id(), r.from(), r.to());
    }

    /** Deletes only if the credit belongs to the logged-in user. */
    public void delete(Long id) {
        incomes.findByIdAndUserId(id, CurrentUser.id()).ifPresent(incomes::delete);
    }

    public boolean categoryInUse(Long categoryId) {
        return incomes.existsByUserIdAndCategoryId(CurrentUser.id(), categoryId);
    }
}
