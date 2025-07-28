package org.couponmanagement.grpc;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.protobuf.Value;
import io.grpc.stub.StreamObserver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.devh.boot.grpc.server.service.GrpcService;
import org.couponmanagement.cache.CouponCacheService;
import org.couponmanagement.dto.UserCouponClaimInfo;
import org.couponmanagement.dto.CouponDetail;
import org.couponmanagement.coupon.CouponServiceGrpc;
import org.couponmanagement.coupon.CouponServiceProto;
import org.couponmanagement.dto.UserCouponIds;
import org.couponmanagement.entity.Coupon;
import org.couponmanagement.entity.CouponUser;
import org.couponmanagement.grpc.annotation.RequireAuth;
import org.couponmanagement.grpc.validation.RequestValidator;
import org.couponmanagement.grpc.annotation.PerformanceMonitor;
import org.couponmanagement.repository.CouponRepository;
import org.couponmanagement.repository.CouponUserRepository;
import org.couponmanagement.service.CouponApplicationResult;
import org.couponmanagement.service.CouponService;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.google.protobuf.NullValue.NULL_VALUE;

@GrpcService
@RequiredArgsConstructor
@Slf4j
public class CouponGrpcService extends CouponServiceGrpc.CouponServiceImplBase {

    private final CouponService couponService;
    private final RequestValidator validator;
    private final CouponRepository couponRepository;
    private final CouponCacheService couponCacheService;
    private final CouponUserRepository couponUserRepository;

    @Override
    @RequireAuth("USE_COUPON")
    @PerformanceMonitor()
    public void applyCouponManual(CouponServiceProto.ApplyCouponManualRequest request,
                                StreamObserver<CouponServiceProto.ApplyCouponManualResponse> responseObserver) {
        
        log.info("Received applyCouponManual gRPC request: userId={}, couponCode={}, orderAmount={}", 
                request.getUserId(), request.getCouponCode(), request.getOrderAmount());

        try {
            validator.validateUserId(request.getUserId());
            validator.validateCouponCode(request.getCouponCode());
            validator.validateOrderAmount(request.getOrderAmount());

            CouponApplicationResult result = couponService.applyCouponManual(
                    request.getUserId(), 
                    request.getCouponCode(), 
                    BigDecimal.valueOf(request.getOrderAmount()),
                    parseOrderDateTime(request.getOrderDate())
                    );

            CouponServiceProto.ApplyCouponManualResponse response;
            BigDecimal orderAmount = BigDecimal.valueOf(request.getOrderAmount());
            BigDecimal finalAmount = orderAmount.subtract(result.getDiscountAmount());
            if (result.isSuccess()) {

                response = CouponServiceProto.ApplyCouponManualResponse.newBuilder()
                        .setStatus(CouponServiceProto.Status.newBuilder()
                                .setCode(CouponServiceProto.StatusCode.OK)
                                .setMessage("Coupon applied successfully")
                                .build())
                        .setPayload(CouponServiceProto.ApplyCouponManualResponsePayload.newBuilder()
                                .setSuccess(true)
                                .setCouponId(result.getCouponId())
                                .setCouponCode(result.getCouponCode())
                                .setOrderAmount(orderAmount.doubleValue())
                                .setDiscountAmount(result.getDiscountAmount().doubleValue())
                                .setFinalAmount(finalAmount.doubleValue())
                                .build())
                        .build();

                log.info("Manual coupon applied successfully: couponId={}, discount={}", 
                        result.getCouponId(), result.getDiscountAmount());
            } else {
                response = CouponServiceProto.ApplyCouponManualResponse.newBuilder()
                        .setStatus(CouponServiceProto.Status.newBuilder()
                                .setCode(CouponServiceProto.StatusCode.INTERNAL)
                                .setMessage(result.getErrorMessage())
                                .build())
                        .setPayload(CouponServiceProto.ApplyCouponManualResponsePayload.newBuilder()
                                .setSuccess(false)
                                .setOrderAmount(orderAmount.doubleValue())
                                .setDiscountAmount(result.getDiscountAmount().doubleValue())
                                .setFinalAmount(finalAmount.doubleValue())
                                .setErrorMessage(result.getErrorMessage())
                                .build())
                        .build();

                log.warn("Manual coupon application failed: {}", result.getErrorMessage());
            }

            responseObserver.onNext(response);
            responseObserver.onCompleted();

        } catch (Exception e) {
            log.error("Error in applyCouponManual gRPC call: userId={}, couponCode={}, error={}", 
                     request.getUserId(), request.getCouponCode(), e.getMessage(), e);

            CouponServiceProto.ApplyCouponManualResponse errorResponse = CouponServiceProto.ApplyCouponManualResponse.newBuilder()
                    .setStatus(CouponServiceProto.Status.newBuilder()
                            .setCode(CouponServiceProto.StatusCode.INTERNAL)
                            .setMessage("Internal server error")
                            .build())
                    .setError(CouponServiceProto.Error.newBuilder()
                            .setCode("INTERNAL_ERROR")
                            .setMessage("Internal server error: " + e.getMessage())
                            .build())
                    .build();

            responseObserver.onNext(errorResponse);
            responseObserver.onCompleted();
        }
    }

