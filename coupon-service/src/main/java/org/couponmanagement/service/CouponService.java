package org.couponmanagement.service;

import io.grpc.Channel;
import io.micrometer.observation.annotation.Observed;
import lombok.extern.slf4j.Slf4j;
import org.couponmanagement.budget.BudgetServiceGrpc;
import org.couponmanagement.budget.BudgetServiceProto;
import org.couponmanagement.cache.CouponCacheService;
import org.couponmanagement.cache.RedisLockService;
import org.couponmanagement.dto.CouponDetail;
import org.couponmanagement.dto.CouponErrorCode;
import org.couponmanagement.dto.EvaluateRuleResponsePayload;
import org.couponmanagement.dto.RegisterStatus;
import org.couponmanagement.dto.RollBackBudgetEvent;
import org.couponmanagement.dto.RuleCollectionResult;
import org.couponmanagement.dto.UserCouponClaimInfo;
import org.couponmanagement.dto.UserCouponIds;
import org.couponmanagement.entity.Coupon;
import org.couponmanagement.entity.CouponUser;
import org.couponmanagement.grpc.annotation.PerformanceMonitor;
import org.couponmanagement.grpc.client.GrpcClientFactory;
import org.couponmanagement.grpc.validation.RequestValidator;
import org.couponmanagement.repository.CouponRepository;
import org.couponmanagement.repository.CouponUserRepository;
import org.couponmanagement.rule.RuleServiceGrpc;
import org.couponmanagement.rule.RuleServiceProto;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.interceptor.TransactionAspectSupport;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

@Service
@Slf4j
public class CouponService {

    private final CouponUserRepository couponUserRepository;
    private final CouponRepository couponRepository;
    private final RequestValidator validator;
    private final CouponCacheService couponCacheService;
    private final RedisLockService redisLockService;
    private final Executor couponEvaluationExecutor;
    private final GrpcClientFactory grpcClientFactory;

    public CouponService(
            CouponUserRepository couponUserRepository,
            CouponRepository couponRepository,
            RequestValidator validator,
            CouponCacheService couponCacheService,
            RedisLockService redisLockService,
            @Qualifier("couponEvaluationExecutor") Executor couponEvaluationExecutor,
            GrpcClientFactory grpcClientFactory) {
        this.couponUserRepository = couponUserRepository;
        this.couponRepository = couponRepository;
        this.validator = validator;
        this.couponCacheService = couponCacheService;
        this.redisLockService = redisLockService;
        this.couponEvaluationExecutor = couponEvaluationExecutor;
        this.grpcClientFactory = grpcClientFactory;
    }

    @Observed(name = "get-user-coupons", contextualName = "user-coupons-retrieval")
    public UserCouponsResult getUserCouponsWithPagination(Integer userId, int page, int size) {
        log.info("Getting user coupons: userId={}, page={}, size={}", userId, page, size);

        try {
            validator.validateUserId(userId);

            Optional<UserCouponIds> cachedUserCouponIds =
                    couponCacheService.getCachedUserCouponIds(userId);

            List<UserCouponClaimInfo> userCouponClaimInfos;
            Map<Integer, CouponDetail> couponDetailMap;

            if (cachedUserCouponIds.isPresent()) {
                userCouponClaimInfos = cachedUserCouponIds.get().getCouponDetails();
                List<Integer> couponIds = cachedUserCouponIds.get().getCouponIds();

                log.debug("Cache hit for user coupon IDs: userId={}, totalCoupons={}", userId, couponIds.size());

                couponDetailMap = couponCacheService.getCachedCouponDetailsBatch(couponIds);

                List<Integer> missingCouponIds = couponIds.parallelStream()
                        .filter(id -> !couponDetailMap.containsKey(id))
                        .toList();

                if (!missingCouponIds.isEmpty()) {
                    log.debug("Cache miss for some coupon details: userId={}, missingCount={}", userId, missingCouponIds.size());
                    handleMissingCouponDetails(userId, missingCouponIds, couponDetailMap);
                }

            } else {
                log.debug("Cache miss for user coupon IDs: userId={}", userId);
                var dbResult = loadUserCouponsFromDatabase(userId);
                userCouponClaimInfos = dbResult.userCouponClaimInfos();
                couponDetailMap = dbResult.couponDetailMap();
            }

            List<UserCouponClaimInfo> activeUserCouponClaimInfos = userCouponClaimInfos.parallelStream()
                    .filter(claimInfo -> {
                        CouponDetail couponDetail = couponDetailMap.get(claimInfo.getCouponId());
                        return couponDetail != null && couponDetail.isActive();
                    })
                    .toList();

            int totalCount = activeUserCouponClaimInfos.size();
            List<UserCouponClaimInfo> paginatedCouponClaimInfos =
                    applyPagination(activeUserCouponClaimInfos, page, size);

            log.info("User coupons retrieved: userId={}, totalCount={}, page={}, size={}, returnedCount={}",
                    userId, totalCount, page, size, paginatedCouponClaimInfos.size());

            return new UserCouponsResult(
                    paginatedCouponClaimInfos,
                    couponDetailMap,
                    totalCount,
                    page,
                    size
            );

        } catch (Exception e) {
            log.error("Error getting user coupons: userId={}, error={}", userId, e.getMessage(), e);
            throw new RuntimeException("Failed to get user coupons: " + e.getMessage(), e);
        }
    }

    private void handleMissingCouponDetails(Integer userId, List<Integer> missingCouponIds,
                                            Map<Integer, CouponDetail> couponDetailMap) {
        List<CouponUser> missingCouponUsers = couponUserRepository.findActiveCouponsByUserId(userId).parallelStream()
                .filter(cu -> missingCouponIds.contains(cu.getCouponId()))
                .toList();

        for (CouponUser couponUser : missingCouponUsers) {
            if (couponUser.getCoupon() != null) {
                CouponDetail couponDetail =
                        CouponDetail.fromCoupon(couponUser.getCoupon());
                couponDetailMap.put(couponUser.getCouponId(), couponDetail);
                couponCacheService.cacheCouponDetail(couponUser.getCouponId(), couponDetail);
            }
        }
    }

