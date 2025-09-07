package org.couponmanagement.service;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.couponmanagement.dto.BudgetErrorCode;
import org.couponmanagement.dto.BudgetEvent;
import org.couponmanagement.dto.RollBackBudgetEvent;
import org.couponmanagement.entity.RegisterStatus;
import org.couponmanagement.performance.CustomMetricsRegistry;
import org.couponmanagement.performance.ErrorMetricsRegistry;
import org.couponmanagement.repository.BudgetRepository;
import org.couponmanagement.repository.CouponBudgetUsageRepository;
import org.couponmanagement.repository.CouponBudgetUsageRepositoryImpl;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Slf4j
@Service
@AllArgsConstructor
public class BudgetUsageService {
    private final BudgetRepository budgetRepository;
    private final CouponBudgetUsageRepository couponBudgetUsageRepository;
    private final CouponBudgetUsageRepositoryImpl couponBudgetUsageRepositoryImpl;
    private final ErrorMetricsRegistry errorMetricsRegistry;

    public void processBudgetUsage(BudgetEvent event) {
        try {
            log.info("Processing budget : {} for transaction: {}", event.getBudgetId(), event.getTransactionId());
            processRegisterEvent(event);
            log.info("Successfully processed budget event: {} for transaction: {}",
                    event.getBudgetId(), event.getTransactionId());

        } catch (Exception e) {
            log.error("Error processing budget usage for event {}: {}", event, e.getMessage(), e);
            errorMetricsRegistry.incrementBusinessError(
                    BudgetErrorCode.SERVICE_UNAVAILABLE.name(),
                    "BudgetService"
            );
            throw e;
        }
    }

    public void processRollbackBudgetUsage(RollBackBudgetEvent event) {
        try {
            log.info("Processing rollback budget : {} for user: {} with coupon : {}", event.getBudgetId(), event.getUserId(), event.getCouponId());
            rollbackBudgetUsage(event);
        } catch (Exception e) {
            log.error("Error processing rollback budget usage for event {}: {}", event, e.getMessage(), e);
            throw e;
        }
    }

    @Transactional
    public void processRegisterEvent(BudgetEvent event) {
        try {
            LocalDateTime now = LocalDateTime.now();
            int inserted = couponBudgetUsageRepository.recordBudgetUsage(
                    event.getTransactionId(),
                    event.getBudgetId(),
                    event.getCouponId(),
                    event.getUserId(),
                    event.getDiscountAmount(),
                    RegisterStatus.REGISTERED.name(),
                    event.getUsageTime(),
                    now,
                    now
            );

            if (inserted > 0) {
                budgetRepository.updateBudgetWithDeduction(event.getBudgetId(), event.getDiscountAmount());
                log.info("Registered budget usage for transaction: {}, budgetId: {}",
                        event.getTransactionId(), event.getBudgetId());
            } else {
                log.warn("Failed to insert budget usage record for transaction: {}", event.getTransactionId());
            }
        } catch (Exception e) {
            log.error("Error in processRegisterEvent for event {}: {}", event, e.getMessage(), e);
            throw e;
        }
    }

    @Transactional
    private void rollbackBudgetUsage(RollBackBudgetEvent event) {
        try {
            boolean updated = couponBudgetUsageRepositoryImpl.reverseBudgetUsageAndRefund(
                    event.getBudgetId(),
                    event.getCouponId(),
                    event.getUserId(),
                    RegisterStatus.CANCELLED
            );
            if (!updated){
                log.warn("No budget usage record found to rollback for userId: {}, budgetId: {}, couponId: {}",
                        event.getUserId(),
                        event.getBudgetId(), event.getCouponId());
                errorMetricsRegistry.incrementBusinessError(
                        BudgetErrorCode.ROLLBACK_FAILED.name(),
                        "BudgetService"
                );
                throw new RuntimeException("No budget usage record found to rollback");
            } else {
                log.info("Successfully rolled back budget usage for userId: {}, budgetId: {}, couponId: {}",
                        event.getUserId(), event.getBudgetId(), event.getCouponId());
            }

        } catch (Exception e) {
            log.error("Error rolling back budget usage for event {}: {}", event, e.getMessage(), e);
            errorMetricsRegistry.incrementBusinessError(
                    BudgetErrorCode.ROLLBACK_FAILED.name(),
                    "BudgetService"
            );
            throw e;
        }
    }
}
