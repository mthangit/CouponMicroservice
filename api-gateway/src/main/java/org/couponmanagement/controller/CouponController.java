package org.couponmanagement.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.observation.annotation.Observed;
import org.couponmanagement.dto.ApplyCouponRequest;
import org.couponmanagement.dto.AutoApplyCouponRequest;
import org.couponmanagement.dto.UpdateCouponRequest;
import org.couponmanagement.dto.response.*;
import org.couponmanagement.grpc.CouponServiceClient;
import org.couponmanagement.grpc.annotation.PerformanceMonitor;
import org.couponmanagement.coupon.*;
import org.couponmanagement.security.RequireAdmin;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import lombok.extern.slf4j.Slf4j;
import jakarta.validation.Valid;
import com.google.protobuf.Value;

import java.util.List;
import java.util.Map;
import java.util.HashMap;

@RestController
@RequestMapping("/api/v1/coupons")
@Slf4j
public class CouponController {

    @Autowired
    private CouponServiceClient couponServiceClient;

    @PostMapping("/apply-manual")
    @Observed(name = "apply-coupon-manual", contextualName = "manual-coupon-application")
    @PerformanceMonitor
    public ResponseEntity<?> applyCouponManual(@Valid @RequestBody ApplyCouponRequest request) {
        try {
            log.info("Apply coupon manual: user={}, coupon={}", request.getUserId(), request.getCouponCode());

            // Convert DTO to protobuf
            CouponServiceProto.ApplyCouponManualRequest.Builder protoRequestBuilder =
                CouponServiceProto.ApplyCouponManualRequest.newBuilder()
                    .setUserId(request.getUserId())
                    .setCouponCode(request.getCouponCode())
                    .setOrderAmount(request.getOrderAmount());

            if (request.getOrderDate() != null && !request.getOrderDate().trim().isEmpty()) {
                protoRequestBuilder.setOrderDate(request.getOrderDate());
            }

            CouponServiceProto.ApplyCouponManualResponse response =
                couponServiceClient.applyCouponManual(protoRequestBuilder.build());

            if (response.getStatus().getCode() == CouponServiceProto.StatusCode.OK) {
                // Convert protobuf response to DTO
                ApplyCouponResponse couponResponse = ApplyCouponResponse.builder()
                        .success(response.getPayload().getSuccess())
                        .couponCode(response.getPayload().getCouponCode())
                        .orderAmount(response.getPayload().getOrderAmount())
                        .discountAmount(response.getPayload().getDiscountAmount())
                        .finalAmount(response.getPayload().getFinalAmount())
                        .message(response.getPayload().getErrorMessage()) // Use errorMessage instead
                        .build();

                return ResponseEntity.ok(couponResponse);
            } else {
                // Convert protobuf error to DTO
                ErrorResponse errorResponse = ErrorResponse.builder()
                        .code(response.getError().getCode())
                        .message(response.getError().getMessage())
                        .details(response.getError().getDetailsMap())
                        .build();

                return ResponseEntity.badRequest().body(errorResponse);
            }
        } catch (Exception e) {
            log.error("Error in apply coupon manual: {}", e.getMessage(), e);

            ErrorResponse errorResponse = ErrorResponse.builder()
                    .code("INTERNAL_ERROR")
                    .message("Internal server error")
                    .build();

            return ResponseEntity.internalServerError().body(errorResponse);
        }
    }

    @PostMapping("/apply-auto")
    @Observed(name = "apply-coupon-auto", contextualName = "auto-coupon-application")
    @PerformanceMonitor
    public ResponseEntity<?> applyCouponAuto(@Valid @RequestBody AutoApplyCouponRequest request) {
        try {
            log.info("Apply coupon auto: user={}", request.getUserId());

            // Convert DTO to protobuf
            CouponServiceProto.ApplyCouponAutoRequest.Builder protoRequestBuilder =
                CouponServiceProto.ApplyCouponAutoRequest.newBuilder()
                    .setUserId(request.getUserId())
                    .setOrderAmount(request.getOrderAmount());

            if (request.getOrderDate() != null && !request.getOrderDate().trim().isEmpty()) {
                protoRequestBuilder.setOrderDate(request.getOrderDate());
            }

            CouponServiceProto.ApplyCouponAutoResponse response =
                couponServiceClient.applyCouponAuto(protoRequestBuilder.build());

            if (response.getStatus().getCode() == CouponServiceProto.StatusCode.OK) {
                // Convert protobuf response to DTO
                ApplyCouponResponse couponResponse = ApplyCouponResponse.builder()
                        .success(response.getPayload().getSuccess())
                        .couponCode(response.getPayload().getCouponCode())
                        .orderAmount(response.getPayload().getOrderAmount())
                        .discountAmount(response.getPayload().getDiscountAmount())
                        .finalAmount(response.getPayload().getFinalAmount())
                        .message(response.getPayload().getErrorMessage()) // Use errorMessage instead
                        .build();

                return ResponseEntity.ok(couponResponse);
            } else {
                // Convert protobuf error to DTO
                ErrorResponse errorResponse = ErrorResponse.builder()
                        .code(response.getError().getCode())
                        .message(response.getError().getMessage())
                        .details(response.getError().getDetailsMap())
                        .build();

                return ResponseEntity.badRequest().body(errorResponse);
            }
        } catch (Exception e) {
            log.error("Error in apply coupon auto: {}", e.getMessage(), e);

            ErrorResponse errorResponse = ErrorResponse.builder()
                    .code("INTERNAL_ERROR")
                    .message("Internal server error")
                    .build();

            return ResponseEntity.internalServerError().body(errorResponse);
        }
    }

