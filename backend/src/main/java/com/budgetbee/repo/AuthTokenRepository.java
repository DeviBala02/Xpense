package com.budgetbee.repo;

import com.budgetbee.model.AuthToken;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.Optional;

public interface AuthTokenRepository extends JpaRepository<AuthToken, Long> {
    Optional<AuthToken> findByTokenHash(String tokenHash);
    void deleteByTokenHash(String tokenHash);
    void deleteByUserId(Long userId);
    void deleteByExpiresAtBefore(LocalDateTime time);
}
