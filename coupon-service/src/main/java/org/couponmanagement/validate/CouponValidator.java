package org.couponmanagement.validate;

import com.google.protobuf.Value;
import org.couponmanagement.coupon.CouponServiceProto;

import java.time.LocalDateTime;
import java.util.Map;
import org.couponmanagement.utils.Utils;


public class CouponValidator {
    public void validateCreateCouponRequest(CouponServiceProto.CreateCouponRequest request) {
        if (request.getCode().isEmpty()) {
            throw new IllegalArgumentException("Coupon code is required");
        }
        if (request.getTitle().isEmpty()) {
            throw new IllegalArgumentException("Coupon title is required");
        }
        if (request.getConfig().getConfigMap().isEmpty()) {
            throw new IllegalArgumentException("Discount config is required");
        }
        if (request.getEndDate().isEmpty()){
            throw new IllegalArgumentException("Expire day is required");
        }
        if (request.getStartDate().isEmpty()){
            throw new IllegalArgumentException("Start day is required");
        }

        LocalDateTime startDate = Utils.parseOrderDateTime(request.getStartDate());
        LocalDateTime endDate = Utils.parseOrderDateTime(request.getEndDate());

        if (startDate.isAfter(endDate)) {
            throw new IllegalArgumentException("Start date must be before end date");
        }
        validateDiscountConfig(request);
    }

    public void validateDiscountConfig(CouponServiceProto.CreateCouponRequest request) {
        if (!request.hasConfig() || request.getConfig().getConfigMap().isEmpty()) {
            throw new IllegalArgumentException("Discount config is required");
        }
        Map<String, Value> configMap = request.getConfig().getConfigMap();
        if (!configMap.containsKey("type") ||
                configMap.get("type").getKindCase() != Value.KindCase.STRING_VALUE) {
            throw new IllegalArgumentException("Discount type is required in config");
        }
        String type = configMap.get("type").getStringValue();
        if (!(type.equalsIgnoreCase("PERCENTAGE") || type.equalsIgnoreCase("FIXED_AMOUNT"))) {
            throw new IllegalArgumentException("Discount type must be PERCENTAGE hoặc FIXED_AMOUNT");
        }
        if (!configMap.containsKey("value") ||
                configMap.get("value").getKindCase() != Value.KindCase.NUMBER_VALUE) {
            throw new IllegalArgumentException("Discount value is required and must be a number");
        }
        double value = configMap.get("value").getNumberValue();
        if (value <= 0) {
            throw new IllegalArgumentException("Discount value must be greater than 0");
        }
        if (configMap.containsKey("max_discount")) {
            Value maxDiscountValue = configMap.get("max_discount");
            if (maxDiscountValue.getKindCase() != Value.KindCase.NUMBER_VALUE) {
                throw new IllegalArgumentException("max_discount must be a number nếu có");
            }
            double maxDiscount = maxDiscountValue.getNumberValue();
            if (maxDiscount < 0) {
                throw new IllegalArgumentException("max_discount must be >= 0 nếu có");
            }
        }
    }

}
