package com.budgetbee.service;

import org.springframework.stereotype.Service;

/**
 * The AI money coach.
 *
 * 1. Plain code works out the facts (PlanService / PlanCalculator).
 * 2. A local AI model turns the facts into a friendly 2-3 sentence tip.
 * 3. NumberGuard rejects the tip if it contains any number that was not in the facts.
 * 4. If AI is off, down, or fails the guard, a built-in template is used instead.
 */
@Service
public class CoachService {

    public record Advice(String text, String engine) {}

    private static final String SYSTEM = """
        You are a friendly, concise money coach for a user in India.
        Write 2 or 3 short sentences in plain English.
        Use ONLY the numbers listed in FACTS. Never invent, add up or calculate new numbers.
        Do not give investment advice. Use the ₹ symbol for money.
        """;

    private final OllamaClient ollama;

    public CoachService(OllamaClient ollama) {
        this.ollama = ollama;
    }

    public Advice advise(PlanService.PlanView v) {
        String template = template(v);
        String status = v.plan().status();
        if (!ollama.enabled() || status.equals("SETUP") || status.equals("NO_PLAN")) {
            return new Advice(template, "rules");
        }
        String facts = facts(v);
        String ai = ollama.chat(SYSTEM, "FACTS:\n" + facts, false);
        if (ai != null && !ai.isBlank() && NumberGuard.grounded(ai, facts)) {
            return new Advice(ai.trim(), "ollama:" + ollama.model());
        }
        return new Advice(template, "rules");
    }

    String facts(PlanService.PlanView v) {
        PlanCalculator.Plan p = v.plan();
        StringBuilder sb = new StringBuilder();
        sb.append("status=").append(p.status()).append('\n');
        sb.append("dailyAllowance=").append(p.dailyAllowance().toPlainString()).append('\n');
        sb.append("spentToday=").append(p.spentToday().toPlainString()).append('\n');
        sb.append("leftToday=").append(p.leftToday().toPlainString()).append('\n');
        sb.append("daysLeftInMonth=").append(p.daysLeft()).append('\n');
        sb.append("monthSpendPlan=").append(p.pool().toPlainString()).append('\n');
        sb.append("projectedMonthSpendAtCurrentPace=").append(p.projectedSpend().toPlainString()).append('\n');
        sb.append("onTrack=").append(p.onTrack()).append('\n');
        if (v.goalName() != null) {
            sb.append("savingsGoal=").append(v.goalName()).append('\n');
            sb.append("goalSavingPerMonth=").append(v.goalPerMonth().toPlainString()).append('\n');
        }
        if (v.topCategory() != null) {
            sb.append("biggestCategory=").append(v.topCategory()).append('\n');
            sb.append("biggestCategoryPercent=").append(v.topCategoryPercent()).append('\n');
        }
        return sb.toString();
    }

    String template(PlanService.PlanView v) {
        PlanCalculator.Plan p = v.plan();
        String top = v.topCategory() == null ? ""
            : " " + v.topCategory() + " is your biggest category (" + v.topCategoryPercent() + "% of spending).";
        switch (p.status()) {
            case "SETUP":
                return "Tell me your monthly income and I will work out how much you can safely spend each day.";
            case "NO_PLAN":
                return "Your savings goals need more than your income covers this month. "
                    + "Give a goal a later deadline or a smaller target.";
            case "OVER":
                return "You are " + Money.fmt(p.leftToday().negate()) + " over today's safe limit of " + Money.fmt(p.dailyAllowance())
                    + ". Extra spending comes out of your savings"
                    + (v.goalName() != null ? " and delays " + v.goalName() : "")
                    + ", so keep the rest of today to essentials." + top;
            case "CAREFUL":
                return "Only " + Money.fmt(p.leftToday()) + " is left for today (limit " + Money.fmt(p.dailyAllowance())
                    + "). Pause non-essential spending until tomorrow." + top;
            default:
                if (p.onTrack()) {
                    return "You are on track with " + Money.fmt(p.leftToday()) + " left today. At this pace you will finish the month about "
                        + Money.fmt(p.pool().subtract(p.projectedSpend())) + " under your plan." + top;
                }
                return Money.fmt(p.leftToday()) + " is left today, but at this pace you will pass your monthly plan by "
                    + Money.fmt(p.projectedSpend().subtract(p.pool())) + ". A few days under the limit will fix that." + top;
        }
    }
}
