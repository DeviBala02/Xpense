package com.budgetbee.web;

import com.budgetbee.model.SavingsGoal;
import com.budgetbee.repo.SavingsGoalRepository;
import com.budgetbee.security.CurrentUser;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/goals")
public class GoalController {

    public record GoalView(Long id, String name, BigDecimal target, BigDecimal saved, int percent,
                           LocalDate deadline, BigDecimal perMonthNeeded) {}

    private final SavingsGoalRepository goals;

    public GoalController(SavingsGoalRepository goals) {
        this.goals = goals;
    }

    @GetMapping
    public List<GoalView> list() {
        return goals.findByUserId(CurrentUser.id()).stream().map(this::view).toList();
    }

    /** { name, targetAmount, deadline? } */
    @PostMapping
    public GoalView create(@RequestBody Map<String, String> body) {
        SavingsGoal g = new SavingsGoal();
        g.userId = CurrentUser.id();
        g.name = body.getOrDefault("name", "").trim();
        if (g.name.isEmpty()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Give the goal a name");
        g.targetAmount = new BigDecimal(body.getOrDefault("targetAmount", "0"));
        if (g.targetAmount.signum() <= 0) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Target must be above 0");
        String d = body.get("deadline");
        g.deadline = (d == null || d.isBlank()) ? null : LocalDate.parse(d);
        return view(goals.save(g));
    }

    @PostMapping("/{id}/deposit")
    public GoalView deposit(@PathVariable Long id, @RequestBody Map<String, String> body) {
        SavingsGoal g = goals.findByIdAndUserId(id, CurrentUser.id()).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Goal not found"));
        BigDecimal amt = new BigDecimal(body.getOrDefault("amount", "0"));
        if (amt.signum() == 0) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Amount cannot be 0");
        g.savedAmount = g.savedAmount.add(amt).max(BigDecimal.ZERO);
        return view(goals.save(g));
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable Long id) {
        goals.findByIdAndUserId(id, CurrentUser.id()).ifPresent(goals::delete);
    }

    private GoalView view(SavingsGoal g) {
        int pct = g.targetAmount.signum() == 0 ? 0
            : g.savedAmount.multiply(BigDecimal.valueOf(100)).divide(g.targetAmount, 0, RoundingMode.DOWN).intValue();
        BigDecimal perMonth = null;
        if (g.deadline != null) {
            BigDecimal left = g.targetAmount.subtract(g.savedAmount).max(BigDecimal.ZERO);
            long months = Math.max(1, ChronoUnit.MONTHS.between(LocalDate.now(), g.deadline));
            perMonth = left.divide(BigDecimal.valueOf(months), 0, RoundingMode.UP);
        }
        return new GoalView(g.id, g.name, g.targetAmount, g.savedAmount, Math.min(pct, 100), g.deadline, perMonth);
    }
}
