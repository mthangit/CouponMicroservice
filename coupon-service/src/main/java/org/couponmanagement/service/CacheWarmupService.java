package org.couponmanagement.service;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.couponmanagement.cache.CouponCacheService;
import org.couponmanagement.dto.CouponDetail;
import org.couponmanagement.dto.UserCouponClaimInfo;
import org.couponmanagement.dto.UserCouponIds;
import org.couponmanagement.entity.Coupon;
import org.couponmanagement.entity.CouponUser;
import org.couponmanagement.repository.CouponRepository;
import org.couponmanagement.repository.CouponUserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@Slf4j
@Transactional(readOnly = true)
public class CacheWarmupService {

    private final CouponCacheService couponCacheService;
    private final CouponRepository couponRepository;
    private final CouponUserRepository couponUserRepository;

    public CacheWarmupService(
            CouponCacheService couponCacheService,
            CouponRepository couponRepository,
            CouponUserRepository couponUserRepository) {
        this.couponCacheService = couponCacheService;
        this.couponRepository = couponRepository;
        this.couponUserRepository = couponUserRepository;
    }

    public WarmupResult warmupActiveCoupons() {
        log.info("Starting warm up for active coupons");
        LocalDateTime now = LocalDateTime.now();

        try {
            List<Coupon> activeCoupons = couponRepository.findActiveCoupons(now);
            int successCount = 0;
            int failureCount = 0;

            for (Coupon coupon : activeCoupons) {
                try {
                    CouponDetail couponDetail = CouponDetail.fromCoupon(coupon);
                    couponCacheService.cacheCouponDetail(coupon.getId(), couponDetail);

                    // Cache coupon code mapping
                    couponCacheService.cacheCouponCodeMapping(coupon.getCode(), coupon.getId());

                    successCount++;
                } catch (Exception e) {
                    log.error("Failed to cache coupon: couponId={}, error={}", coupon.getId(), e.getMessage());
                    failureCount++;
                }
            }

            log.info("Completed warm up for active coupons: success={}, failure={}", successCount, failureCount);
            return WarmupResult.builder()
                .type("ACTIVE_COUPONS")
                .successCount(successCount)
                .failureCount(failureCount)
                .totalProcessed(activeCoupons.size())
                .build();

        } catch (Exception e) {
            log.error("Error during active coupons warm up", e);
            return WarmupResult.builder()
                .type("ACTIVE_COUPONS")
                .successCount(0)
                .failureCount(1)
                .totalProcessed(0)
                .error(e.getMessage())
                .build();
        }
    }

    public WarmupResult warmupUserCoupons(List<Integer> userIds) {
        log.info("Starting warm up for user coupons: userCount={}", userIds.size());

        int successCount = 0;
        int failureCount = 0;

        for (Integer userId : userIds) {
            try {
                List<CouponUser> userCoupons = couponUserRepository.findByUserId(userId);

                if (!userCoupons.isEmpty()) {
                    UserCouponIds userCouponIds = convertToUserCouponIds(userCoupons);
                    couponCacheService.cacheUserCouponIds(userId, userCouponIds);
                    successCount++;
                }
            } catch (Exception e) {
                log.error("Failed to cache user coupons: userId={}, error={}", userId, e.getMessage());
                failureCount++;
            }
        }

        log.info("Completed warm up for user coupons: success={}, failure={}", successCount, failureCount);
        return WarmupResult.builder()
            .type("USER_COUPONS")
            .successCount(successCount)
            .failureCount(failureCount)
            .totalProcessed(userIds.size())
            .build();
    }

