package org.couponmanagement.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.couponmanagement.entity.Coupon;

import java.time.LocalDateTime;

@Data
@Builder
@AllArgsConstructor
@RequiredArgsConstructor
public class CouponDetail {
    private Integer couponId;
    private String couponCode;
    private String title;
    private String description;
    private String type;
    private String status;
    private Integer collectionKeyId;
    private String discountConfigJson;
    private boolean isActive;
    private LocalDateTime createdAt;
    private LocalDateTime expiryDate;
    private LocalDateTime updatedAt;
    private LocalDateTime cachedAt;

    public static CouponDetail fromCoupon(Coupon coupon) {
        return CouponDetail.builder()
                .couponId(coupon.getId())
                .couponCode(coupon.getCode())
                .title(coupon.getTitle())
                .description(coupon.getDescription())
                .type(coupon.getDiscountType())
                .status(coupon.getIsActive() ? "ACTIVE" : "INACTIVE")
                .collectionKeyId(coupon.getCollectionKeyId())
                .discountConfigJson(coupon.getDiscountConfigJson())
                .isActive(coupon.getIsActive())
                .createdAt(coupon.getCreatedAt())
                .expiryDate(coupon.getExpiryDate())
                .updatedAt(coupon.getUpdatedAt())
                .cachedAt(LocalDateTime.now())
                .build();
    }
}
