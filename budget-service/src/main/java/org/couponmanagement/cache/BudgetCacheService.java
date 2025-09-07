package org.couponmanagement.cache;


import io.micrometer.observation.annotation.Observed;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.couponmanagement.dto.BudgetErrorCode;
import org.couponmanagement.dto.BudgetCheckResult;
import org.couponmanagement.dto.CacheOperationResult;
import org.couponmanagement.dto.LockResult;
import org.couponmanagement.entity.RegisterStatus;
import org.redisson.api.RAtomicDoubleAsync;
import org.redisson.api.RLock;
import org.redisson.api.RSet;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisTemplate;

import org.springframework.data.redis.core.SessionCallback;
import org.springframework.data.redis.core.RedisOperations;
import org.springframework.dao.DataAccessException;

import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

@Service
@Slf4j
public class BudgetCacheService {
    private final RedisCacheService cacheService;
    private final BudgetCacheProperties cacheProperties;
    private final RedisTemplate<String, Object> redisTemplate;
    private final RedissonClient redissonClient;
    private static final Duration DEFAULT_LOCK_TIMEOUT = Duration.ofSeconds(15);

    private static final BigDecimal SCALE_FACTOR = BigDecimal.valueOf(100);
    private static final int MAX_RETRIES = 3;
    private static final long RETRY_DELAY_MS = 10;

    public BudgetCacheService(RedisCacheService cacheService,
                              BudgetCacheProperties cacheProperties,
                              @Qualifier("redisTemplate") RedisTemplate<String, Object> redisTemplate,
                              RedissonClient redissonClient) {
        this.redissonClient = redissonClient;
        this.cacheService = cacheService;
        this.cacheProperties = cacheProperties;
        this.redisTemplate = redisTemplate;
    }

    @Observed(name = "BudgetCacheService.deductBudget")
    public CacheOperationResult deductBudget(Integer budgetId, Integer couponId, Integer userId, BigDecimal amount) {
        String budgetKey = cacheProperties.getKeyBudgetById(budgetId);
        String trackingKey = cacheProperties.getKeyRegisteredBudget(budgetId, couponId, userId);
        Long amountInMinorUnit = convertToMinorUnitLong(amount);

        log.debug("Starting budget deduction - budgetId: {} couponId: {} userId: {} amount: {} (minor unit: {})",
                budgetId, couponId, userId, amount, amountInMinorUnit);

        return executeBudgetDeductionWithRetry(budgetKey, trackingKey, budgetId, couponId, userId, amountInMinorUnit);
    }

