package org.couponmanagement.service;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.couponmanagement.entity.CouponUser;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CouponApplicationResult {
    private boolean success;
    private Integer couponId;
    private String couponCode;
    private BigDecimal discountAmount;
    private String errorMessage;

    public static CouponApplicationResult success(Integer couponId, String couponCode, BigDecimal discountAmount) {
        return CouponApplicationResult.builder()
                .success(true)
                .couponId(couponId)
                .couponCode(couponCode)
                .discountAmount(discountAmount)
                .build();
    }

    public static CouponApplicationResult failure(String errorMessage) {
        return CouponApplicationResult.builder()
                .success(false)
                .discountAmount(BigDecimal.ZERO)
                .errorMessage(errorMessage)
                .build();
    }

    public static CouponApplicationResult buildResult(CouponUser couponUser, BigDecimal discountAmount,
                                                      String errorMessage, boolean success) {
        if (success && couponUser != null) {
            return CouponApplicationResult.builder()
                    .success(true)
                    .couponId(couponUser.getCouponId())
                    .couponCode(couponUser.getCoupon() != null ? couponUser.getCoupon().getCode() : null)
                    .discountAmount(discountAmount)
                    .build();
        } else {
            return CouponApplicationResult.builder()
                    .success(false)
                    .couponId(couponUser != null ? couponUser.getCouponId() : null)
                    .couponCode(couponUser != null && couponUser.getCoupon() != null ?
                               couponUser.getCoupon().getCode() : null)
                    .discountAmount(discountAmount != null ? discountAmount : BigDecimal.ZERO)
                    .errorMessage(errorMessage)
                    .build();
        }
    }
}