    private DatabaseCouponsResult loadUserCouponsFromDatabase(Integer userId) {
        List<CouponUser> allCouponUsers = couponUserRepository.findActiveCouponsByUserId(userId);

        Map<Integer, UserCouponClaimInfo> userCouponInfoMap = allCouponUsers.parallelStream()
                .collect(Collectors.toMap(
                        CouponUser::getCouponId,
                        cu -> new UserCouponClaimInfo(
                                cu.getId(),
                                cu.getUserId(),
                                cu.getCouponId(),
                                cu.getCreatedAt(),
                                cu.getExpiryDate()
                        )
                ));

        List<UserCouponClaimInfo> userCouponClaimInfos =
                new ArrayList<>(userCouponInfoMap.values());

        if (!userCouponInfoMap.isEmpty()) {
            UserCouponIds userCouponIdsCache =
                    UserCouponIds.of(userCouponInfoMap);
            couponCacheService.cacheUserCouponIds(userId, userCouponIdsCache);
        }

        Map<Integer, CouponDetail> couponDetailMap = new HashMap<>();
        for (CouponUser couponUser : allCouponUsers) {
            if (couponUser.getCoupon() != null) {
                CouponDetail couponDetail = CouponDetail.fromCoupon(couponUser.getCoupon());
                couponDetailMap.put(couponUser.getCouponId(), couponDetail);
                couponCacheService.cacheCouponDetail(couponUser.getCouponId(), couponDetail);
            }
        }

        return new DatabaseCouponsResult(userCouponClaimInfos, couponDetailMap);
    }

    private List<UserCouponClaimInfo> applyPagination(
            List<UserCouponClaimInfo> userCouponClaimInfos, int page, int size) {
        int startIndex = page * size;
        int endIndex = Math.min(startIndex + size, userCouponClaimInfos.size());

        if (startIndex >= userCouponClaimInfos.size()) {
            return List.of();
        } else {
            return userCouponClaimInfos.subList(startIndex, endIndex);
        }
    }

    public record UserCouponsResult(
            List<UserCouponClaimInfo> userCouponClaimInfos,
            Map<Integer, CouponDetail> couponDetailMap,
            int totalCount,
            int page,
            int size
    ) {
    }

    @Observed(name = "apply-coupon-manual", contextualName = "manual-coupon-application")
    @PerformanceMonitor
    public CouponApplicationResult applyCouponManual(Integer userId, String couponCode, BigDecimal orderAmount,
                                                     LocalDateTime orderDate) {
        log.info("Applying coupon manually: userId={}, couponCode={}, orderAmount={}",
                userId, couponCode, orderAmount);

        try {
            Integer couponId = getCouponIdFromCacheOrDB(couponCode);
            if (couponId == null) {
                return CouponApplicationResult.buildResult(
                        null,
                        BigDecimal.valueOf(0),
                        "Coupon not found",
                        false,
                        CouponErrorCode.COUPON_NOT_FOUND.name()
                );
            }

            CouponUserResult couponUserResult = getCouponUserFromCacheOrDB(userId, couponId, couponCode);
            if (!couponUserResult.success()) {
                return CouponApplicationResult.buildResult(
                        null,
                        BigDecimal.valueOf(0),
                        couponUserResult.errorMessage(),
                        false,
                        CouponErrorCode.COUPON_NOT_FOUND.name()
                );
            }

            CouponUser couponUser = couponUserResult.couponUser();
            Coupon coupon = couponUserResult.coupon();

            if (!couponUser.isUsable(orderDate)) {
                return CouponApplicationResult.buildResult(
                        couponUser,
                        BigDecimal.valueOf(0),
                        "Coupon is expired",
                        false,
                        CouponErrorCode.COUPON_EXPIRED.name()
                );
            }

            BigDecimal discountAmount = coupon.calculateDiscount(orderAmount);
            if (discountAmount.compareTo(BigDecimal.ZERO) <= 0) {
                return CouponApplicationResult.failure("No discount applicable for this order amount", CouponErrorCode.INVALID_DISCOUNT_AMOUNT.name());
            }

            if (isCouponExpired(couponUser, coupon, orderDate)) {
                return CouponApplicationResult.buildResult(
                        couponUser,
                        BigDecimal.valueOf(0),
                        "Coupon is expired",
                        false,
                        CouponErrorCode.COUPON_EXPIRED.name()
                );
            }

            RuleEvaluationResult ruleResult = evaluateRulesForCoupon(couponUser, userId, orderAmount, orderDate);
            if (!ruleResult.success()) {
                return CouponApplicationResult.buildResult(
                        couponUser,
                        discountAmount,
                        ruleResult.errorMessage(),
                        false,
                        CouponErrorCode.RULE_VIOLATION.name()
                );
            }

            BudgetRegistrationResult registrationResult = registerBudgetForCoupon(
                    couponUser.getId(),
                    userId,
                    coupon.getId(),
                    coupon.getBudgetId(),
                    discountAmount
            );
            if (!registrationResult.isSuccess()) {
                return CouponApplicationResult.buildResult(
                        couponUser,
                        discountAmount,
                        registrationResult.message(),
                        false,
                        registrationResult.errorCode()
                );
            }

            boolean successInvalidateCache = invalidateCacheCouponUser(userId, couponUser.getCouponId());
            if (!successInvalidateCache) {
                log.warn("Failed to invalidate user coupon cache after manual application: userId={}, couponId={}",
                        userId, couponUser.getCouponId());
                return CouponApplicationResult.buildResult(
                        couponUser,
                        discountAmount,
                        "Failed to invalidate coupon cache",
                        false,
                        CouponErrorCode.INTERNAL_ERROR.name()
                );
            }

            updateCoupon(couponId, userId,
                    CouponUser.CouponUserStatus.USED, LocalDateTime.now());

            log.info("Manual coupon applied successfully: couponId={}, discount={}",
                    coupon.getId(), discountAmount);
            return CouponApplicationResult.buildResult(
                    couponUser,
                    discountAmount,
                    null,
                    true,
                    null
            );

        } catch (Exception e) {
            log.error("Error applying coupon manually: userId={}, couponCode={}, error={}",
                    userId, couponCode, e.getMessage(), e);
            return CouponApplicationResult.failure("Internal error: " + e.getMessage(), CouponErrorCode.INTERNAL_ERROR.name());
        }
    }


