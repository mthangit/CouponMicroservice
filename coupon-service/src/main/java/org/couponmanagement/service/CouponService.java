package org.couponmanagement.service;

import io.grpc.Channel;
import io.micrometer.observation.annotation.Observed;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.couponmanagement.cache.CouponCacheService;
import org.couponmanagement.coupon.CouponServiceProto;
import org.couponmanagement.dto.CouponDetail;
import org.couponmanagement.dto.UserCouponClaimInfo;
import org.couponmanagement.dto.UserCouponIds;
import org.couponmanagement.entity.Coupon;
import org.couponmanagement.entity.CouponUser;
import org.couponmanagement.grpc.client.GrpcClientFactory;
import org.couponmanagement.grpc.validation.RequestValidator;
import org.couponmanagement.repository.CouponRepository;
import org.couponmanagement.repository.CouponUserRepository;
import org.couponmanagement.rule.RuleServiceGrpc;
import org.couponmanagement.rule.RuleServiceProto;
import org.springframework.cglib.core.Local;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class CouponService {

    private final CouponUserRepository couponUserRepository;
    private final CouponRepository couponRepository;
    private final RequestValidator validator;
    private final CouponCacheService couponCacheService;
    private final Executor couponEvaluationExecutor;
    private final GrpcClientFactory grpcClientFactory;

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

            int totalCount = userCouponClaimInfos.size();
            List<UserCouponClaimInfo> paginatedCouponClaimInfos =
                    applyPagination(userCouponClaimInfos, page, size);

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
        List<CouponUser> missingCouponUsers = couponUserRepository.findByUserId(userId).stream()
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
        List<CouponUser> allCouponUsers = couponUserRepository.findByUserId(userId);

        Map<Integer, UserCouponClaimInfo> userCouponInfoMap = allCouponUsers.stream()
                .collect(Collectors.toMap(
                        CouponUser::getCouponId,
                        cu -> new UserCouponClaimInfo(
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
    ) {}

    @Observed(name = "apply-coupon-manual", contextualName = "manual-coupon-application")
    public CouponApplicationResult applyCouponManual(Integer userId, String couponCode, BigDecimal orderAmount,
                                                     LocalDateTime orderDate) {
        log.info("Applying coupon manually: userId={}, couponCode={}, orderAmount={}",
                userId, couponCode, orderAmount);

        try {
            validator.validateUserId(userId);
            validator.validateCouponCode(couponCode);
            validator.validateOrderAmount(orderAmount.doubleValue());

            Optional<Coupon> coupon = Optional.empty();
            Optional<Integer> couponIdOpt = couponCacheService.getCouponIdByCode(couponCode);
            Optional<CouponUser> couponUser = Optional.empty();
            Optional<CouponDetail> couponDetail = Optional.empty();
            Integer couponId = null;
            if (couponIdOpt.isPresent()) {
                couponId = couponIdOpt.get();
            } else {
                coupon = couponRepository.findByCodeIgnoreCase(couponCode);
                if (coupon.isEmpty()) {
                    return CouponApplicationResult.buildResult(
                            null,
                            BigDecimal.valueOf(0),
                            "Coupon not found",
                            false
                    );
                }
                couponId = coupon.get().getId();
                couponCacheService.cacheCouponCodeMapping(couponCode, couponId);

            }

            Optional<UserCouponIds> cachedUserCoupons = couponCacheService.getCachedUserCouponIds(userId);
            boolean userHasCoupon = false;
            if (cachedUserCoupons.isPresent()) {
                UserCouponClaimInfo userCouponClaimInfo = cachedUserCoupons.get().getCouponClaimInfo(couponId);
                if (!userHasCoupon) {
                    return CouponApplicationResult.buildResult(
                            null,
                            BigDecimal.valueOf(0),
                            "Coupon not found for user",
                            false
                    );
                }
                couponDetail = couponCacheService.getCachedCouponDetail(couponId);
                if (couponDetail.isEmpty()){
                    coupon = couponRepository.findById(couponId);
                    if (coupon.isEmpty()){
                        return CouponApplicationResult.buildResult(
                                null,
                                BigDecimal.valueOf(0),
                                "Coupon not found for user",
                                false
                        );
                    }
                    couponDetail = Optional.ofNullable(CouponDetail.fromCoupon(coupon.get()));
                    couponUser = Optional.ofNullable(CouponUser.buildFromDetailAndClaimInfo(couponDetail.get(), userCouponClaimInfo));
                }
            } else {
                couponUser = couponUserRepository.findByUserIdAndCouponCode(userId, couponCode);
                if (couponUser.isEmpty()){
                    return CouponApplicationResult.buildResult(
                            null,
                            BigDecimal.valueOf(0),
                            "Coupon not found for user",
                            false
                    );
                }
                coupon = Optional.ofNullable(couponUser.get().getCoupon());
                couponDetail = Optional.ofNullable(CouponDetail.fromCoupon(coupon.get()));
            }
            BigDecimal discountAmount = coupon.get().calculateDiscount(orderAmount);

            if (!couponUser.get().isUsable(orderDate)) {
                return CouponApplicationResult.buildResult(
                        couponUser.get(),
                        discountAmount,
                        "Coupon is expired or has been used before",
                        false
                );
            }

            if (discountAmount.compareTo(BigDecimal.ZERO) <= 0) {
                return CouponApplicationResult.failure("No discount applicable for this order amount");
            }

            String requestID = String.valueOf(UUID.randomUUID());
            Channel channel = grpcClientFactory.getRuleServiceChannel();
            RuleServiceGrpc.RuleServiceBlockingStub stub = RuleServiceGrpc.newBlockingStub(channel);

            var grpcRequest = RuleServiceProto.EvaluateRuleRequest.newBuilder()
                    .setRequestId(requestID)
                    .setUserId(userId)
                    .setOrderAmount(orderAmount.doubleValue())
                    .setDiscountAmount(discountAmount.doubleValue())
                    .setOrderDate(orderDate.toString())
                    .addAllRuleCollectionIds(List.of(couponDetail.get().getCollectionKeyId()))
                    .build();

            var grpcResponse = stub.evaluateRuleCollections(grpcRequest);

            if (grpcResponse.getStatus().getCode() != RuleServiceProto.StatusCode.OK) {
                log.warn("Rule service call failed: userId={}, couponCode={}, status={}, message={}",
                        userId, couponCode, grpcResponse.getStatus().getCode(), grpcResponse.getStatus().getMessage());
                return CouponApplicationResult.buildResult(
                        couponUser.get(),
                        discountAmount,
                        "Rule evaluation failed: " + grpcResponse.getStatus().getMessage(),
                        false
                );
            }

            var payload = grpcResponse.getPayload();
            boolean allRulesPassed = true;
            StringBuilder failureReason = new StringBuilder();
            for (var ruleResult : payload.getRuleCollectionResultsList()) {
                if (!ruleResult.getIsSuccess()) {
                    allRulesPassed = false;
                    if (!failureReason.isEmpty()) {
                        failureReason.append(", ");
                    }
                    failureReason.append("Collection ")
                               .append(ruleResult.getRuleCollectionId())
                               .append(": ")
                               .append(ruleResult.getErrorMessage());
                }
            }
            if (!allRulesPassed) {
                log.warn("Rule validation failed for coupon application: userId={}, couponCode={}, reasons={}",
                        userId, couponCode, failureReason.toString());
                return CouponApplicationResult.buildResult(
                        couponUser.get(),
                        discountAmount,
                        "Rule validation failed: " + failureReason.toString(),
                        false
                );
            }

            log.info("All business rules passed for coupon application: userId={}, couponCode={}", userId, couponCode);
            couponUser.get().markAsUsed();
            couponUserRepository.updateStatusUsedByUserIdAndCouponId(userId, couponUser.get().getCouponId());
            invalidateUserCouponCaches(userId, couponUser.get().getCouponId());
            log.info("Manual coupon applied successfully: couponId={}, discount={}",
                    coupon.get().getId(), discountAmount);
            return CouponApplicationResult.buildResult(
                    couponUser.get(),
                    discountAmount,
                    null,
                    true
            );

        } catch (Exception e) {
            log.error("Error applying coupon manually: userId={}, couponCode={}, error={}",
                     userId, couponCode, e.getMessage(), e);
            return CouponApplicationResult.failure("Internal error: " + e.getMessage());
        }
    }

    @Observed(name = "apply-coupon-auto", contextualName = "auto-coupon-application")
    public CouponApplicationResult applyCouponAuto(Integer userId, BigDecimal orderAmount, LocalDateTime orderDate) {
        log.info("Auto-applying best coupon: userId={}, orderAmount={}", userId, orderAmount);

        try {
            validateCouponAutoInput(userId, orderAmount);

            List<CouponUser> availableCoupons = getAvailableCouponsFromCacheOrDB(userId);
            if (availableCoupons.isEmpty()) {
                return CouponApplicationResult.failure("No available coupons for user");
            }

            BestCouponResult bestCouponResult = findBestCoupon(availableCoupons, orderAmount, orderDate);
            if (!bestCouponResult.isFound()) {
                return CouponApplicationResult.failure(bestCouponResult.getErrorMessage());
            }
            RuleEvaluationResult ruleEvaluation = evaluateRulesForCoupon(
                    bestCouponResult.couponUser(),
                    userId,
                    orderAmount,
                    bestCouponResult.getDiscountAmount(),
                    orderDate);

            if (!ruleEvaluation.success()) {
                return CouponApplicationResult.buildResult(
                        bestCouponResult.couponUser(),
                        bestCouponResult.getDiscountAmount(),
                        ruleEvaluation.errorMessage(),
                        false);
            }

            return applyCouponAndFinalize(bestCouponResult.couponUser(), bestCouponResult.getDiscountAmount(), userId);

        } catch (Exception e) {
            log.error("Error auto-applying coupon: userId={}, error={}", userId, e.getMessage(), e);
            return CouponApplicationResult.failure("Internal error: " + e.getMessage());
        }
    }

    private void validateCouponAutoInput(Integer userId, BigDecimal orderAmount) {
        validator.validateUserId(userId);
        validator.validateOrderAmount(orderAmount.doubleValue());
    }

    private BestCouponResult findBestCoupon(List<CouponUser> availableCoupons, BigDecimal orderAmount, LocalDateTime orderDate) {
        CouponUser bestCoupon = null;
        BigDecimal bestDiscount = BigDecimal.ZERO;
        String lastErrorMessage = null;

        Optional<CouponUser> bestCouponOpt = availableCoupons.parallelStream()
                .filter(couponUser -> {
                    Coupon coupon = couponUser.getCoupon();
                    return coupon != null && !isCouponExpired(couponUser, coupon, orderDate);
                })
                .map(couponUser -> {
                    Coupon coupon = couponUser.getCoupon();
                    BigDecimal discountAmount = coupon.calculateDiscount(orderAmount);
                    return new CouponDiscountPair(couponUser, discountAmount);
                })
                .filter(pair -> pair.discountAmount().compareTo(BigDecimal.ZERO) > 0)
                .max(Comparator.comparing(CouponDiscountPair::discountAmount))
                .map(CouponDiscountPair::couponUser);

        if (bestCouponOpt.isPresent()) {
            bestCoupon = bestCouponOpt.get();
            bestDiscount = bestCoupon.getCoupon().calculateDiscount(orderAmount);
        } else {
            lastErrorMessage = "No applicable discount for order amount";
        }

        return new BestCouponResult(bestCoupon, bestDiscount, lastErrorMessage);
    }

    private record CouponDiscountPair(CouponUser couponUser, BigDecimal discountAmount) {
    }

    private boolean isCouponExpired(CouponUser couponUser, Coupon coupon, LocalDateTime orderDate) {
        return (couponUser.getExpiryDate() != null && couponUser.getExpiryDate().isBefore(orderDate)) ||
               (coupon.getExpiryDate() != null && coupon.getExpiryDate().isBefore(orderDate));
    }

    private RuleEvaluationResult evaluateRulesForCoupon(CouponUser couponUser, Integer userId,
                                                       BigDecimal orderAmount, BigDecimal discountAmount,
                                                       LocalDateTime orderDate) {
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
                    .setDiscountAmount(discountAmount.doubleValue())
                    .setOrderDate(orderDate.toString())
                    .addAllRuleCollectionIds(List.of(cachedCouponDetail.get().getCollectionKeyId()))
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
                failureReason.append("Collection ")
                           .append(ruleResult.getRuleCollectionId())
                           .append(": ")
                           .append(ruleResult.getErrorMessage());
            }
        }

        if (!allRulesPassed) {
            log.warn("Rule validation failed for auto coupon application: userId={}, couponCode={}, reasons={}",
                    userId, couponCode, failureReason.toString());
            return new RuleEvaluationResult(false, "Rule validation failed: " + failureReason.toString());
        }

        log.info("All business rules passed for auto coupon application: userId={}, couponCode={}", userId, couponCode);
        return new RuleEvaluationResult(true, null);
    }

    private CouponApplicationResult applyCouponAndFinalize(CouponUser couponUser, BigDecimal discountAmount, Integer userId) {
        // Mark coupon as used and save
        couponUser.markAsUsed();
        couponUserRepository.save(couponUser);

        // Invalidate caches
        invalidateUserCouponCaches(userId, couponUser.getCouponId());

        log.info("Auto coupon applied successfully: couponId={}, discount={}",
                couponUser.getCouponId(), discountAmount);

        return CouponApplicationResult.buildResult(couponUser, discountAmount, null, true);
    }

    private record BestCouponResult(CouponUser couponUser, BigDecimal discountAmount, String errorMessage) {
        public boolean isFound() {
            return couponUser != null;
        }

        public BigDecimal getDiscountAmount() {
            return discountAmount != null ? discountAmount : BigDecimal.ZERO;
        }

        public String getErrorMessage() {
            return errorMessage != null ? errorMessage : "No applicable coupon found";
        }
    }

    private record RuleEvaluationResult(boolean success, String errorMessage) {
    }

    @Observed(name = "apply-coupon-auto-parallel", contextualName = "parallel-auto-coupon-application")
    public CompletableFuture<CouponApplicationResult> applyCouponAutoParallel(Integer userId, BigDecimal orderAmount, LocalDateTime orderDate) {
        log.info("Auto-applying best coupon (parallel): userId={}, orderAmount={}", userId, orderAmount);

        return CompletableFuture.supplyAsync(() -> {
            try {
                validator.validateUserId(userId);
                validator.validateOrderAmount(orderAmount.doubleValue());

                // Use enhanced cache logic with parallel processing
                List<CouponUser> availableCoupons = new ArrayList<>();

                Optional<UserCouponIds> cachedUserCoupons = couponCacheService.getCachedUserCouponIds(userId);
                if (cachedUserCoupons.isPresent()) {
                    log.debug("Cache hit for user coupons: userId={}", userId);

                    // OPTIMIZED: Parallel processing for cache-based coupon building
                    List<UserCouponClaimInfo> userCouponClaimInfos = cachedUserCoupons.get().getCouponDetails();

                    List<CouponUser> cachedCoupons = userCouponClaimInfos.parallelStream()
                            .map(claimInfo -> buildCouponUserFromCache(claimInfo))
                            .filter(Optional::isPresent)
                            .map(Optional::get)
                            .filter(couponUser -> couponUser.isUsable(LocalDateTime.now()))
                            .toList();

                    availableCoupons.addAll(cachedCoupons);

                } else {
                    log.debug("Cache miss for user coupons: userId={}", userId);
                    // Fallback to optimized DB query with parallel caching
                    availableCoupons = loadAndCacheAvailableCoupons(userId);
                }

                if (availableCoupons.isEmpty()) {
                    return CouponApplicationResult.failure("No available coupons for user");
                }

                List<CouponUser> validCoupons = availableCoupons.parallelStream()
                        .filter(couponUser -> {
                            Coupon coupon = couponUser.getCoupon();
                            if (coupon == null) return false;

                            return !((couponUser.getExpiryDate() != null && couponUser.getExpiryDate().isBefore(orderDate)) ||
                                    (coupon.getExpiryDate() != null && coupon.getExpiryDate().isBefore(orderDate)));
                        })
                        .toList();

                if (validCoupons.isEmpty()) {
                    return CouponApplicationResult.failure("No valid coupons available for user");
                }

                List<CompletableFuture<CouponWithRuleEvaluationResult>> evaluationFutures = validCoupons.parallelStream()
                        .map(couponUser -> evaluateCouponWithRulesAsync(couponUser, orderAmount, userId, orderDate))
                        .toList();

                CompletableFuture<Void> allEvaluations = CompletableFuture.allOf(
                        evaluationFutures.toArray(new CompletableFuture[0])
                );

                CouponWithRuleEvaluationResult bestResult = allEvaluations.thenApply(v -> {
                    return evaluationFutures.stream()
                            .map(CompletableFuture::join)
                            .filter(result -> result.isValid() && result.discountAmount().compareTo(BigDecimal.ZERO) > 0)
                            .max(Comparator.comparing(CouponWithRuleEvaluationResult::discountAmount))
                            .orElse(null);
                }).join();

                if (bestResult != null) {
                    CouponUser bestCoupon = bestResult.couponUser();
                    BigDecimal bestDiscount = bestResult.discountAmount();

                    couponUserRepository.updateStatusUsedByUserIdAndCouponId(bestCoupon.getUserId(), bestCoupon.getCouponId());

                    invalidateUserCouponCaches(userId, bestCoupon.getCouponId());
                    log.info("Parallel auto coupon applied successfully: couponId={}, discount={}",
                            bestCoupon.getCouponId(), bestDiscount);
                    return CouponApplicationResult.buildResult(
                            bestCoupon,
                            bestDiscount,
                            null,
                            true
                    );
                } else {
                    return CouponApplicationResult.failure("No applicable coupon found after parallel rule evaluation");
                }

            } catch (Exception e) {
                log.error("Error in parallel auto-applying coupon: userId={}, error={}", userId, e.getMessage(), e);
                return CouponApplicationResult.failure("Internal error: " + e.getMessage());
            }
        }, couponEvaluationExecutor);
    }

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

            return evaluateRulesAsync(couponUser, userId, orderAmount, discountResult.discountAmount(), orderDate)
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
            BigDecimal discountAmount, LocalDateTime orderDate) {

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
                        .setDiscountAmount(discountAmount.doubleValue())
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
    ) {}

    @Observed(name = "apply-coupon-auto-parallel-sync", contextualName = "parallel-auto-coupon-application-sync")
    public CouponApplicationResult applyCouponAutoParallelSync(Integer userId, BigDecimal orderAmount, LocalDateTime orderDate) {
        try {
            return applyCouponAutoParallel(userId, orderAmount, orderDate).get();
        } catch (Exception e) {
            log.error("Error in parallel sync auto-applying coupon: userId={}, error={}", userId, e.getMessage(), e);
            return CouponApplicationResult.failure("Internal error: " + e.getMessage());
        }
    }


    private void invalidateUserCouponCaches(Integer userId, Integer couponId) {
        couponCacheService.invalidateUserCache(userId, couponId);
        log.debug("Invalidated caches for userId={}, couponId={}", userId, couponId);
    }

    @Observed(name = "list-coupons", contextualName = "coupons-listing")
    public CouponsListResult listCoupons(int page, int size, String status) {
        log.info("Listing coupons: page={}, size={}, status={}", page, size, status);

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
                    size,
                    status
            );

            log.info("Coupons listed from database: totalCount={}, page={}, size={}",
                    couponPage.getTotalElements(), page, size);

            return result;

        } catch (Exception e) {
            log.error("Error listing coupons: page={}, size={}, status={}, error={}",
                     page, size, status, e.getMessage(), e);
            throw new RuntimeException("Failed to list coupons: " + e.getMessage(), e);
        }
    }

    public record CouponsListResult(
            List<CouponDetail> couponDetails,
            long totalCount,
            int page,
            int size,
            String status
    ) {}

    private List<CouponUser> getAvailableCouponsFromCacheOrDB(Integer userId) {
        List<CouponUser> availableCoupons = new ArrayList<>();

        Optional<UserCouponIds> cachedUserCoupons = couponCacheService.getCachedUserCouponIds(userId);
        if (cachedUserCoupons.isPresent()) {
            log.debug("Cache hit for user coupons: userId={}", userId);

            List<UserCouponClaimInfo> userCouponClaimInfos = cachedUserCoupons.get().getCouponDetails();

            for (UserCouponClaimInfo claimInfo : userCouponClaimInfos) {
                Optional<CouponDetail> couponDetail = couponCacheService.getCachedCouponDetail(claimInfo.getCouponId());

                if (couponDetail.isPresent()) {
                    CouponUser couponUser = CouponUser.buildFromDetailAndClaimInfo(couponDetail.get(), claimInfo);
                    if (couponUser.isUsable(LocalDateTime.now())) {
                        availableCoupons.add(couponUser);
                    }
                } else {
                    Optional<Coupon> coupon = couponRepository.findById(claimInfo.getCouponId());
                    if (coupon.isPresent()) {
                        CouponDetail detail = CouponDetail.fromCoupon(coupon.get());
                        couponCacheService.cacheCouponDetail(claimInfo.getCouponId(), detail);

                        CouponUser couponUser = CouponUser.buildFromDetailAndClaimInfo(detail, claimInfo);
                        if (couponUser.isUsable(LocalDateTime.now())) {
                            availableCoupons.add(couponUser);
                        }
                    }
                }
            }
        } else {
            log.debug("Cache miss for user coupons: userId={}", userId);
            availableCoupons = couponUserRepository.findAvailableCouponsByUserId(userId);

            if (!availableCoupons.isEmpty()) {
                Map<Integer, UserCouponClaimInfo> userCouponInfoMap = availableCoupons.stream()
                        .collect(Collectors.toMap(
                                CouponUser::getCouponId,
                                cu -> new UserCouponClaimInfo(
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
        }

        return availableCoupons;
    }


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
    ) {}

    private Optional<CouponUser> buildCouponUserFromCache(UserCouponClaimInfo claimInfo) {
        Optional<CouponDetail> couponDetail = couponCacheService.getCachedCouponDetail(claimInfo.getCouponId());

        if (couponDetail.isPresent()) {
            return Optional.of(CouponUser.buildFromDetailAndClaimInfo(couponDetail.get(), claimInfo));
        } else {
            return Optional.empty();
        }
    }

    private List<CouponUser> loadAndCacheAvailableCoupons(Integer userId) {
        List<CouponUser> availableCoupons = couponUserRepository.findAvailableCouponsByUserId(userId);

        if (!availableCoupons.isEmpty()) {
            // Cache the results for next time
            Map<Integer, UserCouponClaimInfo> userCouponInfoMap = availableCoupons.stream()
                    .collect(Collectors.toMap(
                            CouponUser::getCouponId,
                            cu -> new UserCouponClaimInfo(
                                    cu.getUserId(),
                                    cu.getCouponId(),
                                    cu.getCreatedAt(),
                                    cu.getExpiryDate()
                            )
                    ));

            UserCouponIds userCouponIdsCache = UserCouponIds.of(userCouponInfoMap);
            couponCacheService.cacheUserCouponIds(userId, userCouponIdsCache);

            // Cache coupon details as well
            availableCoupons.parallelStream().forEach(couponUser -> {
                if (couponUser.getCoupon() != null) {
                    CouponDetail couponDetail = CouponDetail.fromCoupon(couponUser.getCoupon());
                    couponCacheService.cacheCouponDetail(couponUser.getCouponId(), couponDetail);
                }
            });
        }

        return availableCoupons;
    }
}