    @Override
    @RequireAuth("USE_COUPON")
    @PerformanceMonitor()
    public void applyCouponAuto(CouponServiceProto.ApplyCouponAutoRequest request,
                              StreamObserver<CouponServiceProto.ApplyCouponAutoResponse> responseObserver) {
        
        log.info("Received applyCouponAuto gRPC request: userId={}, orderAmount={}", 
                request.getUserId(), request.getOrderAmount());

        try {
            validator.validateUserId(request.getUserId());
            validator.validateOrderAmount(request.getOrderAmount());

            CouponApplicationResult result = couponService.applyCouponAutoParallelSync(
                    request.getUserId(), 
                    BigDecimal.valueOf(request.getOrderAmount()),
                    parseOrderDateTime(request.getOrderDate()));

            CouponServiceProto.ApplyCouponAutoResponse response;

            BigDecimal orderAmount = BigDecimal.valueOf(request.getOrderAmount());
            BigDecimal finalAmount = orderAmount.subtract(result.getDiscountAmount());
            if (result.isSuccess()) {

                response = CouponServiceProto.ApplyCouponAutoResponse.newBuilder()
                        .setStatus(CouponServiceProto.Status.newBuilder()
                                .setCode(CouponServiceProto.StatusCode.OK)
                                .setMessage("Best coupon applied successfully")
                                .build())
                        .setPayload(CouponServiceProto.ApplyCouponAutoResponsePayload.newBuilder()
                                .setSuccess(true)
                                .setCouponId(result.getCouponId())
                                .setCouponCode(result.getCouponCode())
                                .setOrderAmount(orderAmount.doubleValue())
                                .setDiscountAmount(result.getDiscountAmount().doubleValue())
                                .setFinalAmount(finalAmount.doubleValue())
                                .build())
                        .build();

                log.info("Auto coupon applied successfully: couponId={}, discount={}", 
                        result.getCouponId(), result.getDiscountAmount());
            } else {

                response = CouponServiceProto.ApplyCouponAutoResponse.newBuilder()
                        .setStatus(CouponServiceProto.Status.newBuilder()
                                .setCode(CouponServiceProto.StatusCode.OK)
                                .setMessage("No applicable coupon found")
                                .build())
                        .setPayload(CouponServiceProto.ApplyCouponAutoResponsePayload.newBuilder()
                                .setSuccess(false)
                                .setOrderAmount(orderAmount.doubleValue())
                                .setDiscountAmount(0)
                                .setFinalAmount(finalAmount.doubleValue())
                                .build())
                        .build();

                log.info("Auto coupon application failed: {}", result.getErrorMessage());
            }

            responseObserver.onNext(response);
            responseObserver.onCompleted();

        } catch (Exception e) {
            log.error("Error in applyCouponAuto gRPC call: userId={}, error={}", 
                     request.getUserId(), e.getMessage(), e);

            CouponServiceProto.ApplyCouponAutoResponse errorResponse = CouponServiceProto.ApplyCouponAutoResponse.newBuilder()
                    .setStatus(CouponServiceProto.Status.newBuilder()
                            .setCode(CouponServiceProto.StatusCode.INTERNAL)
                            .setMessage("Internal server error")
                            .build())
                    .setError(CouponServiceProto.Error.newBuilder()
                            .setCode("INTERNAL_ERROR")
                            .setMessage("Internal server error: " + e.getMessage())
                            .build())
                    .build();

            responseObserver.onNext(errorResponse);
            responseObserver.onCompleted();
        }
    }

