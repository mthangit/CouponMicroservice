package org.couponmanagement.cache;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

@Data
@Component
@ConfigurationProperties(prefix = "app.cache")
@Validated
public class CouponCacheProperties{

    @NotBlank
    private String keyPrefix = "coupon-service";

    @Min(1)
    private long defaultTtlSeconds = 172800;

    @Min(1)
    private long couponInfoTtlSeconds = 172800;

    @Min(1)
    private long userCouponsTtlSeconds = 172800;

    @Min(1)
    private long couponDetailedTtlSeconds = 172800;

    @Min(1)
    private long couponDetailTtlSeconds = 60 * 60 * 24 * 7; // 1 day

    public String getCouponInfoKey(String couponCode) {
        return "coupon:info:" + couponCode;
    }

    public String getUserCouponsKey(Integer userId) {
        return keyPrefix + ":user-coupons:" + userId;
    }

    public String getUserCouponsBasicKey(Integer userId) {
        return keyPrefix + ":user-coupons-basic:" + userId;
    }

    public String getCouponDetailedKey(String couponCode) {
        return keyPrefix + ":coupon-detailed:" + couponCode;
    }

    public String getUserCouponIdsKey(Integer userId) {
        return keyPrefix + ":user-coupon-ids:" + userId;
    }

    public String getCouponDetailKey(Integer couponId) {
        return keyPrefix + ":coupon-detail:" + couponId;
    }
}
