package org.couponmanagement.grpc;

import net.devh.boot.grpc.client.inject.GrpcClient;
import org.couponmanagement.coupon.*;
import org.springframework.stereotype.Service;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class CouponServiceClient {

    @GrpcClient("coupon-service")
    private CouponServiceGrpc.CouponServiceBlockingStub couponServiceStub;

    public CouponServiceProto.ApplyCouponManualResponse applyCouponManual(CouponServiceProto.ApplyCouponManualRequest request) {
        try {
            log.info("Calling coupon service - apply manual: user={}, coupon={}",
                    request.getUserId(), request.getCouponCode());
            return couponServiceStub.applyCouponManual(request);
        } catch (Exception e) {
            log.error("Error in applyCouponManual: {}", e.getMessage(), e);
            throw e;
        }
    }

    public CouponServiceProto.ApplyCouponAutoResponse applyCouponAuto(CouponServiceProto.ApplyCouponAutoRequest request) {
        try {
            log.info("Calling coupon service - apply auto: user={}", request.getUserId());
            return couponServiceStub.applyCouponAuto(request);
        } catch (Exception e) {
            log.error("Error in applyCouponAuto: {}", e.getMessage(), e);
            throw e;
        }
    }

    public CouponServiceProto.UpdateCouponResponse updateCoupon(CouponServiceProto.UpdateCouponRequest request) {
        try {
            log.info("Calling coupon service - update: id={}", request.getCouponId());
            return couponServiceStub.updateCoupon(request);
        } catch (Exception e) {
            log.error("Error in updateCoupon: {}", e.getMessage(), e);
            throw e;
        }
    }

    public CouponServiceProto.GetCouponDetailsResponse getCouponDetails(CouponServiceProto.GetCouponDetailsRequest request) {
        try {
            log.info("Calling coupon service - get details: id={}", request.getCouponId());
            return couponServiceStub.getCouponDetails(request);
        } catch (Exception e) {
            log.error("Error in getCouponDetails: {}", e.getMessage(), e);
            throw e;
        }
    }

    public CouponServiceProto.ListCouponsResponse listCoupons(CouponServiceProto.ListCouponsRequest request) {
        try {
            log.info("Calling coupon service - list: page={}, size={}",
                    request.getPage(), request.getSize());
            return couponServiceStub.listCoupons(request);
        } catch (Exception e) {
            log.error("Error in listCoupons: {}", e.getMessage(), e);
            throw e;
        }
    }

    public CouponServiceProto.GetUserCouponsResponse getUserCoupons(CouponServiceProto.GetUserCouponsRequest request) {
        try {
            log.info("Calling coupon service - user coupons: user={}", request.getUserId());
            return couponServiceStub.getUserCoupons(request);
        } catch (Exception e) {
            log.error("Error in getUserCoupons: {}", e.getMessage(), e);
            throw e;
        }
    }
}