    public CompletableFuture<Void> updateCouponAsync(Integer couponId, Integer userId,
                                                     CouponUser.CouponUserStatus newStatus,
                                                     LocalDateTime usedAt) {
        return CompletableFuture.runAsync(() -> {
            try {
                couponUserRepository.markCouponUsed(userId, couponId, usedAt, newStatus);
                log.info("Updated coupon usage status for couponId: {}, userId: {} to status: {}",
                        couponId, userId, newStatus);
            } catch (Exception e) {
                log.error("Error updating coupon usage status for couponId: {}, userId: {}: {}",
                        couponId, userId, e.getMessage(), e);
            }
        }, couponEvaluationExecutor);
    }

    public void updateCoupon(Integer couponId, Integer userId,
                                                     CouponUser.CouponUserStatus newStatus,
                                                     LocalDateTime usedAt) {
        try {
            couponUserRepository.markCouponUsed(userId, couponId, usedAt, newStatus);
            log.info("Updated coupon usage status for couponId: {}, userId: {} to status: {}",
                    couponId, userId, newStatus);
        } catch (Exception e) {
            log.error("Error updating coupon usage status for couponId: {}, userId: {}: {}",
                    couponId, userId, e.getMessage(), e);
            throw e;
        }
    }


    @PerformanceMonitor
    @Observed(name = "getCouponIdFromCacheOrDB", contextualName = "CouponService.getCouponIdFromCacheOrDB")
    private Integer getCouponIdFromCacheOrDB(String couponCode) {
        Optional<Integer> couponIdOpt = couponCacheService.getCouponIdByCode(couponCode);
        if (couponIdOpt.isPresent()) {
            log.debug("Cache hit for coupon ID: couponCode={}, couponId={}", couponCode, couponIdOpt.get());
            return couponIdOpt.get();
        }

        Optional<Coupon> coupon = couponRepository.findByCodeIgnoreCase(couponCode);
        if (coupon.isEmpty()) {
            log.debug("Coupon not found in database: couponCode={}", couponCode);
            return null;
        }

        Integer couponId = coupon.get().getId();
        couponCacheService.cacheCouponCodeMapping(couponCode, couponId);
        log.debug("Cache miss for coupon ID: couponCode={}, couponId={} - cached", couponCode, couponId);
        return couponId;
    }

    @PerformanceMonitor
    private CouponUserResult getCouponUserFromCacheOrDB(Integer userId, Integer couponId, String couponCode) {
        Optional<UserCouponIds> cachedUserCoupons = couponCacheService.getCachedUserCouponIds(userId);
        if (cachedUserCoupons.isPresent()) {
            log.debug("Cache UserCouponIds hit for user coupons: userId={}", userId);

            UserCouponClaimInfo userCouponClaimInfo = cachedUserCoupons.get().getCouponClaimInfo(couponId);
            if (userCouponClaimInfo == null) {
                return CouponUserResult.failure("Coupon not found for user");
            }

            Optional<CouponDetail> couponDetail = couponCacheService.getCachedCouponDetail(couponId);
            if (couponDetail.isPresent()) {
                CouponUser couponUser = CouponUser.buildFromDetailAndClaimInfo(couponDetail.get(), userCouponClaimInfo);
                Coupon coupon = couponUser.getCoupon();
                if (!coupon.getIsActive()) {
                    return CouponUserResult.failure("Coupon not found for user");
                }
                return CouponUserResult.success(couponUser, coupon);
            }

            Optional<Coupon> coupon = couponRepository.findById(couponId);
            if (coupon.isEmpty()) {
                return CouponUserResult.failure("Coupon detail not found");
            }

            CouponDetail couponDetailFromDB = CouponDetail.fromCoupon(coupon.get());
            couponCacheService.cacheCouponDetail(couponId, couponDetailFromDB);

            if (!couponDetailFromDB.isActive()) {
                return CouponUserResult.failure("Coupon not found for user");
            }

            CouponUser couponUser = CouponUser.buildFromDetailAndClaimInfo(couponDetailFromDB, userCouponClaimInfo);
            log.debug("Cache miss for coupon detail: couponId={} - cached", couponId);
            return CouponUserResult.success(couponUser, coupon.get());
        }

        log.debug("Cache UserCouponIds miss for user coupons: userId={}", userId);
        Optional<CouponUser> couponUser = couponUserRepository.findByUserIdAndCouponCode(userId, couponCode);
        if (couponUser.isEmpty()) {
            return CouponUserResult.failure("Coupon not found for user");
        }

        CouponUser user = couponUser.get();
        Coupon coupon = user.getCoupon();
        if (coupon == null) {
            return CouponUserResult.failure("Coupon entity is null");
        }

        CouponDetail couponDetail = CouponDetail.fromCoupon(coupon);
        couponCacheService.cacheCouponDetail(couponId, couponDetail);

        Map<Integer, UserCouponClaimInfo> userCouponInfoMap = Map.of(
                couponId, new UserCouponClaimInfo(user.getId(), user.getUserId(), user.getCouponId(), user.getCreatedAt(), user.getExpiryDate())
        );
        UserCouponIds userCouponIdsCache = UserCouponIds.of(userCouponInfoMap);
        couponCacheService.cacheUserCouponIds(userId, userCouponIdsCache);

        return CouponUserResult.success(user, coupon);
    }

    @PerformanceMonitor
    private Coupon buildCouponFromDetail(CouponDetail couponDetail) {
        return Coupon.builder()
                .id(couponDetail.getCouponId())
                .collectionKeyId(couponDetail.getCollectionKeyId())
                .budgetId(couponDetail.getBudgetId())
                .code(couponDetail.getCouponCode())
                .type(couponDetail.getType())
                .title(couponDetail.getTitle())
                .description(couponDetail.getDescription())
                .discountConfigJson(couponDetail.getDiscountConfigJson())
                .expiryDate(couponDetail.getExpiryDate())
                .isActive(couponDetail.isActive())
                .createdAt(couponDetail.getCreatedAt())
                .updatedAt(couponDetail.getUpdatedAt())
                .build();
    }

    private record CouponUserResult(boolean success, CouponUser couponUser, Coupon coupon, String errorMessage) {

        public static CouponUserResult success(CouponUser couponUser, Coupon coupon) {
            return new CouponUserResult(true, couponUser, coupon, null);
        }