    /**
     * Warm up specific coupon details
     */
    public WarmupResult warmupCouponDetails(List<Integer> couponIds) {
        log.info("Starting warm up for coupon details: couponCount={}", couponIds.size());

        int successCount = 0;
        int failureCount = 0;

        for (Integer couponId : couponIds) {
            try {
                couponRepository.findById(couponId).ifPresent(coupon -> {
                    CouponDetail couponDetail = convertToCouponDetail(coupon);
                    couponCacheService.cacheCouponDetail(couponId, couponDetail);
                    couponCacheService.cacheCouponCodeMapping(coupon.getCode(), coupon.getId());
                });
                successCount++;
            } catch (Exception e) {
                log.error("Failed to cache coupon detail: couponId={}, error={}", couponId, e.getMessage());
                failureCount++;
            }
        }

        log.info("Completed warm up for coupon details: success={}, failure={}", successCount, failureCount);
        return WarmupResult.builder()
            .type("COUPON_DETAILS")
            .successCount(successCount)
            .failureCount(failureCount)
            .totalProcessed(couponIds.size())
            .build();
    }

    public Map<String, WarmupResult> warmupAllCache() {
        log.info("Starting full cache warm up");
        List<Integer> activeUserIds = getActiveUserIds();

        WarmupResult activeCouponsWarmup = warmupActiveCoupons();
        WarmupResult userCouponsWarmup = warmupUserCoupons(activeUserIds);

        Map<String, WarmupResult> results = new HashMap<>();
        results.put("activeCoupons", activeCouponsWarmup);
        results.put("userCoupons", userCouponsWarmup);

        log.info("Full cache warm up completed");
        return results;
    }

    private CouponDetail convertToCouponDetail(Coupon coupon) {
        return CouponDetail.builder()
            .couponId(coupon.getId())
            .couponCode(coupon.getCode())
            .title(coupon.getTitle())
            .description(coupon.getDescription())
            .type(coupon.getType())
            .isActive(coupon.getIsActive())
            .expiryDate(coupon.getExpiryDate())
            .collectionKeyId(coupon.getCollectionKeyId())
            .build();
    }

    private UserCouponIds convertToUserCouponIds(List<CouponUser> userCoupons) {
        Map<Integer, UserCouponClaimInfo> claimInfos = userCoupons.stream().collect(
                Collectors.toMap(
                        CouponUser::getCouponId,
                        this::convertToUserCouponClaimInfo
                )
        );

        return UserCouponIds.builder()
            .userCouponInfo(claimInfos)
            .cacheTimestamp(LocalDateTime.now())
            .totalCount(claimInfos.size())
            .build();
    }

    private UserCouponClaimInfo convertToUserCouponClaimInfo(CouponUser couponUser) {
        return UserCouponClaimInfo.builder()
                .couponUserId(couponUser.getId())
            .couponId(couponUser.getCouponId())
            .userId(couponUser.getUserId())
            .claimedDate(couponUser.getClaimedAt())
            .expiryDate(couponUser.getExpiryDate())
            .build();
    }

    private List<Integer> getActiveUserIds() {
        return couponUserRepository.findAll().stream()
            .map(CouponUser::getUserId)
            .distinct()
            .collect(Collectors.toList());
    }

    @Getter
    public static class WarmupResult {
        // Getters
        private String type;
        private int successCount;
        private int failureCount;
        private int totalProcessed;
        private String error;

        // Lombok builder
        public static WarmupResultBuilder builder() {
            return new WarmupResultBuilder();
        }

        public static class WarmupResultBuilder {
            private String type;
            private int successCount;
            private int failureCount;
            private int totalProcessed;
            private String error;

            public WarmupResultBuilder type(String type) {
                this.type = type;
                return this;
            }

            public WarmupResultBuilder successCount(int successCount) {
                this.successCount = successCount;
                return this;
            }

            public WarmupResultBuilder failureCount(int failureCount) {
                this.failureCount = failureCount;
                return this;
            }

            public WarmupResultBuilder totalProcessed(int totalProcessed) {
                this.totalProcessed = totalProcessed;
                return this;
            }

            public WarmupResultBuilder error(String error) {
                this.error = error;
                return this;
            }

            public WarmupResult build() {
                WarmupResult result = new WarmupResult();
                result.type = this.type;
                result.successCount = this.successCount;
                result.failureCount = this.failureCount;
                result.totalProcessed = this.totalProcessed;
                result.error = this.error;
                return result;
            }
        }
    }
}
