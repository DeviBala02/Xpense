package com.budgetbee.service;

import com.budgetbee.model.AuthToken;
import com.budgetbee.model.User;
import com.budgetbee.repo.*;
import com.budgetbee.security.LoginThrottle;
import com.budgetbee.security.TokenUtil;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.Locale;
import java.util.regex.Pattern;

/** Creating accounts, logging in and out, and deleting an account with all its data. */
@Service
public class AuthService {

    public record UserView(Long id, String name, String email) {}
    public record Session(String token, UserView user) {}

    private static final Pattern EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");

    private final UserRepository users;
    private final AuthTokenRepository tokens;
    private final PasswordEncoder encoder;
    private final DefaultsService defaults;
    private final ExpenseRepository expenses;
    private final IncomeRepository incomes;
    private final BudgetRepository budgets;
    private final SavingsGoalRepository goals;
    private final SettingsRepository settings;
    private final SenderRuleRepository senderRules;
    private final CategoryRepository categories;
    private final IncomeCategoryRepository incomeCategories;
    private final long tokenDays;

    /** 5 wrong passwords lock that email for 10 minutes. */
    private final LoginThrottle throttle = new LoginThrottle(5, 10 * 60 * 1000L);

    public AuthService(UserRepository users, AuthTokenRepository tokens, PasswordEncoder encoder, DefaultsService defaults,
                       ExpenseRepository expenses, IncomeRepository incomes, BudgetRepository budgets,
                       SavingsGoalRepository goals, SettingsRepository settings, SenderRuleRepository senderRules,
                       CategoryRepository categories, IncomeCategoryRepository incomeCategories,
                       @Value("${budgetbee.token-days:30}") long tokenDays) {
        this.users = users;
        this.tokens = tokens;
        this.encoder = encoder;
        this.defaults = defaults;
        this.expenses = expenses;
        this.incomes = incomes;
        this.budgets = budgets;
        this.goals = goals;
        this.settings = settings;
        this.senderRules = senderRules;
        this.categories = categories;
        this.incomeCategories = incomeCategories;
        this.tokenDays = tokenDays;
    }

    @Transactional
    public Session register(String name, String email, String password) {
        String cleanName = name == null ? "" : name.trim();
        String cleanEmail = normalize(email);
        if (cleanName.isEmpty() || cleanName.length() > 60) throw bad("Please enter your name (up to 60 letters).");
        if (!EMAIL.matcher(cleanEmail).matches()) throw bad("Please enter a valid email address.");
        if (password == null || password.length() < 8) throw bad("Your password needs at least 8 characters.");
        if (password.length() > 100) throw bad("That password is too long.");
        if (users.existsByEmail(cleanEmail)) throw new ResponseStatusException(HttpStatus.CONFLICT, "An account with this email already exists. Try logging in.");

        User u = new User();
        u.name = cleanName;
        u.email = cleanEmail;
        u.passwordHash = encoder.encode(password);
        u.createdAt = LocalDateTime.now();
        u = users.save(u);
        defaults.seed(u.id);
        return new Session(issueToken(u.id), view(u));
    }

    @Transactional
    public Session login(String email, String password) {
        String key = normalize(email);
        long now = System.currentTimeMillis();
        if (throttle.isLocked(key, now)) {
            long minutes = Math.max(1, throttle.secondsLeft(key, now) / 60);
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Too many wrong attempts. Try again in about " + minutes + " minute(s).");
        }
        User u = users.findByEmail(key).orElse(null);
        if (u == null || password == null || !encoder.matches(password, u.passwordHash)) {
            throttle.fail(key, now);
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Wrong email or password.");
        }
        throttle.success(key);
        return new Session(issueToken(u.id), view(u));
    }

    public UserView me(Long userId) {
        User u = users.findById(userId).orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Please log in"));
        return view(u);
    }

    /** Ends this device's login straight away. */
    @Transactional
    public void logout(String rawToken) {
        if (rawToken != null && !rawToken.isBlank()) tokens.deleteByTokenHash(TokenUtil.sha256Hex(rawToken));
    }

    /** Returns the user id for a valid, unexpired token, otherwise null. */
    @Transactional
    public Long userIdForToken(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) return null;
        AuthToken t = tokens.findByTokenHash(TokenUtil.sha256Hex(rawToken)).orElse(null);
        if (t == null) return null;
        if (t.expiresAt.isBefore(LocalDateTime.now())) {
            tokens.delete(t);
            return null;
        }
        return t.userId;
    }

    /** Permanently deletes the account and every record in it. Needs the password. */
    @Transactional
    public void deleteAccount(Long userId, String password) {
        User u = users.findById(userId).orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Please log in"));
        if (password == null || !encoder.matches(password, u.passwordHash)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "That password is not correct.");
        }
        // Order matters: records that point at categories go first.
        senderRules.deleteByUserId(userId);
        incomes.deleteByUserId(userId);
        expenses.deleteByUserId(userId);
        budgets.deleteByUserId(userId);
        goals.deleteByUserId(userId);
        settings.deleteByUserId(userId);
        categories.deleteByUserId(userId);
        incomeCategories.deleteByUserId(userId);
        tokens.deleteByUserId(userId);
        users.delete(u);
    }

    private String issueToken(Long userId) {
        tokens.deleteByExpiresAtBefore(LocalDateTime.now());   // tidy up old logins
        String raw = TokenUtil.newToken();
        AuthToken t = new AuthToken();
        t.tokenHash = TokenUtil.sha256Hex(raw);
        t.userId = userId;
        t.expiresAt = LocalDateTime.now().plusDays(tokenDays);
        tokens.save(t);
        return raw;
    }

    private static String normalize(String email) {
        return email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
    }

    private static UserView view(User u) {
        return new UserView(u.id, u.name, u.email);
    }

    private static ResponseStatusException bad(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }
}
