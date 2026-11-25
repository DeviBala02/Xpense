package com.budgetbee.service;

import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Safety check for AI text. The model may only repeat numbers that we gave it.
 * If it writes any other number, the text is rejected and the plain template is used instead.
 */
public final class NumberGuard {

    private static final Pattern NUM = Pattern.compile("\\d+(?:\\.\\d+)?");

    private NumberGuard() {}

    public static Set<String> numbersIn(String text) {
        Set<String> out = new HashSet<>();
        if (text == null) return out;
        Matcher m = NUM.matcher(text.replace(",", ""));
        while (m.find()) out.add(m.group());
        return out;
    }

    /** True when every number in the AI text also appears in the facts. */
    public static boolean grounded(String aiText, String facts) {
        return numbersIn(facts).containsAll(numbersIn(aiText));
    }
}
