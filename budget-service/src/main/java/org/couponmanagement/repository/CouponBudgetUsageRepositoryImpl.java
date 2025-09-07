package org.couponmanagement.repository;

import io.micrometer.observation.annotation.Observed;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.couponmanagement.dto.BudgetCheckResult;
import org.couponmanagement.dto.BudgetErrorCode;
import org.couponmanagement.entity.RegisterStatus;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.interceptor.TransactionAspectSupport;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Repository
@RequiredArgsConstructor
public class CouponBudgetUsageRepositoryImpl implements CouponBudgetUsageRepositoryCustom {
    private final EntityManager entityManager;

    @Override
    public boolean reverseBudgetUsageAndRefund(Integer budgetId, Integer couponId, Integer userId, RegisterStatus status) {
        int updatedUsage = entityManager.createNativeQuery("""
                        UPDATE coupon_budget_usage
                        SET status = :newStatus,
                            updated_at = CURRENT_TIMESTAMP
                        WHERE budget_id = :budgetId
                          AND coupon_id = :couponId
                          AND user_id = :userId
                          AND status = :oldStatus
                        """)
                .setParameter("newStatus", status)
                .setParameter("budgetId", budgetId)
                .setParameter("couponId", couponId)
                .setParameter("userId", userId)
                .setParameter("oldStatus", RegisterStatus.REGISTERED)
                .executeUpdate();

        if (updatedUsage == 0) {
            return false;
        }
        int updatedBudget = entityManager.createNativeQuery("""
                            UPDATE budget b
                            JOIN coupon_budget_usage u ON u.budget_id = b.id
                            SET b.remaining = b.remaining + u.amount,
                                b.updated_at = CURRENT_TIMESTAMP
                            WHERE b.id = :budgetId
                              AND u.coupon_id = :couponId
                              AND u.user_id = :userId
                        """)
                .setParameter("budgetId", budgetId)
                .setParameter("couponId", couponId)
                .setParameter("userId", userId)
                .executeUpdate();

        return updatedBudget > 0;
    }

    @Override
    @Transactional
    @Observed(name = "CouponBudgetUsageRepository.registerCouponBudget")
    public BudgetCheckResult registerCouponBudget(Long coupon_user_id, Integer budgetId, Integer couponId, Integer userId, BigDecimal amount, RegisterStatus status) {
        try {
            int inserted = entityManager.createNativeQuery("""
                            INSERT IGNORE INTO coupon_budget_usage (coupon_user_id, budget_id, coupon_id, user_id, amount, status, usage_time, created_at, updated_at)
                            VALUES (:coupon_user_id, :budgetId, :couponId, :userId, :amount, :status, :usage_time, :created_at, :updated_at)
                            """)
                    .setParameter("coupon_user_id", coupon_user_id)
                    .setParameter("budgetId", budgetId)
                    .setParameter("couponId", couponId)
                    .setParameter("userId", userId)
                    .setParameter("amount", amount)
                    .setParameter("usage_time", LocalDateTime.now())
                    .setParameter("status", status.name())
                    .setParameter("created_at", LocalDateTime.now())
                    .setParameter("updated_at", LocalDateTime.now())
                    .executeUpdate();

            if (inserted == 0) {
                return new BudgetCheckResult(
                        false,
                        BudgetErrorCode.ALREADY_RESERVED
                );
            }

            int updatedBudget = entityManager.createNativeQuery("""
                            UPDATE budget
                            SET remaining = remaining - :amount,
                                updated_at = CURRENT_TIMESTAMP
                            WHERE id = :budgetId
                              AND remaining >= :amount
                            """)
                    .setParameter("budgetId", budgetId)
                    .setParameter("amount", amount)
                    .executeUpdate();

            if (updatedBudget == 0) {
                TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
                return new BudgetCheckResult(false, BudgetErrorCode.INSUFFICIENT_BUDGET);
            }

            return new BudgetCheckResult(
                    true,
                    BudgetErrorCode.NONE
            );

        } catch (Exception e) {
            TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
            return new BudgetCheckResult(false, BudgetErrorCode.INTERNAL);
        }
    }


    @Transactional
    @Observed(name = "CouponBudgetUsageRepository.revertBudget")
    public boolean revertBudget(Long coupon_user_id, Integer budgetId, Integer couponId, Integer userId, RegisterStatus status){
        try {

            int updated = entityManager.createNativeQuery("""
                            UPDATE coupon_budget_usage
                            SET status = :newStatus,
                                updated_at = CURRENT_TIMESTAMP
                            WHERE coupon_user_id = :coupon_user_id
                              AND budget_id = :budgetId
                              AND coupon_id = :couponId
                              AND user_id = :userId
                              AND status = :oldStatus
                            """)
                    .setParameter("newStatus", status)
                    .setParameter("coupon_user_id", coupon_user_id)
                    .setParameter("budgetId", budgetId)
                    .setParameter("couponId", couponId)
                    .setParameter("userId", userId)
                    .setParameter("oldStatus", RegisterStatus.REGISTERED)
                    .executeUpdate();

            if (updated == 1) {
                int updatedBudget = entityManager.createNativeQuery("""
                            UPDATE budget b
                            JOIN coupon_budget_usage u ON u.budget_id = b.id
                            SET b.remaining = b.remaining + u.amount,
                                b.updated_at = CURRENT_TIMESTAMP
                            WHERE b.id = :budgetId
                              AND u.coupon_id = :couponId
                              AND u.user_id = :userId
                        """)
                        .setParameter("budgetId", budgetId)
                        .setParameter("couponId", couponId)
                        .setParameter("userId", userId)
                        .executeUpdate();
                return updatedBudget != 0;
            } else {
                return false;
            }
        } catch (Exception e){
            return false;
        }
    }
}

