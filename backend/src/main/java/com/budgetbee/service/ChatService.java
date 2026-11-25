package com.budgetbee.service;

import com.budgetbee.model.Budget;
import com.budgetbee.model.Category;
import com.budgetbee.model.Expense;
import com.budgetbee.model.Income;
import com.budgetbee.model.SavingsGoal;
import com.budgetbee.repo.CategoryRepository;
import com.budgetbee.repo.SavingsGoalRepository;
import com.budgetbee.security.CurrentUser;
import com.budgetbee.service.RuleParser.Intent;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

/**
 * The AI bot. It turns a chat message into an "intent" (add, ask, list, budget, goal ...)
 * and then runs that intent against the database.
 *
 * - With budgetbee.ai.enabled=true a local Ollama model understands free-form sentences.
 * - Without it (or if Ollama is down) a built-in rule parser handles common phrases.
 * The numbers always come from the database, never from the model.
 */
@Service
public class ChatService {

    public record Reply(String reply, boolean changed, String engine) {}

    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH);

    private final ParserService parser;
    private final RuleParser rules;
    private final ExpenseService expenses;
    private final IncomeService incomeSvc;
    private final BudgetService budgets;
    private final CategoryRepository categories;
    private final SavingsGoalRepository goals;
    private final ObjectMapper mapper;
    private final OllamaClient ollama;
    private final PlanService plan;
    private final CoachService coach;

    public ChatService(ParserService parser, RuleParser rules, ExpenseService expenses, IncomeService incomeSvc, BudgetService budgets,
                       CategoryRepository categories, SavingsGoalRepository goals, ObjectMapper mapper,
                       OllamaClient ollama, PlanService plan, CoachService coach) {
        this.parser = parser;
        this.rules = rules;
        this.expenses = expenses;
        this.incomeSvc = incomeSvc;
        this.budgets = budgets;
        this.categories = categories;
        this.goals = goals;
        this.mapper = mapper;
        this.ollama = ollama;
        this.plan = plan;
        this.coach = coach;
    }

    public Reply chat(String message) {
        if (message == null || message.isBlank()) {
            return new Reply("Type something like “spent 50 on snacks” or “how much did I spend this week?”", false, "rules");
        }
        Intent intent = null;
        String engine = "rules";
        if (ollama.enabled()) {
            intent = viaLlm(message);
            if (intent != null) engine = "ollama:" + ollama.model();
        }
        if (intent == null) intent = rules.parse(message);
        return execute(intent, message, engine);
    }

    // ---------------------------------------------------------------- understanding

    private Intent viaLlm(String msg) {
        try {
            String names = categories.findByUserId(CurrentUser.id()).stream().map(c -> c.name).collect(Collectors.joining(", "));
            String system = """
                You convert a message from an expense-tracker user into JSON. Reply with JSON only.
                Today is %s. Categories: %s.
                Schema: {"intent":"ADD|QUERY|LIST|DELETE_LAST|SET_BUDGET|ADD_GOAL|ADD_INCOME|QUERY_INCOME|SAFE|WHAT_IF|COACH|HELP|UNKNOWN",
                "amount":number or null,"category":one of the categories or null,
                "period":"today|yesterday|week|last_week|month|last_month|year|all" or null,
                "note":short text or null}
                ADD = the user spent money. QUERY = asks how much was spent. LIST = wants to see transactions.
                ADD_INCOME = the user received money. QUERY_INCOME = asks how much money came in. SAFE = asks how much they can spend today. WHAT_IF = asks if they can afford an amount (amount = that amount). COACH = asks for advice. SET_BUDGET = wants a spending limit. ADD_GOAL = wants to save toward something (note = goal name).
                """.formatted(LocalDate.now(), names);
            String raw = ollama.chat(system, msg, true);
            if (raw == null) return null;
            JsonNode content = mapper.readTree(raw);
            String type = content.path("intent").asText("UNKNOWN").toUpperCase(Locale.ROOT);
            if (type.equals("UNKNOWN")) return null;
            BigDecimal amount = content.path("amount").isNumber() ? content.path("amount").decimalValue() : null;
            String category = content.path("category").isTextual() ? content.path("category").asText() : null;
            String period = content.path("period").isTextual() ? content.path("period").asText() : null;
            String note = content.path("note").isTextual() ? content.path("note").asText() : msg;
            if (type.equals("ADD") && amount == null) amount = parser.extractAmount(msg);
            return new Intent(type, amount, category, period, note);
        } catch (Exception e) {
            return null; // Ollama not running or bad JSON -> fall back to rules
        }
    }

    // ---------------------------------------------------------------- doing

    private Reply execute(Intent in, String original, String engine) {
        switch (in.type()) {
            case "ADD": return add(in, original, engine);
            case "QUERY": return query(in, engine);
            case "LIST": return list(in, engine);
            case "DELETE_LAST": return deleteLast(engine);
            case "SET_BUDGET": return setBudget(in, engine);
            case "ADD_GOAL": return addGoal(in, engine);
            case "ADD_INCOME": return addIncome(in, original, engine);
            case "QUERY_INCOME": return queryIncome(in, engine);
            case "SAFE": return safe(engine);
            case "WHAT_IF": return whatIf(in, engine);
            case "COACH": return coachReply(engine);
            case "HELP": return new Reply(help(), false, engine);
            default:
                return new Reply("I didn't catch that. " + help(), false, engine);
        }
    }

    private Reply add(Intent in, String original, String engine) {
        if (in.amount() == null) return new Reply("How much was it? Try “spent 50 on snacks”.", false, engine);
        Category cat = parser.byName(in.category())
            .or(() -> parser.matchCategory(original))
            .orElseGet(parser::others);
        LocalDateTime when = LocalDateTime.now();
        if ("yesterday".equals(in.period())) when = LocalDate.now().minusDays(1).atTime(12, 0);
        Expense e = expenses.add(in.amount(), cat, parser.extractMerchant(original), original, "CHAT", original, when);
        StringBuilder sb = new StringBuilder("Added " + Money.fmt(e.amount) + " under " + cat.icon + " " + cat.name
            + (when.toLocalDate().isEqual(LocalDate.now()) ? "." : " for yesterday."));
        for (String a : budgets.alertsFor(cat)) sb.append("\n").append(a);
        PlanCalculator.Plan p = plan.today().plan();
        if (!p.status().equals("SETUP") && !p.status().equals("NO_PLAN")) {
            sb.append(p.leftToday().signum() >= 0
                ? "\nSafe to spend left today: " + Money.fmt(p.leftToday()) + "."
                : "\nYou are " + Money.fmt(p.leftToday().negate()) + " over today's safe limit.");
        }
        return new Reply(sb.toString(), true, engine);
    }

    private Reply query(Intent in, String engine) {
        Period.Range r = Period.resolve(in.period() == null ? "month" : in.period());
        Category cat = parser.byName(in.category()).orElse(null);
        if (cat != null) {
            BigDecimal total = expenses.totalFor(cat, r);
            return new Reply("You spent " + Money.fmt(total) + " on " + cat.icon + " " + cat.name + " " + r.label() + ".", false, engine);
        }
        BigDecimal total = expenses.total(r);
        List<Object[]> rows = expenses.byCategory(r);
        if (rows.isEmpty()) return new Reply("No expenses recorded " + r.label() + " yet.", false, engine);
        String top = rows.stream().limit(3)
            .map(x -> x[0] + " " + Money.fmt((BigDecimal) x[1]))
            .collect(Collectors.joining(", "));
        return new Reply("Total spent " + r.label() + ": " + Money.fmt(total) + ". Top categories: " + top + ".", false, engine);
    }

    private Reply list(Intent in, String engine) {
        List<Expense> rows = in.period() == null ? expenses.recent() : expenses.list(Period.resolve(in.period()));
        if (in.category() != null) {
            rows = rows.stream().filter(e -> e.category.name.equalsIgnoreCase(in.category())).toList();
        }
        if (rows.isEmpty()) return new Reply("Nothing to show yet.", false, engine);
        String lines = rows.stream().limit(10)
            .map(e -> "• " + e.spentAt.format(DAY) + "  " + Money.fmt(e.amount) + "  " + e.category.icon + " " + e.category.name
                + (e.merchant != null ? "  (" + e.merchant + ")" : ""))
            .collect(Collectors.joining("\n"));
        return new Reply(lines, false, engine);
    }

    private Reply deleteLast(String engine) {
        Expense e = expenses.deleteLast();
        if (e == null) return new Reply("There is no expense to delete.", false, engine);
        return new Reply("Deleted the last expense: " + Money.fmt(e.amount) + " under " + e.category.name + ".", true, engine);
    }

    private Reply setBudget(Intent in, String engine) {
        if (in.amount() == null) return new Reply("What should the limit be? Try “set food budget 5000 this month”.", false, engine);
        Period.Range r = Period.resolve(in.period() == null ? "month" : in.period());
        Category cat = parser.byName(in.category()).orElse(null);
        Budget b = budgets.create(null, cat, in.amount(), r.startDate(), r.endDate());
        return new Reply("Budget set: " + b.name + " " + Money.fmt(b.amount) + " for " + r.label()
            + " (" + b.startDate.format(DAY) + " – " + b.endDate.format(DAY) + "). I'll warn you at 80%.", true, engine);
    }

    private Reply addGoal(Intent in, String engine) {
        SavingsGoal g = new SavingsGoal();
        g.userId = CurrentUser.id();
        g.name = in.note() == null || in.note().isBlank() ? "My goal" : in.note();
        g.targetAmount = in.amount();
        goals.save(g);
        return new Reply("Savings goal created: " + g.name + " – target " + Money.fmt(g.targetAmount)
            + ". Add money to it from the Goals tab.", true, engine);
    }

    private Reply addIncome(Intent in, String original, String engine) {
        if (in.amount() == null) return new Reply("How much did you receive? Try “received 5000 salary”.", false, engine);
        var cat = incomeSvc.matchCategory(original).orElse(null);
        Income i = incomeSvc.add(in.amount(), parser.extractSender(original), cat, original, "CHAT", original, LocalDateTime.now());
        String msg = "Recorded " + Money.fmt(i.amount) + " received" + (i.sender != null ? " from " + i.sender : "")
            + (i.category != null ? " under " + i.category.icon + " " + i.category.name + "." : ". Pick its category in the Money in tab.");
        return new Reply(msg, true, engine);
    }

    private Reply queryIncome(Intent in, String engine) {
        Period.Range r = Period.resolve(in.period() == null ? "month" : in.period());
        BigDecimal total = incomeSvc.total(r);
        List<Object[]> rows = incomeSvc.byCategory(r);
        if (rows.isEmpty()) return new Reply("No money received " + r.label() + " yet.", false, engine);
        String parts = rows.stream().limit(4)
            .map(x -> (x[0] == null ? "Needs a category" : x[0]) + " " + Money.fmt((BigDecimal) x[1]))
            .collect(Collectors.joining(", "));
        long pending = incomeSvc.pendingCount();
        return new Reply("Money in " + r.label() + ": " + Money.fmt(total) + " (" + parts + ")."
            + (pending > 0 ? "\n" + pending + " credit(s) still need a category in the Money in tab." : ""), false, engine);
    }

    private Reply safe(String engine) {
        PlanService.PlanView v = plan.today();
        PlanCalculator.Plan p = v.plan();
        if (p.status().equals("SETUP")) {
            return new Reply("Tell me your monthly income first (Budgets tab, or load the sample data) and I will work out your safe daily amount.", false, engine);
        }
        if (p.status().equals("NO_PLAN")) {
            return new Reply(coach.advise(v).text(), false, engine);
        }
        String text = p.leftToday().signum() >= 0
            ? "You can safely spend " + Money.fmt(p.leftToday()) + " more today (daily limit " + Money.fmt(p.dailyAllowance())
                + ", " + p.daysLeft() + " days left this month)."
            : "You are " + Money.fmt(p.leftToday().negate()) + " over today's safe limit of " + Money.fmt(p.dailyAllowance()) + ".";
        return new Reply(text, false, engine);
    }

    private Reply whatIf(Intent in, String engine) {
        if (in.amount() == null) return new Reply("How much is it? Try “can I afford 500?”", false, engine);
        PlanService.PlanView v = plan.today();
        String status = v.plan().status();
        if (status.equals("SETUP") || status.equals("NO_PLAN")) return safe(engine);
        PlanCalculator.WhatIf w = PlanCalculator.whatIf(v.plan(), in.amount());
        if (w.affordable()) {
            return new Reply("Yes. After " + Money.fmt(w.amount()) + " you would still have " + Money.fmt(w.leftTodayAfter()) + " left today.", false, engine);
        }
        String text = "That is " + Money.fmt(w.overBy()) + " beyond today's safe limit, so it would come out of your savings"
            + (w.delayDays() != null && v.goalName() != null ? " and delay " + v.goalName() + " by about " + w.delayDays().toPlainString() + " days" : "")
            + ". Your daily allowance for the rest of the month would go from " + Money.fmt(v.plan().dailyAllowance())
            + " to " + Money.fmt(w.newDailyAllowance()) + ".";
        return new Reply(text, false, engine);
    }

    private Reply coachReply(String engine) {
        CoachService.Advice a = coach.advise(plan.today());
        return new Reply(a.text(), false, a.engine().equals("rules") ? engine : a.engine());
    }

    private String help() {
        return "I can: add expenses (“spent 50 on snacks”), answer questions (“how much did I spend on food this week?”), "
            + "list payments (“show last expenses”), set budgets (“set food budget 5000 this month”), "
            + "check your safe daily amount (“how much can I spend today?”, “can I afford 500?”), get advice (“coach me”), "
            + "create goals (“save 40000 for laptop”), record money received (“received 5000 salary”) and undo (“delete last expense”).";
    }
}