        public static CouponUserResult failure(String errorMessage) {
            return new CouponUserResult(false, null, null, errorMessage);
        }

    }

    private boolean isCouponExpired(CouponUser couponUser, Coupon coupon, LocalDateTime orderDate) {
        return (couponUser.getExpiryDate() != null && couponUser.getExpiryDate().isBefore(orderDate)) ||
                (coupon.getExpiryDate() != null && coupon.getExpiryDate().isBefore(orderDate));
    }


    @PerformanceMonitor
    private RuleEvaluationResult evaluateRulesForCoupon(CouponUser couponUser, Integer userId,
                                                        BigDecimal orderAmount,
                                                        LocalDateTime orderDate) {
        try {
            String requestID = String.valueOf(UUID.randomUUID());

            Channel channel = grpcClientFactory.getRuleServiceChannel();
            RuleServiceGrpc.RuleServiceBlockingStub stub = RuleServiceGrpc.newBlockingStub(channel);

            Integer collectionKeyId = couponUser.getCoupon().getCollectionKeyId();

            if (collectionKeyId == 0) {
                return new RuleEvaluationResult(true, null);
            }

            var grpcRequest = RuleServiceProto.EvaluateRuleRequest.newBuilder()
                    .setRequestId(requestID)
                    .setUserId(userId)
                    .setOrderAmount(orderAmount.doubleValue())
                    .setOrderDate(orderDate.toString())
                    .addAllRuleCollectionIds(List.of(collectionKeyId))
                    .build();

            var grpcResponse = stub.evaluateRuleCollections(grpcRequest);

            if (grpcResponse.getStatus().getCode() != RuleServiceProto.StatusCode.OK) {
                log.warn("Rule service call failed for auto coupon: userId={}, couponCode={}, status={}, message={}",
                        userId, couponUser.getCoupon().getCode(), grpcResponse.getStatus().getCode(), grpcResponse.getStatus().getMessage());
                return new RuleEvaluationResult(false, "Rule evaluation failed: " + grpcResponse.getStatus().getMessage());
            }

            return processRuleCollectionResults(grpcResponse.getPayload(), userId, couponUser.getCoupon().getCode());

        } catch (Exception e) {
            log.error("Error during rule evaluation: userId={}, couponCode={}, error={}",
                    userId, couponUser.getCoupon().getCode(), e.getMessage(), e);
            return new RuleEvaluationResult(false, "Rule evaluation error: " + e.getMessage());
        }
    }

    private RuleEvaluationResult processRuleCollectionResults(RuleServiceProto.EvaluateRuleResponsePayload payload,
                                                              Integer userId, String couponCode) {
        boolean allRulesPassed = true;
        StringBuilder failureReason = new StringBuilder();

        for (var ruleResult : payload.getRuleCollectionResultsList()) {
            if (!ruleResult.getIsSuccess()) {
                allRulesPassed = false;
                if (!failureReason.isEmpty()) {
                    failureReason.append(", ");
                }
                failureReason.append(ruleResult.getErrorMessage());
            }
        }

        if (!allRulesPassed) {
            log.warn("Rule validation failed for auto coupon application: userId={}, couponCode={}, reasons={}",
                    userId, couponCode, failureReason.toString());
            return new RuleEvaluationResult(false, failureReason.toString());
        }

        log.info("All business rules passed for auto coupon application: userId={}, couponCode={}", userId, couponCode);
        return new RuleEvaluationResult(true, null);
    }

    private record RuleEvaluationResult(boolean success, String errorMessage) {
    }

    public CouponApplicationResult applyCouponAutoMultiple(
            Integer userId, BigDecimal orderAmount, LocalDateTime orderDate
    ){
        validator.validateUserId(userId);
        validator.validateOrderAmount(orderAmount.doubleValue());
        List<CouponUser> availableCoupons = getAvailableCouponsForUser(userId);
        if (availableCoupons.isEmpty()) {
            return CouponApplicationResult.failure("No available coupons for user", CouponErrorCode.NO_AVAILABLE_COUPONS.name());
        }

        List<CouponUser> validCoupons = availableCoupons.parallelStream()
                .filter(couponUser -> {
                    Coupon coupon = couponUser.getCoupon();
                    if (coupon == null) return false;

                    return !isCouponExpired(couponUser, coupon, orderDate);
                })
                .toList();
        if (validCoupons.isEmpty()) {
            return CouponApplicationResult.failure("No valid coupons available for user", CouponErrorCode.NO_AVAILABLE_COUPONS.name());
        }

        List<CouponWithRuleEvaluationResult> evaluationResults = evaluateMultipleCouponWithRule(
                validCoupons, orderAmount, userId, orderDate
        );
        CouponWithRuleEvaluationResult bestResult = evaluationResults.parallelStream()
                .filter(result -> result.isValid() && result.discountAmount().compareTo(BigDecimal.ZERO) > 0)
                .max(Comparator.comparing(CouponWithRuleEvaluationResult::discountAmount))
                .orElse(null);
        if (bestResult == null) {
            return CouponApplicationResult.failure("No applicable coupon found after rule evaluation", CouponErrorCode.NO_AVAILABLE_COUPONS.name());
        }
        CouponUser bestCoupon = validCoupons.stream()
                .filter(couponUser -> couponUser.getCouponId().equals(bestResult.couponUser().getCouponId()))
                .findFirst()
                .orElse(null);
        BigDecimal bestDiscount = bestResult.discountAmount();

        BudgetRegistrationResult registrationResult = registerBudgetForCoupon(
                bestCoupon.getId(),
                userId,
                bestCoupon.getCouponId(),
                bestCoupon.getCoupon().getBudgetId(),
                bestDiscount
        );

        if (!registrationResult.isSuccess()) {
            return CouponApplicationResult.failure("No applicable coupon found after parallel rule evaluation", registrationResult.errorCode);
        }

        log.info("Parallel auto coupon evaluation completed: couponId={}, discount={}",
                bestCoupon.getCouponId(), bestDiscount);
        return CouponApplicationResult.buildResult(
                bestCoupon,
                bestDiscount,
                null,
                true,
                null
        );
    }

