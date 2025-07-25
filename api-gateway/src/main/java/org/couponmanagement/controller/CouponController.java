package org.couponmanagement.controller;

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
    public ResponseEntity<?> applyCouponManual(@Valid @RequestBody CouponServiceProto.ApplyCouponManualRequest request) {
        try {
            log.info("Apply coupon manual: user={}, coupon={}", request.getUserId(), request.getCouponCode());
            CouponServiceProto.ApplyCouponManualResponse response = couponServiceClient.applyCouponManual(request);

            if (response.getStatus().getCode() == CouponServiceProto.StatusCode.OK) {
                return ResponseEntity.ok(response.getPayload());
            } else {
                return ResponseEntity.badRequest().body(response.getError());
            }
        } catch (Exception e) {
            log.error("Error in apply coupon manual: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().body("Internal server error");
        }
    }

    @PostMapping("/apply-auto")
    public ResponseEntity<?> applyCouponAuto(@Valid @RequestBody CouponServiceProto.ApplyCouponAutoRequest request) {
        try {
            log.info("Apply coupon auto: user={}", request.getUserId());
            CouponServiceProto.ApplyCouponAutoResponse response = couponServiceClient.applyCouponAuto(request);

            if (response.getStatus().getCode() == CouponServiceProto.StatusCode.OK) {
                return ResponseEntity.ok(response.getPayload());
            } else {
                return ResponseEntity.badRequest().body(response.getError());
            }
        } catch (Exception e) {
            log.error("Error in apply coupon auto: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().body("Internal server error");
        }
    }

    @PutMapping("/{couponId}")
    @RequireAdmin
    public ResponseEntity<?> updateCoupon(@PathVariable int couponId, @Valid @RequestBody CouponServiceProto.UpdateCouponRequest request) {
        try {
            log.info("Update coupon: id={}", couponId);
            CouponServiceProto.UpdateCouponRequest.Builder requestBuilder = request.toBuilder();
            requestBuilder.setCouponId(couponId);

            CouponServiceProto.UpdateCouponResponse response = couponServiceClient.updateCoupon(requestBuilder.build());

            if (response.getStatus().getCode() == CouponServiceProto.StatusCode.OK) {
                return ResponseEntity.ok(response.getPayload());
            } else {
                return ResponseEntity.badRequest().body(response.getError());
            }
        } catch (Exception e) {
            log.error("Error in update coupon: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().body("Internal server error");
        }
    }

    @GetMapping("/{couponId}")
    @RequireAdmin
    public ResponseEntity<?> getCouponDetails(@PathVariable int couponId) {
        try {
            log.info("Get coupon details: id={}", couponId);
            CouponServiceProto.GetCouponDetailsRequest request = CouponServiceProto.GetCouponDetailsRequest.newBuilder()
                    .setCouponId(couponId)
                    .build();

            CouponServiceProto.GetCouponDetailsResponse response = couponServiceClient.getCouponDetails(request);

            if (response.getStatus().getCode() == CouponServiceProto.StatusCode.OK) {
                return ResponseEntity.ok(response.getPayload());
            } else {
                return ResponseEntity.badRequest().body(response.getError());
            }
        } catch (Exception e) {
            log.error("Error in get coupon details: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().body("Internal server error");
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
                return ResponseEntity.badRequest().body(response.getError());
            }
        } catch (Exception e) {
            log.error("Error in list coupons: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().body("Internal server error");
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
                return ResponseEntity.badRequest().body(response.getError());
            }
        } catch (Exception e) {
            log.error("Error in get user coupons: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().body("Internal server error");
        }
    }
}
