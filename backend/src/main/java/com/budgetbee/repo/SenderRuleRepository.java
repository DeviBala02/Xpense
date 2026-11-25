package com.budgetbee.repo;

import com.budgetbee.model.SenderRule;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface SenderRuleRepository extends JpaRepository<SenderRule, Long> {
    Optional<SenderRule> findByUserIdAndSenderIgnoreCase(Long userId, String sender);
    void deleteByUserId(Long userId);
}
