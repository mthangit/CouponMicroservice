package org.couponmanagement.controller;

import org.couponmanagement.dto.ApplyCouponRequest;
import org.couponmanagement.dto.AutoApplyCouponRequest;
import org.couponmanagement.dto.UpdateCouponRequest;
import org.couponmanagement.dto.response.ApplyCouponResponse;
import org.couponmanagement.dto.response.CouponDetailsResponse;
import org.couponmanagement.dto.response.ErrorResponse;
import org.couponmanagement.grpc.CouponServiceClient;
import org.couponmanagement.coupon.*;
import org.couponmanagement.security.RequireAdmin;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import lombok.extern.slf4j.Slf4j;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/coupons")
@Slf4j
public class CouponController {

    @Autowired
    private CouponServiceClient couponServiceClient;

    @PostMapping("/apply-manual")
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
    public ResponseEntity<?> updateCoupon(@PathVariable int couponId, @Valid @RequestBody UpdateCouponRequest request) {
        try {
            log.info("Update coupon: id={}", couponId);

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
                return ResponseEntity.ok(response.getPayload());
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

            CouponServiceProto.ListCouponsRequest.Builder requestBuilder = CouponServiceProto.ListCouponsRequest.newBuilder()
                    .setPage(page)
                    .setSize(size);

            if (status != null && !status.isEmpty()) {
                requestBuilder.setStatus(status);
            }

            CouponServiceProto.ListCouponsResponse response = couponServiceClient.listCoupons(requestBuilder.build());

            if (response.getStatus().getCode() == CouponServiceProto.StatusCode.OK) {
                return ResponseEntity.ok(response.getPayload());
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
    public ResponseEntity<?> getUserCoupons(
            @PathVariable int userId,
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
                return ResponseEntity.ok(response.getPayload());
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
            log.error("Error in get user coupons: {}", e.getMessage(), e);

            ErrorResponse errorResponse = ErrorResponse.builder()
                    .code("INTERNAL_ERROR")
                    .message("Internal server error")
                    .build();

            return ResponseEntity.internalServerError().body(errorResponse);
        }
    }
}