    @PutMapping("/{couponId}")
    @RequireAdmin
    public ResponseEntity<?> updateCoupon(@PathVariable("couponId") int couponId, @Valid @RequestBody UpdateCouponRequest request) {
        try {
            log.info("Update coupon: id={}, code={}", couponId, request.getCode());
            ObjectMapper objectMapper = new ObjectMapper();

            CouponServiceProto.UpdateCouponRequest.Builder protoRequestBuilder =
                CouponServiceProto.UpdateCouponRequest.newBuilder()
                    .setCouponId(couponId)
                    .setCode(request.getCode());

            if (request.getTitle() != null && !request.getTitle().trim().isEmpty()) {
                protoRequestBuilder.setTitle(request.getTitle());
            }

            if (request.getDescription() != null && !request.getDescription().trim().isEmpty()) {
                protoRequestBuilder.setDescription(request.getDescription());
            }

            if (request.getIsActive() != null) {
                protoRequestBuilder.setIsActive(request.getIsActive());
            }

            if (request.getConfig() != null) {
                try {
                    JsonNode configNode = objectMapper.valueToTree(request.getConfig());

                    CouponServiceProto.DiscountConfig.Builder configBuilder =
                        CouponServiceProto.DiscountConfig.newBuilder();

                    configNode.properties().forEach(entry -> {
                        Value value = convertJsonNodeToValue(entry.getValue());
                        configBuilder.putConfig(entry.getKey(), value);
                    });

                    protoRequestBuilder.setConfig(configBuilder.build());
                } catch (Exception e) {
                    log.error("Error serializing config: {}", e.getMessage(), e);
                    ErrorResponse errorResponse = ErrorResponse.builder()
                            .code("INVALID_CONFIG")
                            .message("Invalid config format")
                            .build();
                    return ResponseEntity.badRequest().body(errorResponse);
                }
            }

            if (request.getCollectionKeyId() != null) {
                protoRequestBuilder.setCollectionKeyId(request.getCollectionKeyId());
            }

            if (request.getStartDate() != null && !request.getStartDate().trim().isEmpty()) {
                protoRequestBuilder.setStartDate(request.getStartDate());
            }

            if (request.getEndDate() != null && !request.getEndDate().trim().isEmpty()) {
                protoRequestBuilder.setEndDate(request.getEndDate());
            }

            CouponServiceProto.UpdateCouponResponse response = couponServiceClient.updateCoupon(protoRequestBuilder.build());

            if (response.getStatus().getCode() == CouponServiceProto.StatusCode.OK) {
                CouponServiceProto.UpdateCouponResponsePayload payload = response.getPayload();

                String configJson = "{}";
                if (payload.hasConfig() && payload.getConfig().getConfigCount() > 0) {
                    try {
                        Map<String, Object> configMap = new HashMap<>();
                        for (Map.Entry<String, com.google.protobuf.Value> entry : payload.getConfig().getConfigMap().entrySet()) {
                            configMap.put(entry.getKey(), convertProtobufValueToJavaObject(entry.getValue()));
                        }
                        configJson = objectMapper.writeValueAsString(configMap);
                    } catch (Exception e) {
                        log.warn("Error converting config to JSON for response: {}", e.getMessage());
                        configJson = "{}";
                    }
                }

                UpdateCouponResponse updateResponse = UpdateCouponResponse.builder()
                        .couponId(payload.getCouponId())
                        .code(payload.getCode())
                        .title(payload.getTitle())
                        .description(payload.getDescription())
                        .type(payload.getType())
                        .config(configJson)
                        .collectionKeyId(payload.getCollectionKeyId())
                        .startDate(payload.getStartDate())
                        .endDate(payload.getEndDate())
                        .isActive(payload.getIsActive())
                        .createdAt(payload.getCreatedAt())
                        .updatedAt(payload.getUpdatedAt())
                        .build();

                return ResponseEntity.ok(updateResponse);
            } else {
                ErrorResponse errorResponse = ErrorResponse.builder()
                        .code(response.getError().getCode())
                        .message(response.getError().getMessage())
                        .details(response.getError().getDetailsMap())
                        .build();
                return ResponseEntity.badRequest().body(errorResponse);
            }
        } catch (Exception e) {
            log.error("Error in update coupon: {}", e.getMessage(), e);
            ErrorResponse errorResponse = ErrorResponse.builder()
                    .code("INTERNAL_ERROR")
                    .message("Internal server error")
                    .build();

            return ResponseEntity.internalServerError().body(errorResponse);
        }
    }

