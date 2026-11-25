package com.budgetbee.web;

import com.budgetbee.model.Settings;
import com.budgetbee.service.CoachService;
import com.budgetbee.service.PlanService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.Map;

/** Safe to spend today, "can I afford it?", the AI coach, and the expected-income setting. */
@RestController
@RequestMapping("/api")
public class PlanController {

    private final PlanService plan;
    private final CoachService coach;

    public PlanController(PlanService plan, CoachService coach) {
        this.plan = plan;
        this.coach = coach;
    }

    @GetMapping("/plan/today")
    public PlanService.PlanView today() {
        return plan.today();
    }

    @GetMapping("/plan/what-if")
    public PlanService.WhatIfView whatIf(@RequestParam BigDecimal amount) {
        if (amount.signum() <= 0) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Amount must be above 0");
        return plan.whatIf(amount);
    }

    @GetMapping("/plan/coach")
    public CoachService.Advice coach() {
        return coach.advise(plan.today());
    }

    @GetMapping("/settings")
    public Settings settings() {
        return plan.settings();
    }

    /** { expectedIncome } */
    @PutMapping("/settings")
    public Settings update(@RequestBody Map<String, String> body) {
        BigDecimal v = new BigDecimal(body.getOrDefault("expectedIncome", "0"));
        return plan.setExpectedIncome(v);
    }
}
