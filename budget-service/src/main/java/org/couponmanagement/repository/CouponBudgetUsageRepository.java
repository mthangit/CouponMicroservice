package org.couponmanagement.repository;

import io.micrometer.observation.annotation.Observed;
import org.couponmanagement.entity.CouponBudgetUsage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public interface CouponBudgetUsageRepository extends JpaRepository<CouponBudgetUsage, String> {
    @Modifying
    @Query(value = """
        INSERT IGNORE INTO coupon_budget_usage (id, budget_id, coupon_id, user_id, amount, status, usage_time, created_at, updated_at)
        VALUES (:id, :budgetId, :couponId, :userId, :amount, :status, :usage_time, :createdAt, :updatedAt)
        """, nativeQuery = true)
    @Observed(name = "ReserveRepository.insertReserve")
    @Transactional
    int recordBudgetUsage(
            @Param("id") String id,
            @Param("budgetId") Integer budgetId,
            @Param("couponId") Integer couponId,
            @Param("userId") Integer userId,
            @Param("amount") BigDecimal amount,
            @Param("status") String status,
            @Param("usage_time") LocalDateTime usage_time,
            @Param("createdAt") LocalDateTime createdAt,
            @Param("updatedAt") LocalDateTime updatedAt
    );

    @Query(value = """
        SELECT COUNT(1)
        FROM reserve
        WHERE budget_id = :budgetId
          AND coupon_id = :couponId
          AND user_id = :userId
          AND status = 'REGISTERED'
        """, nativeQuery = true)
    int countRegistered(
            @Param("budgetId") Integer budgetId,
            @Param("couponId") Integer couponId,
            @Param("userId") Integer userId
    );

    @Modifying
    @Query(value = """
        UPDATE coupon_budget_usage
        SET amount = amount - :rollbackAmount,
            status = :status,
            updated_at = CURRENT_TIMESTAMP
        WHERE budget_id = :budgetId
          AND coupon_id = :couponId
          AND user_id = :userId
          AND amount >= :rollbackAmount
        """, nativeQuery = true)
    int rollbackBudgetUsage(@Param("budgetId") Integer budgetId,
                            @Param("couponId") Integer couponId,
                            @Param("userId") Integer userId,
                            @Param("rollbackAmount") BigDecimal rollbackAmount,
                            @Param("status") String status);

    @Query("SELECT COUNT(c) FROM CouponBudgetUsage c WHERE c.budgetId = :budgetId")
    int countByBudgetId(@Param("budgetId") Integer budgetId);

    @Query("SELECT c FROM CouponBudgetUsage c WHERE c.id = :transactionId")
    CouponBudgetUsage findByTransactionId(@Param("transactionId") String transactionId);
}