    @Observed(name = "apply-coupon-auto-parallel", contextualName = "parallel-auto-coupon-application")
    @PerformanceMonitor
    public CompletableFuture<CouponApplicationResult> applyCouponAutoParallel(Integer userId, BigDecimal orderAmount, LocalDateTime orderDate) {
        log.info("Auto-applying best coupon (parallel): userId={}, orderAmount={}", userId, orderAmount);

        return CompletableFuture.supplyAsync(() -> {
            try {
                validator.validateUserId(userId);
                validator.validateOrderAmount(orderAmount.doubleValue());

                List<CouponUser> availableCoupons = getAvailableCouponsForUser(userId);
                if (availableCoupons.isEmpty()) {
                    return CouponApplicationResult.failure("No available coupons for user", CouponErrorCode.NO_AVAILABLE_COUPONS.name());
                }

                List<CouponUser> validCoupons = availableCoupons.parallelStream()
                        .filter(couponUser -> {
                            Coupon coupon = couponUser.getCoupon();
                            if (coupon == null) return false;

                            return !isCouponExpired(couponUser, coupon, orderDate);
                        })
                        .toList();

                if (validCoupons.isEmpty()) {
                    return CouponApplicationResult.failure("No valid coupons available for user", CouponErrorCode.NO_AVAILABLE_COUPONS.name());
                }

                List<CompletableFuture<CouponWithRuleEvaluationResult>> evaluationFutures = validCoupons.parallelStream()
                        .map(couponUser -> evaluateCouponWithRulesAsync(couponUser, orderAmount, userId, orderDate))
                        .toList();

                List<CouponWithRuleEvaluationResult> evaluationResults = evaluateMultipleCouponWithRule(
                        validCoupons, orderAmount, userId, orderDate
                );

                CompletableFuture<Void> allEvaluations = CompletableFuture.allOf(
                        evaluationFutures.toArray(new CompletableFuture[0])
                );

                CouponWithRuleEvaluationResult bestResult = allEvaluations.thenApply(v -> {
                    return evaluationFutures.parallelStream()
                            .map(CompletableFuture::join)
                            .filter(result -> result.isValid() && result.discountAmount().compareTo(BigDecimal.ZERO) > 0)
                            .max(Comparator.comparing(CouponWithRuleEvaluationResult::discountAmount))
                            .orElse(null);
                }).join();

                if (bestResult != null) {
                    CouponUser bestCoupon = bestResult.couponUser();
                    BigDecimal bestDiscount = bestResult.discountAmount();

                    CouponUserResult couponUserResult = getCouponUserFromCacheOrDB(userId, bestCoupon.getCouponId(), bestCoupon.getCoupon().getCode());

                    Coupon coupon = bestCoupon.getCoupon();
                    BudgetRegistrationResult registrationResult = registerBudgetForCoupon(
                            bestCoupon.getId(),
                            userId,
                            coupon.getId(),
                            coupon.getBudgetId(),
                            bestDiscount
                    );

                    if (!registrationResult.isSuccess()) {
                        return CouponApplicationResult.failure("No applicable coupon found after parallel rule evaluation", registrationResult.errorCode);
                    }

                    log.info("Parallel auto coupon evaluation completed: couponId={}, discount={}",
                            bestCoupon.getCouponId(), bestDiscount);
                    return CouponApplicationResult.buildResult(
                            bestCoupon,
                            bestDiscount,
                            null,
                            true,
                            null
                    );
                } else {
                    return CouponApplicationResult.failure("No applicable coupon found after parallel rule evaluation", CouponErrorCode.NO_AVAILABLE_COUPONS.name());
                }

            } catch (Exception e) {
                log.error("Error in parallel auto-applying coupon: userId={}, error={}", userId, e.getMessage(), e);
                return CouponApplicationResult.failure("Internal error: " + e.getMessage(), CouponErrorCode.INTERNAL_ERROR.name());
            }
        }, couponEvaluationExecutor);
    }

    private EvaluateRuleResponsePayload evaluateRule(
            List<Integer> ruleCollectionIds, Integer userId, BigDecimal orderAmount,
            LocalDateTime orderDate ) {
        try {
            String requestID = String.valueOf(UUID.randomUUID());

            Channel channel = grpcClientFactory.getRuleServiceChannel();
            RuleServiceGrpc.RuleServiceBlockingStub stub = RuleServiceGrpc.newBlockingStub(channel);

            var grpcRequest = RuleServiceProto.EvaluateRuleRequest.newBuilder()
                    .setRequestId(requestID)
                    .setUserId(userId)
                    .setOrderAmount(orderAmount.doubleValue())
                    .setOrderDate(orderDate.toString())
                    .addAllRuleCollectionIds(ruleCollectionIds)
                    .build();

            var grpcResponse = stub.evaluateRuleCollections(grpcRequest);

            if (grpcResponse.getStatus().getCode() != RuleServiceProto.StatusCode.OK) {
                log.warn("Rule service call failed: userId={}, ruleCollectionIds={}, status={}, message={}",
                        userId, ruleCollectionIds, grpcResponse.getStatus().getCode(), grpcResponse.getStatus().getMessage());

                List<RuleCollectionResult> failedResults = ruleCollectionIds.stream()
                    .map(ruleCollectionId -> new RuleCollectionResult(
                        ruleCollectionId,
                        false,
                        "Rule service call failed: " + grpcResponse.getStatus().getMessage()
                    ))
                    .toList();

                return new EvaluateRuleResponsePayload(requestID, userId, failedResults);
            }

            var payload = grpcResponse.getPayload();
            List<RuleCollectionResult> ruleCollectionResults = payload.getRuleCollectionResultsList().parallelStream()
                .map(grpcResult -> new RuleCollectionResult(
                    grpcResult.getRuleCollectionId(),
                    grpcResult.getIsSuccess(),
                    grpcResult.getErrorMessage()
                ))
                .toList();

            log.debug("Rule service response: userId={}, requestId={}, ruleCollectionResults={}",
                    userId, requestID, ruleCollectionResults);

            log.info("Rule evaluation completed: userId={}, requestId={}, totalRules={}, passedRules={}",
                    userId, requestID, ruleCollectionResults.size(),
                    ruleCollectionResults.stream().mapToLong(r -> r.success() ? 1 : 0).sum());

            return new EvaluateRuleResponsePayload(requestID, userId, ruleCollectionResults);

        } catch (Exception e) {
            log.error("Error during rule evaluation: userId={}, ruleCollectionIds={}, error={}",
                    userId, ruleCollectionIds, e.getMessage(), e);

            List<RuleCollectionResult> failedResults = ruleCollectionIds.stream()
                .map(ruleCollectionId -> new RuleCollectionResult(
                    ruleCollectionId,
                    false,
                    "Internal error during rule evaluation: " + e.getMessage()
                ))
                .toList();

            return new EvaluateRuleResponsePayload(
                String.valueOf(UUID.randomUUID()),
                userId,
                failedResults
            );
        }
    }


