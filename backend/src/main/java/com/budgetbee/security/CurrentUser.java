package com.budgetbee.security;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.server.ResponseStatusException;

/** The id of the logged-in user for the request being handled. Every query uses it, so users never see each other's data. */
public final class CurrentUser {

    private CurrentUser() {}

    public static Long id() {
        Authentication a = SecurityContextHolder.getContext().getAuthentication();
        if (a != null && a.getPrincipal() instanceof Long id) return id;
        throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Please log in");
    }
}
