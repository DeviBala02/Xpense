package com.budgetbee.repo;

import com.budgetbee.model.Budget;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface BudgetRepository extends JpaRepository<Budget, Long> {
    List<Budget> findByUserIdAndEndDateGreaterThanEqualOrderByEndDateAsc(Long userId, LocalDate date);
    Optional<Budget> findByIdAndUserId(Long id, Long userId);
    void deleteByUserId(Long userId);
}
