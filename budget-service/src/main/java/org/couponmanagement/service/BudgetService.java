package org.couponmanagement.service;

import io.micrometer.observation.annotation.Observed;
import lombok.extern.slf4j.Slf4j;
import org.couponmanagement.cache.BudgetCacheService;
import org.couponmanagement.cache.BudgetCacheProperties;
import org.couponmanagement.dto.BudgetEvent;
import org.couponmanagement.dto.BudgetCheckResult;
import org.couponmanagement.dto.LockResult;
import org.couponmanagement.dto.RegisterBudgetRequest;
import org.couponmanagement.dto.RegisterBudgetResponse;
import org.couponmanagement.dto.BudgetErrorCode;
import org.couponmanagement.entity.RegisterStatus;
import org.couponmanagement.repository.CouponBudgetUsageRepositoryImpl;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.UUID;
import java.time.LocalDateTime;
import java.util.function.Supplier;

@Service
@Slf4j
public class BudgetService {

    private final BudgetCacheService budgetCacheService;
    private final BudgetCacheProperties budgetCacheProperties;
    private final BudgetEventProducer budgetEventProducer;
    private final CouponBudgetUsageRepositoryImpl couponBudgetUsageRepositoryImpl;


    public BudgetService(BudgetCacheService budgetCacheService,
                        BudgetCacheProperties budgetCacheProperties,
                        BudgetEventProducer budgetEventProducer,
                        CouponBudgetUsageRepositoryImpl couponBudgetUsageRepositoryImpl
                        ) {
        this.budgetCacheService = budgetCacheService;
        this.budgetCacheProperties = budgetCacheProperties;
        this.budgetEventProducer = budgetEventProducer;
        this.couponBudgetUsageRepositoryImpl = couponBudgetUsageRepositoryImpl;
    }


    @Observed(name = "BudgetService.registerBudgetCoupon")
    public RegisterBudgetResponse registerBudgetCoupon(RegisterBudgetRequest request){
        try {

            BudgetCheckResult res = executeUnderLock(request.getBudgetId(), () -> {
                return couponBudgetUsageRepositoryImpl.registerCouponBudget(
                        request.getCounponUserId(),
                        request.getBudgetId(),
                        request.getCouponId(),
                        request.getUserId(),
                        request.getDiscountAmount(),
                        RegisterStatus.REGISTERED
                );
            });

            return RegisterBudgetResponse.builder()
                    .success(true)
                    .message(res.errorCode().getMessage())
                    .errorCode(res.errorCode())
                    .build();
        } catch (Exception e){
            log.error("Error registering budget coupon: {}", e.getMessage(), e);
            return RegisterBudgetResponse.builder()
                    .success(false)
                    .message("Error registering budget coupon")
                    .errorCode(BudgetErrorCode.INTERNAL)
                    .build();
        }
    }



    @Observed(name = "BudgetService.registerBudgetCouponKafka")
    public RegisterBudgetResponse registerBudgetCouponKafka(RegisterBudgetRequest request){
        try {
            String reserveId = UUID.randomUUID().toString();

            BudgetCheckResult result = budgetCacheService.checkAndDecrementBudget(request.getBudgetId(), request.getCouponId(), request.getUserId(), request.getDiscountAmount());

            if (result.success()){
                publishBudgetUsageEvent(request, reserveId);
            }

            return RegisterBudgetResponse.builder()
                    .success(result.success())
                    .message(result.errorCode().getMessage())
                    .errorCode(result.errorCode())
                    .build();

        } catch (Exception e){
            log.error("Error registering budget coupon: {}", e.getMessage(), e);
            return RegisterBudgetResponse.builder()
                    .success(false)
                    .message("Error registering budget coupon")
                    .errorCode(BudgetErrorCode.INTERNAL)
                    .build();
        }
    }

    @Observed(name = "BudgetService.registerBudgetCouponDB")
    public RegisterBudgetResponse registerBudgetCouponDB(RegisterBudgetRequest request){
        try {
            BudgetCheckResult result = proceedBudgetCheckDB(
                    request.getCounponUserId(),
                    request.getBudgetId(),
                    request.getCouponId(),
                    request.getUserId(),
                    request.getDiscountAmount()
            );

            return RegisterBudgetResponse.builder()
                    .success(result.success())
                    .message(result.errorCode().getMessage())
                    .errorCode(result.errorCode())
                    .build();

        } catch (Exception e){
            log.error("Error registering budget coupon: {}", e.getMessage(), e);
            return RegisterBudgetResponse.builder()
                    .success(false)
                    .message("Error registering budget coupon")
                    .errorCode(BudgetErrorCode.INTERNAL)
                    .build();
        }
    }


    public void publishBudgetUsageEvent(RegisterBudgetRequest request, String transactionId) {
        try {
            BudgetEvent event = BudgetEvent.builder()
                    .transactionId(transactionId)
                    .budgetId(request.getBudgetId())
                    .couponId(request.getCouponId())
                    .userId(request.getUserId())
                    .discountAmount(request.getDiscountAmount())
                    .usageTime(LocalDateTime.now())
                    .build();

            budgetEventProducer.sendBudgetEvent(event);
            log.info("Published budget usage event for transaction: {}, budgetId: {}",
                    transactionId, request.getBudgetId());
        } catch (Exception e) {
            log.error("Failed to publish budget usage event for transaction: {}, error: {}",
                     transactionId, e.getMessage(), e);
            throw e;
        }
    }


    private BudgetCheckResult executeUnderLock(Integer budgetId, Supplier<BudgetCheckResult> operation) {
        LockResult lockResult = null;
        String lockKey = budgetCacheProperties.getKeyLockBudget(budgetId);
        try {
            lockResult = budgetCacheService.acquireLock(lockKey);
            if (lockResult == null) {
                log.error("Failed to acquire lock - lockResult is null: budgetId={}", budgetId);
                throw new RuntimeException("Failed to acquire lock - cache service returned null");
            }
            if (!lockResult.acquired()) {
                log.warn("Lock already held - operation rejected: budgetId={}, error={}",
                        budgetId, lockResult.errorMessage());
                throw new RuntimeException("Operation rejected - budget is being processed by another request: " + lockResult.errorMessage());
            }
            return operation.get();
        } catch (Exception e) {
            log.error("Error executing operation under lock: budgetId={}, error={}",
                    budgetId, e.getMessage(), e);
            throw e;
        } finally {
            if (lockResult != null && lockResult.acquired()) {
                boolean released = budgetCacheService.releaseLock(lockResult);
                if (released) {
                    log.debug("Lock released for operation: budgetId={}", budgetId);
                } else {
                    log.warn("Failed to release lock for operation: budgetId={}", budgetId);
                }
            }
        }
    }

    public BudgetCheckResult proceedBudgetCheckDB(Long coupon_user_id, Integer budgetId, Integer couponId, Integer userId, BigDecimal amount){
        try {
            return couponBudgetUsageRepositoryImpl.registerCouponBudget(
                    coupon_user_id,
                    budgetId,
                    couponId,
                    userId,
                    amount,
                    RegisterStatus.REGISTERED
            );

        } catch (Exception e){
            log.error("Error checking budget in DB for coupon_user_id: {}, error: {}",
                    coupon_user_id, e.getMessage(), e);
            return new BudgetCheckResult(
                    false,
                    BudgetErrorCode.INTERNAL
            );
        }
    }
}