    private List<CouponWithRuleEvaluationResult> evaluateMultipleCouponWithRule(
            List<CouponUser> couponUsers, BigDecimal orderAmount, Integer userId, LocalDateTime orderDate) {
        List<Integer> ruleCollectionIds = couponUsers.parallelStream()
                .map(cu -> cu.getCoupon().getCollectionKeyId())
                .distinct()
                .toList();
        EvaluateRuleResponsePayload evaluateRuleResponsePayload = evaluateRule(
                ruleCollectionIds, userId, orderAmount, orderDate
        );
        var ruleCollectionResultMap = evaluateRuleResponsePayload.ruleCollectionResults().stream()
                .collect(Collectors.toMap(RuleCollectionResult::ruleCollectionId, Function.identity()));

        return couponUsers.parallelStream().map(couponUser -> {
            var coupon = couponUser.getCoupon();
            if (coupon == null) {
                return new CouponWithRuleEvaluationResult(couponUser, BigDecimal.ZERO, false, "Coupon entity is null");
            }

            var discountAmount = coupon.calculateDiscount(orderAmount);
            if (discountAmount.compareTo(BigDecimal.ZERO) <= 0) {
                return new CouponWithRuleEvaluationResult(couponUser, BigDecimal.ZERO, false, "No applicable discount for order amount");
            }

            var ruleResult = ruleCollectionResultMap.get(coupon.getCollectionKeyId());
            if (ruleResult == null || !ruleResult.success()) {
                return new CouponWithRuleEvaluationResult(
                        couponUser,
                        discountAmount,
                        false,
                        ruleResult != null ? ruleResult.errorMessage() : "Rule evaluation failed"
                );
            }

            return new CouponWithRuleEvaluationResult(couponUser, discountAmount, true, null);
        }).toList();
    }


    @PerformanceMonitor
    private CompletableFuture<CouponWithRuleEvaluationResult> evaluateCouponWithRulesAsync(
            CouponUser couponUser, BigDecimal orderAmount, Integer userId, LocalDateTime orderDate) {

        return CompletableFuture.supplyAsync(() -> {
                    try {
                        Coupon coupon = couponUser.getCoupon();
                        if (coupon == null) {
                            return new CouponWithRuleEvaluationResult(couponUser, BigDecimal.ZERO, false, "Coupon entity is null");
                        }

                        BigDecimal discountAmount = coupon.calculateDiscount(orderAmount);
                        if (discountAmount.compareTo(BigDecimal.ZERO) <= 0) {
                            return new CouponWithRuleEvaluationResult(couponUser, BigDecimal.ZERO, false, "No applicable discount for order amount");
                        }

                        log.debug("Coupon discount calculated: couponId={}, discount={}", coupon.getId(), discountAmount);
                        return new CouponWithRuleEvaluationResult(couponUser, discountAmount, true, null);

                    } catch (Exception e) {
                        log.warn("Error calculating discount for coupon: couponId={}, error={}",
                                couponUser.getCouponId(), e.getMessage(), e);
                        return new CouponWithRuleEvaluationResult(couponUser, BigDecimal.ZERO, false, "Discount calculation error: " + e.getMessage());
                    }
                }, couponEvaluationExecutor)
                .thenCompose(discountResult -> {
                    if (!discountResult.isValid()) {
                        return CompletableFuture.completedFuture(discountResult);
                    }

                    return evaluateRulesAsync(couponUser, userId, orderAmount, orderDate)
                            .thenApply(ruleResult -> {
                                if (ruleResult.success()) {
                                    return new CouponWithRuleEvaluationResult(
                                            discountResult.couponUser(),
                                            discountResult.discountAmount(),
                                            true,
                                            null);
                                } else {
                                    return new CouponWithRuleEvaluationResult(
                                            discountResult.couponUser(),
                                            discountResult.discountAmount(),
                                            false,
                                            ruleResult.errorMessage());
                                }
                            });
                });
    }

    private CompletableFuture<RuleEvaluationResult> evaluateRulesAsync(
            CouponUser couponUser, Integer userId, BigDecimal orderAmount,
            LocalDateTime orderDate) {

        return CompletableFuture.supplyAsync(() -> {
            try {
                String requestID = String.valueOf(UUID.randomUUID());

                Channel channel = grpcClientFactory.getRuleServiceChannel();
                RuleServiceGrpc.RuleServiceBlockingStub stub = RuleServiceGrpc.newBlockingStub(channel);

                Optional<CouponDetail> cachedCouponDetail = getCouponDetailByCode(couponUser.getCoupon().getCode());
                if (cachedCouponDetail.isEmpty()) {
                    return new RuleEvaluationResult(false, "Coupon detail not found for rule evaluation");
                }

                var grpcRequest = RuleServiceProto.EvaluateRuleRequest.newBuilder()
                        .setRequestId(requestID)
                        .setUserId(userId)
                        .setOrderAmount(orderAmount.doubleValue())
                        .setOrderDate(orderDate.toString())
                        .addAllRuleCollectionIds(List.of(cachedCouponDetail.get().getCollectionKeyId()))
                        .build();

                var grpcResponse = stub.evaluateRuleCollections(grpcRequest);

                if (grpcResponse.getStatus().getCode() != RuleServiceProto.StatusCode.OK) {
                    log.warn("Rule service call failed for parallel evaluation: userId={}, couponCode={}, status={}, message={}",
                            userId, couponUser.getCoupon().getCode(), grpcResponse.getStatus().getCode(), grpcResponse.getStatus().getMessage());
                    return new RuleEvaluationResult(false, "Rule evaluation failed: " + grpcResponse.getStatus().getMessage());
                }

                return processRuleCollectionResults(grpcResponse.getPayload(), userId, couponUser.getCoupon().getCode());

            } catch (Exception e) {
                log.error("Error during parallel rule evaluation: userId={}, couponCode={}, error={}",
                        userId, couponUser.getCoupon().getCode(), e.getMessage(), e);
                return new RuleEvaluationResult(false, "Rule evaluation error: " + e.getMessage());
            }
        }, couponEvaluationExecutor);
    }


