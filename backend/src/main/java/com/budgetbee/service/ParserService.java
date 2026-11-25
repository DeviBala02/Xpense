package com.budgetbee.service;

import com.budgetbee.model.Category;
import com.budgetbee.repo.CategoryRepository;
import com.budgetbee.security.CurrentUser;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads a payment text (GPay notification, bank SMS or something you typed)
 * and works out the amount, the merchant and the category from your keywords.
 */
@Service
public class ParserService {

    private static final Pattern RUPEE_AMOUNT =
        Pattern.compile("(?i)(?:₹|rs\\.?|inr)\\s*([0-9][0-9,]*(?:\\.[0-9]{1,2})?)");
    private static final Pattern ANY_NUMBER =
        Pattern.compile("(?<![A-Za-z0-9/])([0-9][0-9,]*(?:\\.[0-9]{1,2})?)(?![A-Za-z0-9/])");
    private static final Pattern MERCHANT =
        Pattern.compile("(?i)\\bto\\s+([A-Za-z0-9 &.'-]{2,40}?)(?=\\s+(?:on|via|using|ref|upi|from|for)\\b|[.,]|$)");

    private static final Pattern SENDER_UPI_REF =
        Pattern.compile("(?i)UPI/(?:(?:CR|DR)/)?\\d{6,}/([A-Za-z][A-Za-z .]{1,40}?)(?=/|\\s{2,}|$|[^A-Za-z .])");
    private static final Pattern SENDER_FROM =
        Pattern.compile("(?i)\\bfrom\\s+([A-Za-z][A-Za-z0-9 &.'@-]{1,40}?)(?=\\s+(?:on|via|using|ref|upi|to|for|a/c|ac)\\b|[.,;(]|$)");
    private static final Pattern SENDER_BY =
        Pattern.compile("(?i)\\bby\\s+(?:vpa\\s+)?([A-Za-z][A-Za-z0-9 &.'@-]{1,40}?)(?=\\s+(?:on|via|using|ref|upi|to|for|a/c|ac)\\b|[.,;(]|$)");

    private final CategoryRepository categories;

    public ParserService(CategoryRepository categories) {
        this.categories = categories;
    }

    public record Parsed(BigDecimal amount, Category category, String merchant, boolean incoming) {}

    public Parsed parse(String text) {
        BigDecimal amount = extractAmount(text);
        Category cat = matchCategory(text).orElseGet(this::others);
        return new Parsed(amount, cat, extractMerchant(text), isIncoming(text));
    }

    public BigDecimal extractAmount(String text) {
        if (text == null) return null;
        Matcher m = RUPEE_AMOUNT.matcher(text);
        if (m.find()) return toAmount(m.group(1));
        m = ANY_NUMBER.matcher(text);
        if (m.find()) return toAmount(m.group(1));
        return null;
    }

    private BigDecimal toAmount(String raw) {
        try {
            BigDecimal v = new BigDecimal(raw.replace(",", ""));
            return v.signum() > 0 ? v : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public String extractMerchant(String text) {
        if (text == null) return null;
        Matcher m = MERCHANT.matcher(text);
        return m.find() ? m.group(1).trim() : null;
    }

    /** Who sent the money, read from alerts like "credited ... from Karthik" or "UPI/CR/1234/KARTHIK S". */
    public String extractSender(String text) {
        if (text == null) return null;
        for (Pattern pat : new Pattern[]{SENDER_UPI_REF, SENDER_FROM, SENDER_BY}) {
            Matcher m = pat.matcher(text);
            if (m.find()) {
                String name = m.group(1).trim().replaceAll("\\s+", " ");
                if (name.length() >= 2 && !name.equalsIgnoreCase("upi")) return name;
            }
        }
        return null;
    }

    /** Money coming in (refund, credit) should not be counted as an expense. */
    public boolean isIncoming(String text) {
        String t = text.toLowerCase();
        boolean in = t.contains("credited") || t.matches("(?s).*\\breceived\\b.*") || t.contains("refund");
        boolean out = t.contains("debited") || t.contains("paid") || t.contains("sent") || t.contains("spent");
        return in && !out;
    }

    /** Picks the category whose keywords (or name) appear most often in the text. */
    public Optional<Category> matchCategory(String text) {
        return pickCategory(categories.findByUserId(CurrentUser.id()), text);
    }

    /** The matching itself, without any database, so it can be tested on its own. */
    public static Optional<Category> pickCategory(List<Category> all, String text) {
        if (text == null || text.isBlank()) return Optional.empty();
        String t = text.toLowerCase();
        Category best = null;
        int bestScore = 0;
        for (Category c : all) {
            int score = 0;
            if (hit(t, c.name)) score++;
            if (c.keywords != null) {
                for (String k : c.keywords.split(",")) {
                    if (hit(t, k)) score++;
                }
            }
            if (score > bestScore) {
                best = c;
                bestScore = score;
            }
        }
        return Optional.ofNullable(best);
    }

    private static boolean hit(String text, String keyword) {
        if (keyword == null) return false;
        String k = keyword.trim().toLowerCase();
        if (k.isEmpty()) return false;
        return Pattern.compile("(?<![a-z0-9])" + Pattern.quote(k) + "s?(?![a-z0-9])").matcher(text).find();
    }

    public Category others() {
        Long uid = CurrentUser.id();
        return categories.findByUserIdAndNameIgnoreCase(uid, "Others").orElseGet(() -> {
            Category c = new Category("Others", "📦", "");
            c.userId = uid;
            return categories.save(c);
        });
    }

    public Optional<Category> byName(String name) {
        if (name == null || name.isBlank()) return Optional.empty();
        return categories.findByUserIdAndNameIgnoreCase(CurrentUser.id(), name.trim());
    }
}