    @Override
    @RequireAuth("MANAGE_COUPON")
    @PerformanceMonitor()
    @Transactional
    public void updateCoupon(CouponServiceProto.UpdateCouponRequest request,
                           StreamObserver<CouponServiceProto.UpdateCouponResponse> responseObserver) {

        log.info("Received updateCoupon gRPC request: couponId={}", request.getCouponId());

        try {
            // Validate input
            if (request.getCouponId() <= 0) {
                throw new IllegalArgumentException("Invalid coupon ID");
            }

            Coupon existingCoupon = couponRepository.findById(request.getCouponId())
                    .orElseThrow(() -> new IllegalArgumentException("Coupon not found"));

            existingCoupon.setDescription(request.getDescription());
            existingCoupon.setCode(request.getCode());
            existingCoupon.setTitle(request.getTitle());
            existingCoupon.setExpiryDate(parseOrderDateTime(request.getEndDate()));
            existingCoupon.setIsActive(request.getIsActive());
            existingCoupon.setCollectionKeyId(request.getCollectionKeyId());

            if (request.hasConfig()) {
                Map<String, Value> configMap = request.getConfig().getConfigMap();
                Coupon.DiscountConfig currentConfig = existingCoupon.getDiscountConfig();

                boolean configUpdated = false;

                if (configMap.containsKey("max_discount")) {
                    Value maxDiscountProto = configMap.get("max_discount");
                    if (maxDiscountProto.hasNumberValue()) {
                        BigDecimal maxDiscount = BigDecimal.valueOf(maxDiscountProto.getNumberValue());
                        if (maxDiscount.compareTo(BigDecimal.ZERO) >= 0) {
                            currentConfig.setMaxDiscount(maxDiscount);
                            configUpdated = true;
                            log.debug("Updated maxDiscount for coupon {}: {}", request.getCouponId(), maxDiscount);
                        } else {
                            throw new IllegalArgumentException("maxDiscount must be non-negative");
                        }
                    } else if (maxDiscountProto.hasNullValue()) {
                        currentConfig.setMaxDiscount(null);
                        configUpdated = true;
                        log.debug("Removed maxDiscount limit for coupon {}", request.getCouponId());
                    }
                }

                if (configUpdated) {
                    existingCoupon.setDiscountConfig(currentConfig);
                }
            }

            Coupon updatedCoupon = couponRepository.save(existingCoupon);
            log.info("Coupon updated in database successfully: couponId={}", request.getCouponId());

            try {
                CouponDetail couponDetail = CouponDetail.fromCoupon(updatedCoupon);
                updateCouponDetailCaches(couponDetail);
                couponCacheService.cacheCouponCodeMapping(couponDetail.getCouponCode(), couponDetail.getCouponId());
                log.info("Cache updated successfully for coupon: couponId={}", request.getCouponId());
            } catch (Exception cacheException) {
                log.warn("Failed to update cache for coupon {}: {}",
                        request.getCouponId(), cacheException.getMessage());
                throw new Exception("Faild to update cache");
            }

            CouponServiceProto.UpdateCouponResponsePayload payload = buildCouponPayload(updatedCoupon);

            CouponServiceProto.UpdateCouponResponse response = CouponServiceProto.UpdateCouponResponse.newBuilder()
                    .setStatus(CouponServiceProto.Status.newBuilder()
                            .setCode(CouponServiceProto.StatusCode.OK)
                            .setMessage("Coupon updated successfully")
                            .build())
                    .setPayload(payload)
                    .build();

            log.info("Coupon updated successfully: couponId={}", request.getCouponId());

            responseObserver.onNext(response);
            responseObserver.onCompleted();

        } catch (IllegalArgumentException e) {
            log.warn("Invalid request for updateCoupon: couponId={}, error={}", request.getCouponId(), e.getMessage());

            CouponServiceProto.UpdateCouponResponse errorResponse = CouponServiceProto.UpdateCouponResponse.newBuilder()
                    .setStatus(CouponServiceProto.Status.newBuilder()
                            .setCode(CouponServiceProto.StatusCode.INVALID_ARGUMENT)
                            .setMessage("Invalid request")
                            .build())
                    .setError(CouponServiceProto.Error.newBuilder()
                            .setCode("INVALID_ARGUMENT")
                            .setMessage(e.getMessage())
                            .build())
                    .build();

            responseObserver.onNext(errorResponse);
            responseObserver.onCompleted();

        } catch (Exception e) {
            log.error("Error in updateCoupon gRPC call: couponId={}, error={}", request.getCouponId(), e.getMessage(), e);

            CouponServiceProto.UpdateCouponResponse errorResponse = CouponServiceProto.UpdateCouponResponse.newBuilder()
                    .setStatus(CouponServiceProto.Status.newBuilder()
                            .setCode(CouponServiceProto.StatusCode.INTERNAL)
                            .setMessage("Internal server error")
                            .build())
                    .setError(CouponServiceProto.Error.newBuilder()
                            .setCode("INTERNAL_ERROR")
                            .setMessage("Internal server error: " + e.getMessage())
                            .build())
                    .build();

            responseObserver.onNext(errorResponse);
            responseObserver.onCompleted();
        }
    }

