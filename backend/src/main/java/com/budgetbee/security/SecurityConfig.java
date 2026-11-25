package com.budgetbee.security;

import com.budgetbee.service.AuthService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Everything under /api needs a login, except registering, logging in and the health check.
 * The web page itself (HTML, CSS, JS) is public. Logins use tokens, so there are no cookies and no CSRF risk.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http, AuthService auth) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .httpBasic(basic -> basic.disable())
            .formLogin(form -> form.disable())
            .authorizeHttpRequests(a -> a
                .requestMatchers("/api/auth/register", "/api/auth/login", "/api/health").permitAll()
                .requestMatchers("/api/**").authenticated()
                .anyRequest().permitAll())
            .exceptionHandling(e -> e.authenticationEntryPoint((req, res, ex) -> {
                res.setStatus(401);
                res.setContentType(MediaType.APPLICATION_JSON_VALUE);
                res.setCharacterEncoding("UTF-8");
                res.getWriter().write("{\"error\":\"Please log in\"}");
            }))
            .addFilterBefore(new TokenAuthFilter(auth), UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }
}
