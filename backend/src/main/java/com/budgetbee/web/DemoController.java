package com.budgetbee.web;

import com.budgetbee.service.DemoService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/demo")
public class DemoController {

    private final DemoService demo;

    public DemoController(DemoService demo) {
        this.demo = demo;
    }

    /** Loads sample data into an empty app. */
    @PostMapping("/seed")
    public Map<String, String> seed() {
        return Map.of("message", demo.seed());
    }
}