    @Override
    @RequireAuth("MANAGE_COUPON")
    @PerformanceMonitor()
    public void listCoupons(CouponServiceProto.ListCouponsRequest request,
                          StreamObserver<CouponServiceProto.ListCouponsResponse> responseObserver) {

        log.info("Received listCoupons gRPC request: page={}, size={}, status={}",
                request.getPage(), request.getSize(), request.getStatus());

        try {
            int page = Math.max(0, request.getPage());
            int size = Math.min(Math.max(1, request.getSize()), 100);

            CouponService.CouponsListResult result = couponService.listCoupons(page, size, request.getStatus());

            List<CouponServiceProto.CouponSummary> couponSummaries = result.couponDetails().stream()
                    .map(this::buildCouponSummaryFromDetail)
                    .toList();

            CouponServiceProto.ListCouponsResponsePayload payload = CouponServiceProto.ListCouponsResponsePayload.newBuilder()
                    .addAllCoupons(couponSummaries)
                    .setTotalCount(result.totalCount())
                    .setPage(result.page())
                    .setSize(result.size())
                    .build();

            CouponServiceProto.ListCouponsResponse response = CouponServiceProto.ListCouponsResponse.newBuilder()
                    .setStatus(CouponServiceProto.Status.newBuilder()
                            .setCode(CouponServiceProto.StatusCode.OK)
                            .setMessage("Coupons retrieved successfully")
                            .build())
                    .setPayload(payload)
                    .build();

            log.info("Coupons listed successfully: totalCount={}, page={}, size={}, returnedCount={}",
                    result.totalCount(), page, size, couponSummaries.size());

            responseObserver.onNext(response);
            responseObserver.onCompleted();

        } catch (Exception e) {
            log.error("Error in listCoupons gRPC call: error={}", e.getMessage(), e);

            CouponServiceProto.ListCouponsResponse errorResponse = CouponServiceProto.ListCouponsResponse.newBuilder()
                    .setStatus(CouponServiceProto.Status.newBuilder()
                            .setCode(CouponServiceProto.StatusCode.INTERNAL)
                            .setMessage("Internal server error")
                            .build())
                    .setError(CouponServiceProto.Error.newBuilder()
                            .setCode("INTERNAL_ERROR")
                            .setMessage("Internal server error: " + e.getMessage())
                            .build())
                    .build();

            responseObserver.onNext(errorResponse);
            responseObserver.onCompleted();
        }
    }

