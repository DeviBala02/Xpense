package com.budgetbee.security;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LoginThrottleTest {

    private static final long TEN_MIN = 10 * 60 * 1000L;

    @Test
    void locksAfterTooManyWrongPasswords() {
        LoginThrottle t = new LoginThrottle(5, TEN_MIN);
        for (int i = 0; i < 4; i++) t.fail("a@x.com", 1000);
        assertFalse(t.isLocked("a@x.com", 1000));
        t.fail("a@x.com", 1000);
        assertTrue(t.isLocked("a@x.com", 1000));
    }

    @Test
    void staysLockedUntilTheTimeEnds() {
        LoginThrottle t = new LoginThrottle(3, TEN_MIN);
        for (int i = 0; i < 3; i++) t.fail("a@x.com", 0);
        assertTrue(t.isLocked("a@x.com", TEN_MIN - 1));
        assertFalse(t.isLocked("a@x.com", TEN_MIN));
    }

    @Test
    void reportsRoundedUpSecondsLeft() {
        LoginThrottle t = new LoginThrottle(1, 90_000L);
        t.fail("a@x.com", 0);
        assertEquals(90L, t.secondsLeft("a@x.com", 0));
        assertEquals(31L, t.secondsLeft("a@x.com", 59_001));
        assertEquals(0L, t.secondsLeft("a@x.com", 90_000));
    }

    @Test
    void countsStartAgainAfterALockEnds() {
        LoginThrottle t = new LoginThrottle(2, 1000);
        t.fail("a@x.com", 0);
        t.fail("a@x.com", 0);
        assertTrue(t.isLocked("a@x.com", 500));
        t.fail("a@x.com", 5000);                       // lock ended: this is failure number 1 again
        assertFalse(t.isLocked("a@x.com", 5000));
    }

    @Test
    void aSuccessfulLoginClearsTheCount() {
        LoginThrottle t = new LoginThrottle(3, TEN_MIN);
        t.fail("a@x.com", 0);
        t.fail("a@x.com", 0);
        t.success("a@x.com");
        t.fail("a@x.com", 0);
        assertFalse(t.isLocked("a@x.com", 0));
    }

    @Test
    void differentEmailsAreIndependent() {
        LoginThrottle t = new LoginThrottle(1, TEN_MIN);
        t.fail("a@x.com", 0);
        assertTrue(t.isLocked("a@x.com", 0));
        assertFalse(t.isLocked("b@x.com", 0));
    }
}