    private CacheOperationResult executeBudgetDeductionWithRetry(String budgetKey, String trackingKey, 
                                                                Integer budgetId, Integer couponId, Integer userId, 
                                                                Long amountInMinorUnit) {
        for (int retryCount = 0; retryCount < MAX_RETRIES; retryCount++) {
            try {
                Boolean success = executeBudgetDeductionTransaction(budgetKey, trackingKey, budgetId, 
                                                                   couponId, userId, amountInMinorUnit, retryCount);
                if (success != null) {
                    return new CacheOperationResult(success, true);
                }

                if (retryCount < MAX_RETRIES - 1) {
                    handleRetry(retryCount, budgetId);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.error("Thread interrupted during budget deduction retry for budgetId: {}", budgetId, e);
                return new CacheOperationResult(false, false);
            } catch (Exception e) {
                log.error("Error deducting budget for budgetId: {} couponId: {} userId: {} amount: {} (attempt {})",
                        budgetId, couponId, userId, amountInMinorUnit, retryCount + 1, e);
                return new CacheOperationResult(false, false);
            }
        }

        log.error("Budget deduction failed after {} retries due to concurrent modifications for budgetId: {} couponId: {} userId: {}",
                MAX_RETRIES, budgetId, couponId, userId);
        return new CacheOperationResult(false, false);
    }

    private Boolean executeBudgetDeductionTransaction(String budgetKey, String trackingKey, 
                                                     Integer budgetId, Integer couponId, Integer userId, 
                                                     Long amountInMinorUnit, int retryCount) {
        return redisTemplate.execute(new SessionCallback<Boolean>() {
            @Override
            public Boolean execute(@NonNull RedisOperations operations) throws DataAccessException {
                operations.watch(budgetKey);

                Long currentBudget = getCurrentBudgetFromCache(operations, budgetKey, budgetId);
                if (currentBudget == null) {
                    operations.unwatch();
                    return false;
                }
                if (!isBudgetSufficient(currentBudget, amountInMinorUnit, budgetId)) {
                    operations.unwatch();
                    return false;
                }
                return executeBudgetTransaction(operations, budgetKey, trackingKey, currentBudget, 
                                              amountInMinorUnit, budgetId, couponId, userId, retryCount);
            }
        });
    }

    private Long getCurrentBudgetFromCache(RedisOperations operations, String budgetKey, Integer budgetId) {
        Object currentBudgetObj = operations.opsForValue().get(budgetKey);

        if (currentBudgetObj == null) {
            log.warn("Budget not found in cache for budgetId: {}", budgetId);
            return null;
        }

        return parseBudgetValue(currentBudgetObj, budgetId);
    }

    private Long parseBudgetValue(Object currentBudgetObj, Integer budgetId) {
        try {
            if (currentBudgetObj instanceof String) {
                return Long.valueOf((String) currentBudgetObj);
            } else if (currentBudgetObj instanceof Number) {
                return ((Number) currentBudgetObj).longValue();
            } else {
                log.error("Unexpected budget type: {} for budgetId: {}",
                        currentBudgetObj.getClass().getSimpleName(), budgetId);
                return null;
            }
        } catch (NumberFormatException e) {
            log.error("Failed to parse budget value for budgetId: {} from: {}", budgetId, currentBudgetObj, e);
            return null;
        }
    }

    private boolean isBudgetSufficient(Long currentBudget, Long requiredAmount, Integer budgetId) {
        if (currentBudget < requiredAmount) {
            log.warn("Insufficient budget for budgetId: {} - current: {} minor units, required: {} minor units",
                    budgetId, currentBudget, requiredAmount);
            return false;
        }
        return true;
    }

    private Boolean executeBudgetTransaction(@NonNull RedisOperations operations, String budgetKey, String trackingKey,
                                           Long currentBudget, Long amountInMinorUnit, Integer budgetId, 
                                           Integer couponId, Integer userId, int retryCount) {
        operations.multi();

        Long newBudget = currentBudget - amountInMinorUnit;
        operations.opsForValue().set(budgetKey, newBudget,
                Duration.ofSeconds(cacheProperties.getBudgetTtlSeconds()));

        operations.opsForValue().set(trackingKey, RegisterStatus.REGISTERED.name(),
                Duration.ofSeconds(cacheProperties.getRegisterTtlSeconds()));
        List<Object> results = operations.exec();
        if (results == null) {
            log.debug("Transaction failed due to concurrent modification - retry attempt {} for budgetId: {}",
                    retryCount + 1, budgetId);
            return null;
        }

        log.debug("Budget deduction successful - budgetId: {} couponId: {} userId: {} amount: {} minor units. " +
                "Budget changed from {} to {}", budgetId, couponId, userId, amountInMinorUnit, currentBudget, newBudget);

        return true;
    }

    private void handleRetry(int retryCount, Integer budgetId) throws InterruptedException {
        log.debug("Retrying budget deduction due to concurrent modification - attempt {} for budgetId: {}",
                retryCount + 2, budgetId);
        Thread.sleep(RETRY_DELAY_MS);
    }

    public void compensateBudget(Integer budgetId, BigDecimal amount) {
        try {
            Optional<BigDecimal> currentAmount = getBudgetAmount(budgetId);
            if (currentAmount.isPresent()) {
                BigDecimal newAmount = currentAmount.get().add(amount);
                setBudgetAmount(budgetId, newAmount);
                log.debug("Compensated budget {} by amount {}. New total: {}", budgetId, amount, newAmount);
            } else {
                log.warn("Cannot compensate budget {} - no current amount found", budgetId);
            }
        } catch (Exception e) {
            log.error("Error compensating budget {} by amount {}", budgetId, amount, e);
        }
    }


    private long parseLongValue(Object value) {
        if (value instanceof String) {
            return Long.parseLong((String) value);
        } else if (value instanceof Number) {
            return ((Number) value).longValue();
        } else {
            throw new IllegalArgumentException("Cannot parse value to long: " + value.getClass());
        }
    }

    public boolean isRegistered(Integer budgetId, Integer couponId, Integer userId) {
        String trackingKey = cacheProperties.getKeyRegisteredBudget(budgetId, couponId, userId);
        Object value = cacheService.getRaw(trackingKey);
        return RegisterStatus.REGISTERED.name().equals(value);
    }


    public Optional<BigDecimal> getBudgetAmount(Integer budgetId) {
        String cacheKey = cacheProperties.getKeyBudgetById(budgetId);
        Object rawValue = cacheService.getRaw(cacheKey);

        if (rawValue == null) return Optional.empty();

        try {
            long minorUnit = parseLongValue(rawValue);
            return Optional.of(convertFromMinorUnit(minorUnit));
        } catch (Exception e) {
            log.warn("Failed to parse budget amount for budgetId: {} from value: {}", budgetId, rawValue, e);
            return Optional.empty();
        }
    }

    private BigDecimal convertFromMinorUnit(long minorUnit) {
        return BigDecimal.valueOf(minorUnit).divide(SCALE_FACTOR, 2, RoundingMode.DOWN);
    }

    public void setBudgetAmount(Integer budgetId, BigDecimal amount) {
        try {
            String cacheKey = cacheProperties.getKeyBudgetById(budgetId);
            RAtomicDoubleAsync atomicDouble = redissonClient.getAtomicDouble(cacheKey);
            atomicDouble.setAsync(amount.doubleValue());
            atomicDouble.expireAsync(Duration.ofSeconds(cacheProperties.getBudgetTtlSeconds()));
            log.debug("Set budget amount for budgetId: {} amount: {} (as string)", budgetId, amount);
        } catch (Exception e) {
            log.error("Error setting budget amount in cache for budgetId: {}", budgetId, e);
        }
    }

    public void setRegistrationStatus(Integer budgetId, Integer couponId, Integer userId, RegisterStatus status) {
        try {
            String trackingKey = cacheProperties.getKeyRegisteredBudget(budgetId, couponId, userId);
            cacheService.put(trackingKey, status.name(), cacheProperties.getRegisterTtlSeconds());
            log.debug("Set registration status for budgetId: {} couponId: {} userId: {} status: {}", 
                budgetId, couponId, userId, status);
        } catch (Exception e) {
            log.error("Error setting registration status in cache for budgetId: {} couponId: {} userId: {}", 
                budgetId, couponId, userId, e);
        }
    }

    public Long convertToMinorUnitLong(BigDecimal amount) {
        if (amount == null) {
            return 0L;
        }

        return amount.multiply(SCALE_FACTOR)
                .setScale(0, RoundingMode.DOWN)
                .longValueExact();
    }

    public BudgetCheckResult checkAndDecrementBudget(Integer budgetId, Integer couponId, Integer userId, BigDecimal amount) {
        String budgetKey = cacheProperties.getKeyBudgetById(budgetId);
        String trackingKey = cacheProperties.getKeyBudgetUsage();
        try {
            RSet<String> usageSet = redissonClient.getSet(trackingKey);
            String trackingValue = budgetId + ":" + couponId + ":" + userId;
            if (usageSet.add(trackingValue)){
                log.debug("Tracking usage for budgetId: {} couponId: {} userId: {} successful",
                        budgetId, couponId, userId);
                RAtomicDoubleAsync atomicDouble = redissonClient.getAtomicDouble(budgetKey);
                Double newValue = atomicDouble.addAndGetAsync(-(amount.doubleValue())).get();
                if (newValue < 0){
                    log.debug("Budget insufficient for budgetId: {} couponId: {} userId: {}. Reverting deduction.",
                            budgetId, couponId, userId);
                    log.debug("Current budget after deduction attempt: {} for budgetId: {}", newValue, budgetId);
                    atomicDouble.addAndGetAsync(amount.doubleValue()).get();
                    usageSet.remove(trackingValue);
                    return new BudgetCheckResult(false, BudgetErrorCode.ALREADY_RESERVED);
                }
                return new BudgetCheckResult(true, BudgetErrorCode.NONE);
            }
            return new BudgetCheckResult(false, BudgetErrorCode.INSUFFICIENT_BUDGET);
        } catch (Exception e) {
            log.error("Error decrementing budget for budgetId: {} by amount: {}", budgetId, amount, e);
            return new BudgetCheckResult(false, BudgetErrorCode.INTERNAL);
        }
    }

    public LockResult acquireLock(String lockKey) {
        String lockId = Thread.currentThread().getName() + "-" + System.currentTimeMillis();

        try {
            RLock lock = redissonClient.getLock(lockKey);
            boolean acquired = lock.tryLock(DEFAULT_LOCK_TIMEOUT.toSeconds(), TimeUnit.SECONDS);

            if (acquired) {
                log.debug("Lock acquired successfully: lockKey={}, lockId={}, timeout={}s",
                        lockKey, lockId, DEFAULT_LOCK_TIMEOUT.getSeconds());
                return new LockResult(lockKey, lockId, true, null);
            } else {
                log.warn("Lock already held by another thread: lockKey={}, lockId={}",
                        lockKey, lockId);
                return new LockResult(lockKey, lockId, false, "Lock already held by another thread");
            }

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("Lock acquisition interrupted: lockKey={}", lockKey, e);
            return new LockResult(lockKey, lockId, false, "Lock acquisition interrupted");
        } catch (Exception e) {
            log.error("Error acquiring lock: lockKey={}", lockKey, e);
            return new LockResult(lockKey, lockId, false, "Lock acquisition error: " + e.getMessage());
        }
    }

    public boolean releaseLock(LockResult lockResult) {
        if (lockResult == null || !lockResult.acquired()) {
            return false;
        }
        try {
            RLock lock = redissonClient.getLock(lockResult.lockKey());
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
                log.debug("Lock released successfully: lockKey={}, lockId={}",
                        lockResult.lockKey(), lockResult.lockId());
                return true;
            } else {
                log.warn("Failed to release lock: lockKey={}, lockId={}, heldByCurrentThread={}",
                        lockResult.lockKey(), lockResult.lockId(), lock.isHeldByCurrentThread());
                return false;
            }

        } catch (Exception e) {
            log.error("Error releasing lock: lockKey={}, lockId={}",
                    lockResult.lockKey(), lockResult.lockId(), e);
            return false;
        }
    }

}
