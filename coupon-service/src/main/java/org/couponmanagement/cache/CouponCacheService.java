package org.couponmanagement.cache;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.couponmanagement.dto.CouponDetail;
import org.couponmanagement.dto.UserCouponIds;
import org.couponmanagement.grpc.annotation.PerformanceMonitor;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class CouponCacheService {

    private final RedisCacheService cacheService;
    private final CouponCacheProperties cacheProperties;

    @PerformanceMonitor()
    public void cacheUserCouponIds(Integer userId, UserCouponIds userCouponIds) {
        String key = cacheProperties.getUserCouponIdsKey(userId);
        cacheService.put(key, userCouponIds, cacheProperties.getUserCouponsTtlSeconds());
        log.debug("Cached user coupon IDs: userId={}, count={}, ttl={}s",
                userId, userCouponIds.getUserCouponInfo().size(), cacheProperties.getUserCouponsTtlSeconds());
    }

    @PerformanceMonitor
    public Optional<UserCouponIds> getCachedUserCouponIds(Integer userId) {
        String key = cacheProperties.getUserCouponIdsKey(userId);
        Optional<UserCouponIds> result = cacheService.get(key, UserCouponIds.class);

        if (result.isPresent()) {
            log.debug("Cache hit for user coupon IDs: userId={}", userId);
        } else {
            log.debug("Cache miss for user coupon IDs: userId={}", userId);
        }

        return result;
    }

    @PerformanceMonitor()
    public void cacheCouponDetail(Integer couponId, CouponDetail couponDetail) {
        String key = cacheProperties.getCouponDetailKey(couponId);
        cacheService.put(key, couponDetail, cacheProperties.getCouponDetailTtlSeconds());
        log.debug("Cached coupon detail: couponId={}, code={}, ttl={}s",
                couponId, couponDetail.getCouponCode(), cacheProperties.getCouponDetailTtlSeconds());
    }

    public Optional<CouponDetail> getCachedCouponDetail(Integer couponId) {
        String key = cacheProperties.getCouponDetailKey(couponId);
        Optional<CouponDetail> result = cacheService.get(key, CouponDetail.class);

        if (result.isPresent()) {
            log.debug("Cache hit for coupon detail: couponId={}", couponId);
        } else {
            log.debug("Cache miss for coupon detail: couponId={}", couponId);
        }

        return result;
    }

    public Map<Integer, CouponDetail> getCachedCouponDetailsBatch(List<Integer> couponIds) {
        if (couponIds.isEmpty()) {
            return new java.util.HashMap<>();
        }

        Map<Integer, CouponDetail> results = new HashMap<>();
        List<Integer> cacheHits = new ArrayList<>();
        List<Integer> cacheMisses = new ArrayList<>();

        for (Integer couponId : couponIds) {
            Optional<CouponDetail> cached = getCachedCouponDetail(couponId);
            if (cached.isPresent()) {
                results.put(couponId, cached.get());
                cacheHits.add(couponId);
            } else {
                cacheMisses.add(couponId);
            }
        }

        log.debug("Batch coupon detail cache: hits={}, misses={}, total={}",
                cacheHits.size(), cacheMisses.size(), couponIds.size());

        return results;
    }

    public void invalidateUserCache(Integer userId, Integer couponId){
        Optional<UserCouponIds> result = getCachedUserCouponIds(userId);
        if (result.isPresent()){
            result.get().removeUserClaimInfo(couponId);
            cacheUserCouponIds(userId, result.get());
        }
    }

    public void cacheCouponCodeMapping(String couponCode, Integer couponId) {
        String key = cacheProperties.getCouponInfoKey(couponCode);
        cacheService.put(key, couponId, cacheProperties.getCouponInfoTtlSeconds());
        log.debug("Cached coupon code mapping: couponCode={}, couponId={}, ttl={}s", couponCode, couponId, cacheProperties.getCouponInfoTtlSeconds());
    }

    public Optional<Integer> getCouponIdByCode(String couponCode) {
        String key = cacheProperties.getCouponInfoKey(couponCode);
        Optional<Integer> result = cacheService.get(key, Integer.class);
        if (result.isPresent()) {
            log.debug("Cache hit for coupon code mapping: couponCode={}, couponId={}", couponCode, result.get());
        } else {
            log.debug("Cache miss for coupon code mapping: couponCode={}", couponCode);
        }
        return result;
    }
}