    @GetMapping
    @RequireAdmin
    public ResponseEntity<?> listCoupons(
            @RequestParam(name = "page",defaultValue = "0") int page,
            @RequestParam(name = "size",defaultValue = "10") int size,
            @RequestParam(name = "status",required = false) String status) {
        try {
            log.info("List coupons: page={}, size={}, status={}", page, size, status);
            ObjectMapper objectMapper = new ObjectMapper();

            CouponServiceProto.ListCouponsRequest.Builder requestBuilder = CouponServiceProto.ListCouponsRequest.newBuilder()
                    .setPage(page)
                    .setSize(size);

            if (status != null && !status.isEmpty()) {
                requestBuilder.setStatus(status);
            }

            CouponServiceProto.ListCouponsResponse response = couponServiceClient.listCoupons(requestBuilder.build());

            if (response.getStatus().getCode() == CouponServiceProto.StatusCode.OK) {
                List<CouponSummaryResponse> couponList = response.getPayload().getCouponsList().stream().map(protoCoupon -> {
                    String configJson = "{}";
                    if (protoCoupon.hasConfig() && protoCoupon.getConfig().getConfigCount() > 0) {
                        try {
                            Map<String, Object> configMap = new HashMap<>();
                            for (Map.Entry<String, com.google.protobuf.Value> entry : protoCoupon.getConfig().getConfigMap().entrySet()) {
                                configMap.put(entry.getKey(), convertProtobufValueToJavaObject(entry.getValue()));
                            }
                            configJson = objectMapper.writeValueAsString(configMap);
                        } catch (Exception e) {
                            log.warn("Error converting config to JSON for coupon {}: {}", protoCoupon.getCouponId(), e.getMessage());
                            configJson = "{}";
                        }
                    }

                    return CouponSummaryResponse.builder()
                        .couponId(protoCoupon.getCouponId())
                        .code(protoCoupon.getCode())
                        .title(protoCoupon.getTitle())
                        .description(protoCoupon.getDescription())
                        .type(protoCoupon.getType())
                        .config(configJson)
                        .isActive(protoCoupon.getIsActive())
                        .startDate(protoCoupon.getStartDate())
                        .endDate(protoCoupon.getEndDate())
                        .collectionKeyId(protoCoupon.getCollectionKeyId())
                        .build();
                }).toList();

                ListCouponsResponse listCouponsResponse = ListCouponsResponse.builder()
                    .coupons(couponList)
                    .page(response.getPayload().getPage())
                    .size(response.getPayload().getSize())
                    .totalElements(response.getPayload().getTotalCount())
                    .totalPages((response.getPayload().getCouponsCount()/size) + 1)
                    .build();

                return ResponseEntity.ok(listCouponsResponse);
            } else {
                ErrorResponse errorResponse = ErrorResponse.builder()
                        .code(response.getError().getCode())
                        .message(response.getError().getMessage())
                        .details(response.getError().getDetailsMap())
                        .build();

                return ResponseEntity.badRequest().body(errorResponse);
            }
        } catch (Exception e) {
            log.error("Error in list coupons: {}", e.getMessage(), e);

            ErrorResponse errorResponse = ErrorResponse.builder()
                    .code("INTERNAL_ERROR")
                    .message("Internal server error")
                    .build();

            return ResponseEntity.internalServerError().body(errorResponse);
        }
    }