    private record CouponWithRuleEvaluationResult(
            CouponUser couponUser,
            BigDecimal discountAmount,
            boolean isValid,
            String errorMessage
    ) {
    }

    @Observed(name = "apply-coupon-auto-parallel-sync", contextualName = "parallel-auto-coupon-application-sync")
    @Transactional(rollbackFor = Exception.class)
    public CouponApplicationResult applyCouponAutoParallelSync(Integer userId, BigDecimal orderAmount, LocalDateTime orderDate) {
        try {
            CouponApplicationResult result = applyCouponAutoParallel(userId, orderAmount, orderDate).get();

            if (result.isSuccess() && result.getCouponId() != null) {

                invalidateCacheCouponUser(userId, result.getCouponId());

                log.info("Parallel auto coupon applied successfully: couponId={}, discount={}",
                        result.getCouponId(), result.getDiscountAmount());
            }

            return result;
        } catch (Exception e) {
            log.error("Error in parallel sync auto-applying coupon: userId={}, error={}", userId, e.getMessage(), e);
            return CouponApplicationResult.failure("Internal error: " + e.getMessage(), CouponErrorCode.INTERNAL_ERROR.name());
        }
    }


    private boolean executeUnderLock(Integer userId, Integer couponId, Supplier<Boolean> operation) {
        RedisLockService.LockResult lockResult = null;

        try {
            lockResult = redisLockService.acquireCouponUserLock(userId, couponId);
            if (!lockResult.acquired()) {
                log.warn("Lock already held - operation rejected: userId={}, couponId={}, error={}",
                        userId, couponId, lockResult.errorMessage());
                throw new RuntimeException("Operation rejected - coupon is being processed by another request: " + lockResult.errorMessage());
            }
            log.debug("Lock acquired for operation: userId={}, couponId={}, lockId={}",
                    userId, couponId, lockResult.lockId());
            return operation.get();
        } catch (Exception e) {
            log.error("Error executing operation under lock: userId={}, couponId={}, error={}",
                    userId, couponId, e.getMessage(), e);
            throw e;
        } finally {
            if (lockResult != null && lockResult.acquired()) {
                boolean released = redisLockService.releaseLock(lockResult);
                if (released) {
                    log.debug("Lock released for operation: userId={}, couponId={}", userId, couponId);
                } else {
                    log.warn("Failed to release lock for operation: userId={}, couponId={}", userId, couponId);
                }
            }
        }
    }

    @PerformanceMonitor
    @Observed(name = "invalidate-coupon-user-cache", contextualName = "CouponService.invalidateCouponUserCache")
    public boolean invalidateCacheCouponUser(Integer userId, Integer couponId) {
        return executeUnderLock(userId, couponId, () -> {
            return couponCacheService.invalidateUserCache(userId, couponId);
        });
    }

    @Observed(name = "list-coupons", contextualName = "coupons-listing")
    public CouponsListResult listCoupons(int page, int size) {
        log.info("Listing coupons: page={}, size={}", page, size);

        try {
            Pageable pageable = PageRequest.of(page, size);

            Page<Coupon> couponPage = couponRepository.findAllCoupons(pageable);

            List<CouponDetail> couponDetails = couponPage.getContent().parallelStream()
                    .map(CouponDetail::fromCoupon)
                    .toList();

            CouponsListResult result = new CouponsListResult(
                    couponDetails,
                    couponPage.getTotalElements(),
                    page,
                    size
            );

            log.info("Coupons listed from database: totalCount={}, page={}, size={}",
                    couponPage.getTotalElements(), page, size);

            return result;

        } catch (Exception e) {
            log.error("Error listing coupons: page={}, size={}, error={}",
                    page, size, e.getMessage(), e);
            throw new RuntimeException("Failed to list coupons: " + e.getMessage(), e);
        }
    }

    public record CouponsListResult(
            List<CouponDetail> couponDetails,
            long totalCount,
            int page,
            int size
    ) {
    }


    @Observed(name = "get-coupon-detail-by-code", contextualName = "coupon-detail-fetch")
    private Optional<CouponDetail> getCouponDetailByCode(String couponCode) {
        Optional<Coupon> couponOpt = couponRepository.findByCodeIgnoreCase(couponCode);
        if (couponOpt.isEmpty()) {
            return Optional.empty();
        }

        Coupon coupon = couponOpt.get();

        Optional<CouponDetail> cachedDetail = couponCacheService.getCachedCouponDetail(coupon.getId());
        if (cachedDetail.isPresent()) {
            log.debug("Cache hit for coupon detail: couponCode={}, couponId={}", couponCode, coupon.getId());
            return cachedDetail;
        }

        CouponDetail couponDetail = CouponDetail.fromCoupon(coupon);
        couponCacheService.cacheCouponDetail(coupon.getId(), couponDetail);
        log.debug("Cache miss for coupon detail: couponCode={}, couponId={} - cached", couponCode, coupon.getId());

        return Optional.of(couponDetail);
    }

    private record DatabaseCouponsResult(
            List<UserCouponClaimInfo> userCouponClaimInfos,
            Map<Integer, CouponDetail> couponDetailMap
    ) {
    }


    private List<CouponUser> getAvailableCouponsForUser(Integer userId) {
        Optional<UserCouponIds> cachedUserCoupons = couponCacheService.getCachedUserCouponIds(userId);
        if (cachedUserCoupons.isPresent()) {
            log.debug("Cache hit for user coupons: userId={}", userId);
            return buildCouponUsersFromCache(cachedUserCoupons.get());
        }

        log.debug("Cache miss for user coupons: userId={}", userId);
        return loadAndCacheAvailableCouponsOptimized(userId);
    }

