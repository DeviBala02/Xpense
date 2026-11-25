package com.budgetbee.repo;

import com.budgetbee.model.IncomeCategory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface IncomeCategoryRepository extends JpaRepository<IncomeCategory, Long> {
    List<IncomeCategory> findByUserId(Long userId);
    Optional<IncomeCategory> findByUserIdAndNameIgnoreCase(Long userId, String name);
    Optional<IncomeCategory> findByIdAndUserId(Long id, Long userId);
    void deleteByUserId(Long userId);
}
