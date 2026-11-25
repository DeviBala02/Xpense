package com.budgetbee.web;

import com.budgetbee.security.CurrentUser;
import com.budgetbee.service.AuthService;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api")
public class AuthController {

    public record RegisterRequest(String name, String email, String password) {}
    public record LoginRequest(String email, String password) {}
    public record PasswordRequest(String password) {}

    private final AuthService auth;

    public AuthController(AuthService auth) {
        this.auth = auth;
    }

    @GetMapping("/health")
    public Map<String, String> health() {
        return Map.of("status", "ok");
    }

    @PostMapping("/auth/register")
    public AuthService.Session register(@RequestBody RegisterRequest r) {
        return auth.register(r.name(), r.email(), r.password());
    }

    @PostMapping("/auth/login")
    public AuthService.Session login(@RequestBody LoginRequest r) {
        return auth.login(r.email(), r.password());
    }

    @GetMapping("/auth/me")
    public AuthService.UserView me() {
        return auth.me(CurrentUser.id());
    }

    /** Logs out this device. */
    @PostMapping("/auth/logout")
    public Map<String, String> logout(@RequestHeader(value = "Authorization", required = false) String header) {
        if (header != null && header.startsWith("Bearer ")) auth.logout(header.substring(7).trim());
        return Map.of("message", "Logged out");
    }

    /** Deletes the account and all its data for good. */
    @PostMapping("/auth/delete-account")
    public Map<String, String> deleteAccount(@RequestBody PasswordRequest r) {
        auth.deleteAccount(CurrentUser.id(), r.password());
        return Map.of("message", "Your account and all its data were deleted.");
    }
}