    @GetMapping("/user/{userId}")
    @Observed(name = "get-user-coupons", contextualName = "user-coupons-retrieval")
    @PerformanceMonitor
    public ResponseEntity<?> getUserCoupons(
            @PathVariable("userId") int userId,
            @RequestParam(name = "page",defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "10") int size) {
        try {
            log.info("Get user coupons: user={}", userId);

            CouponServiceProto.GetUserCouponsRequest request = CouponServiceProto.GetUserCouponsRequest.newBuilder()
                    .setUserId(userId)
                    .setPage(page)
                    .setSize(size)
                    .build();

            CouponServiceProto.GetUserCouponsResponse response = couponServiceClient.getUserCoupons(request);

            if (response.getStatus().getCode() == CouponServiceProto.StatusCode.OK) {
                GetUserCouponsResponse userCouponsResponse = GetUserCouponsResponse.builder()
                        .userCoupons(response.getPayload().getUserCouponsList().stream()
                                .map(protoCoupon -> UserCouponResponse.builder()
                                        .couponId(protoCoupon.getCouponId())
                                        .couponCode(protoCoupon.getCouponCode())
                                        .description(protoCoupon.getDescription())
                                        .status(protoCoupon.getStatus())
                                        .type(protoCoupon.getType())
                                        .value(protoCoupon.getValue())
                                        .startDate(protoCoupon.getStartDate())
                                        .endDate(protoCoupon.getEndDate())
                                        .build())
                                .toList())
                        .totalCount(response.getPayload().getTotalCount())
                        .page(response.getPayload().getPage())
                        .size(response.getPayload().getSize())
                        .build();

                return ResponseEntity.ok(userCouponsResponse);
            } else {
                ErrorResponse errorResponse = ErrorResponse.builder()
                        .code(response.getError().getCode())
                        .message(response.getError().getMessage())
                        .details(response.getError().getDetailsMap())
                        .build();

                return ResponseEntity.badRequest().body(errorResponse);
            }
        } catch (Exception e) {
            log.error("Error in get user coupons: {}", e.getMessage(), e);

            ErrorResponse errorResponse = ErrorResponse.builder()
                    .code("INTERNAL_ERROR")
                    .message("Internal server error")
                    .build();

            return ResponseEntity.internalServerError().body(errorResponse);
        }
    }


    private Value convertJsonNodeToValue(JsonNode node) {
        Value.Builder valueBuilder = Value.newBuilder();

        if (node.isTextual()) {
            valueBuilder.setStringValue(node.textValue());
        } else if (node.isNumber()) {
            valueBuilder.setNumberValue(node.doubleValue());
        } else if (node.isBoolean()) {
            valueBuilder.setBoolValue(node.booleanValue());
        } else if (node.isObject()) {
            com.google.protobuf.Struct.Builder structBuilder = com.google.protobuf.Struct.newBuilder();
            node.properties().forEach(entry -> {
                structBuilder.putFields(entry.getKey(), convertJsonNodeToValue(entry.getValue()));
            });
            valueBuilder.setStructValue(structBuilder.build());
        } else if (node.isArray()) {
            com.google.protobuf.ListValue.Builder listBuilder = com.google.protobuf.ListValue.newBuilder();
            node.forEach(item -> listBuilder.addValues(convertJsonNodeToValue(item)));
            valueBuilder.setListValue(listBuilder.build());
        } else if (node.isNull()) {
            valueBuilder.setNullValue(com.google.protobuf.NullValue.NULL_VALUE);
        }

        return valueBuilder.build();
    }

    private Object convertProtobufValueToJavaObject(com.google.protobuf.Value value) {
        switch (value.getKindCase()) {
            case NUMBER_VALUE:
                return value.getNumberValue();
            case STRING_VALUE:
                return value.getStringValue();
            case BOOL_VALUE:
                return value.getBoolValue();
            case STRUCT_VALUE:
                java.util.Map<String, Object> structMap = new java.util.HashMap<>();
                for (java.util.Map.Entry<String, com.google.protobuf.Value> entry : value.getStructValue().getFieldsMap().entrySet()) {
                    structMap.put(entry.getKey(), convertProtobufValueToJavaObject(entry.getValue()));
                }
                return structMap;
            case LIST_VALUE:
                java.util.List<Object> list = new java.util.ArrayList<>();
                for (com.google.protobuf.Value listValue : value.getListValue().getValuesList()) {
                    list.add(convertProtobufValueToJavaObject(listValue));
                }
                return list;
            case KIND_NOT_SET:
            default:
                return null;
        }
    }
}
