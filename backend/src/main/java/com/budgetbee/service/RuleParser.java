package com.budgetbee.service;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Locale;

/** Built-in understanding of chat messages. Used when Ollama is off or unavailable. */
@Component
public class RuleParser {

    /** What the user wants: ADD, QUERY, LIST, DELETE_LAST, SET_BUDGET, ADD_GOAL, ADD_INCOME, QUERY_INCOME, SAFE, WHAT_IF, COACH, HELP or UNKNOWN. */
    public record Intent(String type, BigDecimal amount, String category, String period, String note) {}

    private final ParserService parser;

    public RuleParser(ParserService parser) {
        this.parser = parser;
    }

    public Intent parse(String msg) {
        String m = msg.toLowerCase(Locale.ROOT).trim();
        BigDecimal amount = parser.extractAmount(msg);
        String period = Period.detect(m);
        String cat = parser.matchCategory(m).map(c -> c.name).orElse(null);

        if (m.matches(".*\\b(delete|remove|undo|cancel)\\b.*\\b(last|latest|previous)\\b.*"))
            return new Intent("DELETE_LAST", null, null, null, null);

        if (m.matches(".*\\b(set|create|make|add|keep|fix)\\b.*\\bbudget\\b.*") && amount != null)
            return new Intent("SET_BUDGET", amount, cat, period == null ? "month" : period, msg);

        if (m.matches(".*\\b(save|saving)\\b.*\\bfor\\b.*") && amount != null) {
            int idx = m.lastIndexOf(" for ");
            String name = idx >= 0 ? msg.substring(idx + 5).trim() : "";
            return new Intent("ADD_GOAL", amount, null, null, name.isBlank() ? "My goal" : name);
        }

        if (m.matches(".*\\b(can i|should i|afford|okay to|ok to)\\b.*") && amount != null)
            return new Intent("WHAT_IF", amount, null, null, null);
        if (m.matches(".*(safe to spend|left today|can i spend|allowance|how much can i|how much do i have).*") && amount == null)
            return new Intent("SAFE", null, null, null, null);
        if (m.matches(".*\\b(advice|coach|tips?|how am i doing|suggest|suggestions?)\\b.*"))
            return new Intent("COACH", null, null, null, null);

        boolean incomeWords = m.matches(".*\\b(received|credited|earned|salary|pocket money|got paid|income|stipend|refund)\\b.*");
        if (incomeWords && amount != null) return new Intent("ADD_INCOME", amount, null, period, msg);
        if (incomeWords) return new Intent("QUERY_INCOME", null, null, period, null);

        if (m.matches(".*\\b(show|list|recent|latest|last|see)\\b.*\\b(expenses?|transactions?|payments?)\\b.*"))
            return new Intent("LIST", null, cat, period, null);

        boolean askWords = m.matches(".*(how much|total|what did i spend|what have i spent|summary|report|spent so far|spending|breakdown|where did).*");
        if (askWords && amount == null) return new Intent("QUERY", null, cat, period, null);
        if (m.contains("?") && amount == null) return new Intent("QUERY", null, cat, period, null);

        if (amount != null) return new Intent("ADD", amount, cat, period, msg);

        if (m.matches(".*\\b(help|hi|hello|hey)\\b.*")) return new Intent("HELP", null, null, null, null);
        return new Intent("UNKNOWN", null, null, null, null);
    }
}
