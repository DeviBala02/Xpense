package com.budgetbee.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NumberGuardTest {

    private static final String FACTS = "leftToday=612 dailyAllowance=797 daysLeft=13 projected=24700 delayDays=2.3";

    @Test
    void acceptsTextThatOnlyUsesGivenNumbers() {
        assertTrue(NumberGuard.grounded("You have ₹612 left today out of ₹797. 13 days to go.", FACTS));
    }

    @Test
    void ignoresThousandsSeparators() {
        assertTrue(NumberGuard.grounded("At this pace you will spend about ₹24,700.", FACTS));
    }

    @Test
    void rejectsAnInventedNumber() {
        assertFalse(NumberGuard.grounded("You have ₹650 left today.", FACTS));
    }

    @Test
    void rejectsADecimalThatWasNotGiven() {
        assertFalse(NumberGuard.grounded("This delays your goal by 3.1 days.", FACTS));
    }

    @Test
    void textWithNoNumbersIsFine() {
        assertTrue(NumberGuard.grounded("You are doing well, keep going.", FACTS));
    }
}