    @Override
    @RequireAuth("USE_COUPON")
    @PerformanceMonitor()
    public void getUserCoupons(CouponServiceProto.GetUserCouponsRequest request,
                             StreamObserver<CouponServiceProto.GetUserCouponsResponse> responseObserver) {

        log.info("Received getUserCoupons gRPC request: userId={}, page={}, size={}",
                request.getUserId(), request.getPage(), request.getSize());

        try {
            int userId = request.getUserId();
            int page = Math.max(0, request.getPage());
            int size = Math.min(Math.max(1, request.getSize()), 100);

            CouponService.UserCouponsResult result = couponService.getUserCouponsWithPagination(userId, page, size);

            List<CouponServiceProto.UserCouponSummary> userCouponSummaries = result.userCouponClaimInfos().stream()
                    .filter(claimInfo -> result.couponDetailMap().containsKey(claimInfo.getCouponId()))
                    .map(claimInfo -> buildUserCouponSummary(
                            result.couponDetailMap().get(claimInfo.getCouponId()),
                            claimInfo))
                    .toList();

            CouponServiceProto.GetUserCouponsResponsePayload payload = CouponServiceProto.GetUserCouponsResponsePayload.newBuilder()
                    .addAllUserCoupons(userCouponSummaries)
                    .setTotalCount(result.totalCount())
                    .setPage(result.page())
                    .setSize(result.size())
                    .build();

            CouponServiceProto.GetUserCouponsResponse response = CouponServiceProto.GetUserCouponsResponse.newBuilder()
                    .setStatus(CouponServiceProto.Status.newBuilder()
                            .setCode(CouponServiceProto.StatusCode.OK)
                            .setMessage("User coupons retrieved successfully")
                            .build())
                    .setPayload(payload)
                    .build();

            log.info("User coupons response built successfully: userId={}, totalCount={}, returnedCount={}",
                    userId, result.totalCount(), userCouponSummaries.size());

            responseObserver.onNext(response);
            responseObserver.onCompleted();

        } catch (Exception e) {
            log.error("Error in getUserCoupons gRPC call: userId={}, error={}", request.getUserId(), e.getMessage(), e);

            CouponServiceProto.GetUserCouponsResponse errorResponse = CouponServiceProto.GetUserCouponsResponse.newBuilder()
                    .setStatus(CouponServiceProto.Status.newBuilder()
                            .setCode(CouponServiceProto.StatusCode.INTERNAL)
                            .setMessage("Internal server error")
                            .build())
                    .setError(CouponServiceProto.Error.newBuilder()
                            .setCode("INTERNAL_ERROR")
                            .setMessage("Internal server error: " + e.getMessage())
                            .build())
                    .build();

            responseObserver.onNext(errorResponse);
            responseObserver.onCompleted();
        }
    }

    @Override
    @RequireAuth("USE_COUPON")
    @PerformanceMonitor()
    @Transactional
    public void rollbackCouponUsage(CouponServiceProto.RollbackCouponUsageRequest request,
                                    StreamObserver<CouponServiceProto.RollbackCouponUsageResponse> responseObserver) {
        log.info("Received rollbackCouponUsage gRPC request: userId={}, couponId={}", request.getUserId(), request.getCouponId());
        try {
            // Find CouponUser by userId and couponId
            var couponUserOpt = couponUserRepository.findByUserIdAndCouponId(request.getUserId(), request.getCouponId());
            if (couponUserOpt.isEmpty()) {
                var response = CouponServiceProto.RollbackCouponUsageResponse.newBuilder()
                        .setStatus(CouponServiceProto.Status.newBuilder()
                                .setCode(CouponServiceProto.StatusCode.NOT_FOUND)
                                .setMessage("Coupon usage not found for user")
                                .build())
                        .setError(CouponServiceProto.Error.newBuilder()
                                .setCode("NOT_FOUND")
                                .setMessage("Coupon usage not found for user")
                                .build())
                        .build();
                responseObserver.onNext(response);
                responseObserver.onCompleted();
                return;
            }
            var couponUser = couponUserOpt.get();
            couponUser.setStatus(CouponUser.CouponUserStatus.CLAIMED);
            couponUser.setUsedAt(null);
            couponUser.setUpdatedAt(java.time.LocalDateTime.now());
            couponUserRepository.save(couponUser);

            var userCouponIdsOpt = couponCacheService.getCachedUserCouponIds(request.getUserId());
            UserCouponIds userCouponIds = userCouponIdsOpt.orElseGet(() -> UserCouponIds.of(new HashMap<>()));
            UserCouponClaimInfo claimInfo = UserCouponClaimInfo.builder()
                    .userId(couponUser.getUserId())
                    .couponId(couponUser.getCouponId())
                    .claimedDate(couponUser.getClaimedAt())
                    .expiryDate(couponUser.getExpiryDate())
                    .build();
            userCouponIds.getUserCouponInfo().put(couponUser.getCouponId(), claimInfo);
            couponCacheService.cacheUserCouponIds(request.getUserId(), userCouponIds);

            var response = CouponServiceProto.RollbackCouponUsageResponse.newBuilder()
                    .setStatus(CouponServiceProto.Status.newBuilder()
                            .setCode(CouponServiceProto.StatusCode.OK)
                            .setMessage("Coupon usage rolled back successfully")
                            .build())
                    .build();
            responseObserver.onNext(response);
            responseObserver.onCompleted();
        } catch (Exception e) {
            log.error("Error in rollbackCouponUsage gRPC call: userId={}, couponId={}, error={}",
                    request.getUserId(), request.getCouponId(), e.getMessage(), e);
            var errorResponse = CouponServiceProto.RollbackCouponUsageResponse.newBuilder()
                    .setStatus(CouponServiceProto.Status.newBuilder()
                            .setCode(CouponServiceProto.StatusCode.INTERNAL)
                            .setMessage("Internal server error")
                            .build())
                    .setError(CouponServiceProto.Error.newBuilder()
                            .setCode("INTERNAL_ERROR")
                            .setMessage("Internal server error: " + e.getMessage())
                            .build())
                    .build();
            responseObserver.onNext(errorResponse);
            responseObserver.onCompleted();
        }
    }

