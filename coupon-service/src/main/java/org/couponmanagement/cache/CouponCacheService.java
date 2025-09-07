package org.couponmanagement.cache;

import io.micrometer.observation.annotation.Observed;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.couponmanagement.dto.CouponDetail;
import org.couponmanagement.dto.UserCouponIds;
import org.couponmanagement.grpc.annotation.PerformanceMonitor;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class CouponCacheService {

    private final RedisCacheService cacheService;
    private final CouponCacheProperties cacheProperties;
    private final RedissonClient redissonClient;


    @PerformanceMonitor()
    public void cacheUserCouponIds(Integer userId, UserCouponIds userCouponIds) {
        String key = cacheProperties.getUserCouponIdsKey(userId);
        cacheService.put(key, userCouponIds, cacheProperties.getUserCouponsTtlSeconds());
        log.debug("Cached user coupon IDs: userId={}, count={}, ttl={}s",
                userId, userCouponIds.getUserCouponInfo().size(), cacheProperties.getUserCouponsTtlSeconds());
    }

    @PerformanceMonitor
    @Observed(name = "getCachedUserCouponIds", contextualName = "CouponCacheService.getCachedUserCouponIds")
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

    @PerformanceMonitor
    @Observed(name = "invalidateUserCache", contextualName = "CouponCacheService.invalidateUserCache")
    public boolean invalidateUserCache(Integer userId, Integer couponId){
        try {
            Optional<UserCouponIds> result = getCachedUserCouponIds(userId);
            if (result.isPresent()){
                result.get().removeUserClaimInfo(couponId);
                cacheUserCouponIds(userId, result.get());
                return true;
            }
        } catch (Exception e) {
            log.error("Error invalidating user cache: userId={}, couponId={}", userId, couponId, e);
            return false;
        }
        return false;
    }

    @PerformanceMonitor()
    public void cacheCouponDetail(Integer couponId, CouponDetail couponDetail) {
        String key = cacheProperties.getCouponDetailKey(couponId);
        cacheService.put(key, couponDetail, cacheProperties.getCouponDetailTtlSeconds());
        log.debug("Cached coupon detail: couponId={}, code={}, ttl={}s",
                couponId, couponDetail.getCouponCode(), cacheProperties.getCouponDetailTtlSeconds());
    }

    @PerformanceMonitor
    @Observed(name = "getCachedCouponDetail", contextualName = "CouponCacheService.getCachedCouponDetail")
    public Optional<CouponDetail> getCachedCouponDetail(Integer couponId) {
        String key = cacheProperties.getCouponDetailKey(couponId);

        return cacheService.get(key, CouponDetail.class);
    }

    public Map<Integer, CouponDetail> getCachedCouponDetailsBatch(List<Integer> couponIds) {
        if (couponIds.isEmpty()) {
            return new HashMap<>();
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


    @PerformanceMonitor
    public void cacheCouponCodeMapping(String couponCode, Integer couponId) {
        String key = cacheProperties.getCouponInfoKey(couponCode);
        cacheService.put(key, couponId, cacheProperties.getCouponInfoTtlSeconds());
        log.debug("Cached coupon code mapping: couponCode={}, couponId={}, ttl={}s", couponCode, couponId, cacheProperties.getCouponInfoTtlSeconds());
    }

    @PerformanceMonitor
    @Observed(name = "getCouponIdByCode", contextualName = "CouponCacheService.getCouponIdByCode")
    public Optional<Integer> getCouponIdByCode(String couponCode) {
        String key = cacheProperties.getCouponInfoKey(couponCode);
        return cacheService.get(key, Integer.class);
    }
}
