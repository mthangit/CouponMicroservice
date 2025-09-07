package org.couponmanagement.repository;

import io.micrometer.observation.annotation.Observed;
import org.couponmanagement.entity.Budget;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

public interface BudgetRepository extends JpaRepository<Budget, Integer> {
    @Modifying
    @Transactional
    @Query(value = """
        UPDATE budget
        SET remaining = remaining - :discountAmount,
            updated_at = NOW()
        WHERE id = :budgetId
          AND remaining >= :discountAmount
        """, nativeQuery = true)
    @Observed(name = "BudgetRepository.updateBudgetWithDeduction")
    int updateBudgetWithDeduction(
            @Param("budgetId") Integer budgetId,
            @Param("discountAmount") BigDecimal discountAmount
    );

    @Transactional
    @Modifying
    @Query("UPDATE Budget b SET b.remaining = b.remaining + :discountAmount WHERE b.id = :budgetId")
    int compensateBudget(
            @Param("budgetId") Integer budgetId,
            @Param("discountAmount") BigDecimal discountAmount);


}
