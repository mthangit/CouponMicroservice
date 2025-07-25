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
public class CouponEvaluationResult {
    private CouponUser couponUser;
    private BigDecimal discountAmount;
    private boolean isValid;
    private String errorMessage;
    private long evaluationTimeMs;

    public static CouponEvaluationResult success(CouponUser couponUser, BigDecimal discountAmount, long evaluationTime) {
        return CouponEvaluationResult.builder()
                .couponUser(couponUser)
                .discountAmount(discountAmount)
                .isValid(true)
                .evaluationTimeMs(evaluationTime)
                .build();
    }

    public static CouponEvaluationResult failure(CouponUser couponUser, String errorMessage, long evaluationTime) {
        return CouponEvaluationResult.builder()
                .couponUser(couponUser)
                .discountAmount(BigDecimal.ZERO)
                .isValid(false)
                .errorMessage(errorMessage)
                .evaluationTimeMs(evaluationTime)
                .build();
    }
}
