package com.budgetbee.repo;

import com.budgetbee.model.Income;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface IncomeRepository extends JpaRepository<Income, Long> {

    List<Income> findByUserIdAndReceivedAtGreaterThanEqualAndReceivedAtLessThanOrderByReceivedAtDesc(Long userId, LocalDateTime from, LocalDateTime to);

    List<Income> findByUserIdAndCategoryIsNullOrderByReceivedAtDesc(Long userId);

    List<Income> findByUserIdAndSenderIgnoreCaseAndCategoryIsNull(Long userId, String sender);

    long countByUserIdAndCategoryIsNull(Long userId);

    boolean existsByUserIdAndCategoryId(Long userId, Long categoryId);

    Optional<Income> findByIdAndUserId(Long id, Long userId);

    void deleteByUserId(Long userId);

    @Query("select coalesce(sum(i.amount), 0) from Income i where i.userId = :uid and i.receivedAt >= :from and i.receivedAt < :to")
    BigDecimal sumBetween(@Param("uid") Long uid, @Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    /** Category name is null for credits that still need a category. */
    @Query("select c.name, coalesce(sum(i.amount), 0) from Income i left join i.category c where i.userId = :uid and i.receivedAt >= :from and i.receivedAt < :to group by c.name order by sum(i.amount) desc")
    List<Object[]> totalsByCategory(@Param("uid") Long uid, @Param("from") LocalDateTime from, @Param("to") LocalDateTime to);
}
