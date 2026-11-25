package com.budgetbee.repo;

import com.budgetbee.model.Category;
import com.budgetbee.model.Expense;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface ExpenseRepository extends JpaRepository<Expense, Long> {

    List<Expense> findByUserIdAndSpentAtGreaterThanEqualAndSpentAtLessThanOrderBySpentAtDesc(Long userId, LocalDateTime from, LocalDateTime to);

    List<Expense> findTop10ByUserIdOrderBySpentAtDesc(Long userId);

    Expense findFirstByUserIdOrderByIdDesc(Long userId);

    Optional<Expense> findByIdAndUserId(Long id, Long userId);

    void deleteByUserId(Long userId);

    @Query("select coalesce(sum(e.amount), 0) from Expense e where e.userId = :uid and e.spentAt >= :from and e.spentAt < :to")
    BigDecimal sumBetween(@Param("uid") Long uid, @Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    @Query("select coalesce(sum(e.amount), 0) from Expense e where e.userId = :uid and e.category = :cat and e.spentAt >= :from and e.spentAt < :to")
    BigDecimal sumByCategoryBetween(@Param("uid") Long uid, @Param("cat") Category cat, @Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    @Query("select e.category.name, coalesce(sum(e.amount), 0) from Expense e where e.userId = :uid and e.spentAt >= :from and e.spentAt < :to group by e.category.name order by sum(e.amount) desc")
    List<Object[]> totalsByCategory(@Param("uid") Long uid, @Param("from") LocalDateTime from, @Param("to") LocalDateTime to);
}
