package com.budgetbee.repo;

import com.budgetbee.model.SavingsGoal;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SavingsGoalRepository extends JpaRepository<SavingsGoal, Long> {
    List<SavingsGoal> findByUserId(Long userId);
    Optional<SavingsGoal> findByIdAndUserId(Long id, Long userId);
    void deleteByUserId(Long userId);
}
