package com.budgetbee.service;

import java.math.BigDecimal;
import java.math.RoundingMode;

public final class Money {
    private Money() {}

    /** 12500 -> "₹12,500" ; 49.5 -> "₹49.50" ; 1234567 -> "₹12,34,567" (Indian digit grouping). */
    public static String fmt(BigDecimal v) {
        if (v == null) v = BigDecimal.ZERO;
        v = v.setScale(2, RoundingMode.HALF_UP);
        boolean neg = v.signum() < 0;
        String[] parts = v.abs().toPlainString().split("\\.");
        String whole = parts[0];
        if (whole.length() > 3) {
            String last3 = whole.substring(whole.length() - 3);
            String rest = whole.substring(0, whole.length() - 3).replaceAll("(\\d)(?=(\\d{2})+$)", "$1,");
            whole = rest + "," + last3;
        }
        String frac = parts[1].equals("00") ? "" : "." + parts[1];
        return (neg ? "-" : "") + "₹" + whole + frac;
    }
}