    private LocalDateTime parseOrderDateTime(String orderDate) {
        if (orderDate == null || orderDate.trim().isEmpty()) {
            return LocalDateTime.now();
        }

        try {
            return LocalDateTime.parse(orderDate, DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        } catch (DateTimeParseException e) {
            log.warn("Error parsing order_date: {}, using current datetime", orderDate, e);
            return LocalDateTime.now();
        }
    }

    private CouponServiceProto.UpdateCouponResponsePayload buildCouponPayload(Coupon coupon) {
        CouponServiceProto.DiscountConfig.Builder discountConfigBuilder =
                CouponServiceProto.DiscountConfig.newBuilder();

        Map<String, Object> configMap = coupon.getDiscountConfigMap();
        if (!configMap.isEmpty()) {
            for (Map.Entry<String, Object> entry : configMap.entrySet()) {
                Value.Builder valueBuilder = Value.newBuilder();

                Object value = entry.getValue();
                switch (value) {
                    case String s -> valueBuilder.setStringValue(s);
                    case Number number -> valueBuilder.setNumberValue(number.doubleValue());
                    case Boolean b -> valueBuilder.setBoolValue(b);
                    case null -> valueBuilder.setNullValue(NULL_VALUE);
                    default -> valueBuilder.setStringValue(value.toString());
                }

                discountConfigBuilder.putConfig(entry.getKey(), valueBuilder.build());
            }
        }

        return CouponServiceProto.UpdateCouponResponsePayload.newBuilder()
                .setCouponId(coupon.getId())
                .setCode(coupon.getCode())
                .setTitle(coupon.getTitle() != null ? coupon.getTitle() : "")
                .setDescription(coupon.getDescription() != null ? coupon.getDescription() : "")
                .setStatus(coupon.getIsActive() ? "ACTIVE" : "INACTIVE")
                .setType(coupon.getDiscountType())
                .setValue(coupon.getDiscountValue() != null ? coupon.getDiscountValue().doubleValue() : 0.0)
                .setConfig(discountConfigBuilder.build())
                .setIsActive(coupon.getIsActive())
                .setStartDate(coupon.getCreatedAt().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME))
                .setEndDate(coupon.getExpiryDate().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME))
                .setCreatedAt(coupon.getCreatedAt().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME))
                .setUpdatedAt(coupon.getUpdatedAt().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME))
                .build();
    }

    private CouponServiceProto.CouponSummary buildCouponSummary(Coupon coupon) {
        CouponServiceProto.DiscountConfig.Builder discountConfigBuilder =
                CouponServiceProto.DiscountConfig.newBuilder();

        Map<String, Object> configMap = coupon.getDiscountConfigMap();
        if (!configMap.isEmpty()) {
            for (Map.Entry<String, Object> entry : configMap.entrySet()) {
                com.google.protobuf.Value.Builder valueBuilder = com.google.protobuf.Value.newBuilder();

                Object value = entry.getValue();
                switch (value) {
                    case String s -> valueBuilder.setStringValue(s);
                    case Number number -> valueBuilder.setNumberValue(number.doubleValue());
                    case Boolean b -> valueBuilder.setBoolValue(b);
                    case null -> valueBuilder.setNullValue(NULL_VALUE);
                    default -> valueBuilder.setStringValue(value.toString());
                }

                discountConfigBuilder.putConfig(entry.getKey(), valueBuilder.build());
            }
        }

        return CouponServiceProto.CouponSummary.newBuilder()
                .setCouponId(coupon.getId())
                .setCode(coupon.getCode())
                .setDescription(coupon.getDescription() != null ? coupon.getDescription() : "")
                .setStatus(coupon.getIsActive() ? "ACTIVE" : "INACTIVE")
                .setType(coupon.getDiscountType())
                .setIsActive(coupon.getIsActive())
                .setConfig(discountConfigBuilder.build())
                .setStartDate(coupon.getCreatedAt().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME))
                .setEndDate(coupon.getExpiryDate().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME))
                .build();
    }

    private CouponServiceProto.CouponSummary buildCouponSummaryFromDetail(CouponDetail couponDetail) {
        CouponServiceProto.DiscountConfig.Builder discountConfigBuilder =
                CouponServiceProto.DiscountConfig.newBuilder();

        if (couponDetail.getDiscountConfigJson() != null && !couponDetail.getDiscountConfigJson().trim().isEmpty()) {
            try {
                ObjectMapper objectMapper = new ObjectMapper();
                TypeReference<Map<String, Object>> typeRef = new TypeReference<Map<String, Object>>() {};
                Map<String, Object> configMap = objectMapper.readValue(couponDetail.getDiscountConfigJson(), typeRef);

                for (Map.Entry<String, Object> entry : configMap.entrySet()) {
                    com.google.protobuf.Value.Builder valueBuilder = com.google.protobuf.Value.newBuilder();

                    Object value = entry.getValue();
                    switch (value) {
                        case String s -> valueBuilder.setStringValue(s);
                        case Number number -> valueBuilder.setNumberValue(number.doubleValue());
                        case Boolean b -> valueBuilder.setBoolValue(b);
                        case null -> valueBuilder.setNullValue(NULL_VALUE);
                        default -> valueBuilder.setStringValue(value.toString());
                    }

                    discountConfigBuilder.putConfig(entry.getKey(), valueBuilder.build());
                }
            } catch (Exception e) {
                log.warn("Failed to parse discount config JSON for coupon {}: {}", couponDetail.getCouponId(), e.getMessage());
            }
        }

        return CouponServiceProto.CouponSummary.newBuilder()
                .setCouponId(couponDetail.getCouponId())
                .setCode(couponDetail.getCouponCode())
                .setTitle(couponDetail.getTitle())
                .setDescription(couponDetail.getDescription() != null ? couponDetail.getDescription() : "")
                .setStatus(couponDetail.getStatus())
                .setType(couponDetail.getType())
                .setCollectionKeyId(couponDetail.getCollectionKeyId())
                .setIsActive(couponDetail.isActive())
                .setConfig(discountConfigBuilder.build())
                .setStartDate(couponDetail.getCreatedAt().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME))
                .setEndDate(couponDetail.getExpiryDate().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME))
                .build();
    }

    private CouponServiceProto.UserCouponSummary buildUserCouponSummary(CouponDetail couponDetail, UserCouponClaimInfo userCouponClaimInfo) {
        double value = 0.0;
        if (couponDetail.getDiscountConfigJson() != null && !couponDetail.getDiscountConfigJson().trim().isEmpty()) {
            try {
                ObjectMapper objectMapper = new ObjectMapper();
                TypeReference<Map<String, Object>> typeRef = new TypeReference<Map<String, Object>>() {};
                Map<String, Object> configMap = objectMapper.readValue(couponDetail.getDiscountConfigJson(), typeRef);

                Object valueObj = configMap.get("value");
                if (valueObj instanceof Number) {
                    value = ((Number) valueObj).doubleValue();
                }
            } catch (Exception e) {
                log.warn("Failed to parse value from discount config for coupon {}: {}", couponDetail.getCouponId(), e.getMessage());
            }
        }

        return CouponServiceProto.UserCouponSummary.newBuilder()
                .setCouponId(couponDetail.getCouponId())
                .setCouponCode(couponDetail.getCouponCode())
                .setTitle(couponDetail.getTitle())
                .setDescription(couponDetail.getDescription() != null ? couponDetail.getDescription() : "")
                .setStatus(couponDetail.isActive() ? "ACTIVE" : "INACTIVE")
                .setType(couponDetail.getType())
                .setValue(value)
                .setStartDate(userCouponClaimInfo.getClaimedDate().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME))
                .setEndDate(couponDetail.getExpiryDate().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME))
                .build();
    }

    private void updateCouponDetailCaches(CouponDetail couponDetail) {
        try {
            couponCacheService.cacheCouponDetail(couponDetail.getCouponId(), couponDetail);
        } catch (Exception e) {
            log.error("Error during cache invalidation for coupon update: couponId={}, error={}",
                    couponDetail.getCouponId(), e.getMessage(), e);
        }
    }
}