    private List<CouponUser> buildCouponUsersFromCache(UserCouponIds cachedUserCoupons) {
        List<UserCouponClaimInfo> userCouponClaimInfos = cachedUserCoupons.getCouponDetails();

        List<Integer> couponIds = userCouponClaimInfos.stream()
                .map(UserCouponClaimInfo::getCouponId)
                .toList();

        Map<Integer, CouponDetail> cachedCouponDetails = couponCacheService.getCachedCouponDetailsBatch(couponIds);

        List<Integer> missingCouponIds = couponIds.stream()
                .filter(id -> !cachedCouponDetails.containsKey(id))
                .toList();

        if (!missingCouponIds.isEmpty()) {
            log.debug("Cache miss for some coupon details: missingCount={}", missingCouponIds.size());
            Map<Integer, CouponDetail> missingCouponDetails = loadMissingCouponDetails(missingCouponIds);
            cachedCouponDetails.putAll(missingCouponDetails);
        }

        return userCouponClaimInfos.parallelStream()
                .map(claimInfo -> {
                    CouponDetail couponDetail = cachedCouponDetails.get(claimInfo.getCouponId());
                    if (couponDetail != null) {
                        return CouponUser.buildFromDetailAndClaimInfo(couponDetail, claimInfo);
                    }
                    return null;
                })
                .filter(Objects::nonNull)
                .filter(couponUser -> couponUser.isUsable(LocalDateTime.now()))
                .toList();
    }

    private Map<Integer, CouponDetail> loadMissingCouponDetails(List<Integer> missingCouponIds) {
        Map<Integer, CouponDetail> missingCouponDetails = new HashMap<>();

        List<Coupon> missingCoupons = missingCouponIds.parallelStream()
                .map(couponRepository::findById)
                .filter(Optional::isPresent)
                .map(Optional::get)
                .toList();

        missingCoupons.parallelStream().forEach(coupon -> {
            CouponDetail couponDetail = CouponDetail.fromCoupon(coupon);
            missingCouponDetails.put(coupon.getId(), couponDetail);
            couponCacheService.cacheCouponDetail(coupon.getId(), couponDetail);
        });

        return missingCouponDetails;
    }

    private List<CouponUser> loadAndCacheAvailableCouponsOptimized(Integer userId) {
        List<CouponUser> availableCoupons = couponUserRepository.findAvailableCouponsByUserId(userId);

        if (!availableCoupons.isEmpty()) {
            Map<Integer, UserCouponClaimInfo> userCouponInfoMap = availableCoupons.stream()
                    .collect(Collectors.toMap(
                            CouponUser::getCouponId,
                            cu -> new UserCouponClaimInfo(
                                    cu.getId(),
                                    cu.getUserId(),
                                    cu.getCouponId(),
                                    cu.getCreatedAt(),
                                    cu.getExpiryDate()
                            )
                    ));

            UserCouponIds userCouponIdsCache = UserCouponIds.of(userCouponInfoMap);
            couponCacheService.cacheUserCouponIds(userId, userCouponIdsCache);

            availableCoupons.parallelStream().forEach(couponUser -> {
                if (couponUser.getCoupon() != null) {
                    CouponDetail couponDetail = CouponDetail.fromCoupon(couponUser.getCoupon());
                    couponCacheService.cacheCouponDetail(couponUser.getCouponId(), couponDetail);
                }
            });
        }

        return availableCoupons;
    }

    @Observed(name = "register-budget-for-coupon", contextualName = "budget-registration")
    @PerformanceMonitor
    public BudgetRegistrationResult registerBudgetForCoupon(Long couponUserId, Integer userId, Integer couponId, Integer budgetId, BigDecimal discountAmount) {
        log.info("Registering budget for coupon: userId={}, couponId={}, budgetId={}, discountAmount={}",
                userId, couponId, budgetId, discountAmount);

        try {
            String requestId = UUID.randomUUID().toString();

            Channel channel = grpcClientFactory.getBudgetServiceChannel();
            BudgetServiceGrpc.BudgetServiceBlockingStub stub = BudgetServiceGrpc.newBlockingStub(channel);

            var grpcRequest = BudgetServiceProto.RegisterBudgetCouponRequest.newBuilder()
                    .setRequestId(requestId)
                    .setCouponUserId(couponUserId)
                    .setUserId(userId)
                    .setCouponId(couponId)
                    .setBudgetId(budgetId)
                    .setDiscountAmount(discountAmount.doubleValue())
                    .build();

            var grpcResponse = stub.register(grpcRequest);

            if (grpcResponse.getStatus().getCode() != BudgetServiceProto.StatusCode.OK) {
                log.warn("Budget registration failed: userId={}, couponId={}, budgetId={}, status={}, message={}",
                        userId, couponId, budgetId, grpcResponse.getStatus().getCode(), grpcResponse.getStatus().getMessage());

                return BudgetRegistrationResult.failure(grpcResponse.getStatus().getMessage(), grpcResponse.getError().getCode());
            }

            boolean isSuccess = grpcResponse.getPayload().getSuccess();
            String message = grpcResponse.getPayload().getMessage();
            String errorCode = grpcResponse.getError().getCode();
            if (isSuccess) {
                log.info("Budget registration successful: userId={}, couponId={}, budgetId={}",
                        userId, couponId, budgetId);
                return BudgetRegistrationResult.success("Budget registered successfully");
            } else {
                log.info("Budget registration failed: userId={}, couponId={}, budgetId={}, message={}",
                        userId, couponId, budgetId, message);
                return BudgetRegistrationResult.failure("Budget registration failed: " + message, errorCode);
            }

        } catch (Exception e) {
            log.error("Error registering budget: userId={}, couponId={}, budgetId={}, error={}",
                    userId, couponId, budgetId, e.getMessage(), e);
            return BudgetRegistrationResult.failure("Budget registration error: " + e.getMessage(), CouponErrorCode.INTERNAL_ERROR.name());
        }
    }

    public record BudgetRegistrationResult(
            boolean isSuccess,
            String message,
            String errorCode
    ) {
        public static BudgetRegistrationResult success(String message) {
            return new BudgetRegistrationResult(true, message, null);
        }

        public static BudgetRegistrationResult failure(String message, String errorCode) {
            return new BudgetRegistrationResult(false, message, errorCode);
        }
    }
}
