package org.couponmanagement.repository;

import org.couponmanagement.dto.BudgetCheckResult;
import org.couponmanagement.entity.RegisterStatus;

import java.math.BigDecimal;

public interface CouponBudgetUsageRepositoryCustom {
    boolean reverseBudgetUsageAndRefund(Integer budgetId, Integer couponId, Integer userId, RegisterStatus status);
    BudgetCheckResult registerCouponBudget(Long coupon_user_id, Integer budgetId, Integer couponId, Integer userId, BigDecimal amount, RegisterStatus status);
}
