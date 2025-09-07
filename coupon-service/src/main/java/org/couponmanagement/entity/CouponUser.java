package org.couponmanagement.entity;

import io.micrometer.observation.annotation.Observed;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.couponmanagement.dto.CouponDetail;
import org.couponmanagement.dto.UserCouponClaimInfo;
import org.couponmanagement.grpc.annotation.PerformanceMonitor;
import org.couponmanagement.service.CouponApplicationResult;

import java.time.LocalDateTime;

/**
 * CouponUser entity representing user-coupon relationship and usage tracking
 */
@Entity
@Table(name = "coupon_user", indexes = {
    @Index(name = "idx_user_id", columnList = "user_id"),
    @Index(name = "idx_coupon_id", columnList = "coupon_id")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CouponUser {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(name = "user_id", nullable = false)
    private Integer userId;
    
    @Column(name = "coupon_id", nullable = false)
    private Integer couponId;
    
    @Column(name = "claimed_at")
    private LocalDateTime claimedAt;
    
    @Column(name = "expiry_date")
    private LocalDateTime expiryDate;
    
    @Column(name = "status", length = 30)
    @Enumerated(EnumType.STRING)
    @Builder.Default
    private CouponUserStatus status = CouponUserStatus.CLAIMED;
    
    @Column(name = "used_at")
    private LocalDateTime usedAt;
    
    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();
    
    @Column(name = "updated_at", nullable = false)
    @Builder.Default
    private LocalDateTime updatedAt = LocalDateTime.now();
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "coupon_id", insertable = false, updatable = false)
    private Coupon coupon;
    
    public enum CouponUserStatus {
        CLAIMED,
        USED,
    }

    @PerformanceMonitor
    public void markAsUsed() {
        this.status = CouponUserStatus.USED;
        this.usedAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    @Transient
    public boolean isUsable(LocalDateTime orderDate) {
        if (getExpiryDate() != null && getExpiryDate().isBefore(orderDate)) {
            return false;
        } else if (coupon.getExpiryDate().isBefore(orderDate)) {
            return false;
        }

        return status == CouponUserStatus.CLAIMED;
    }

    @PerformanceMonitor
    @Observed(name = "buildFromDetailAndClaimInfo", contextualName = "CouponUser.buildFromDetailAndClaimInfo")
    public static CouponUser buildFromDetailAndClaimInfo(CouponDetail couponDetail, UserCouponClaimInfo claimInfo) {
        Coupon coupon = Coupon.builder()
                .id(couponDetail.getCouponId())
                .code(couponDetail.getCouponCode())
                .collectionKeyId(couponDetail.getCollectionKeyId())
                .type(couponDetail.getType())
                .budgetId(couponDetail.getBudgetId())
                .title(couponDetail.getTitle())
                .description(couponDetail.getDescription())
                .discountConfigJson(couponDetail.getDiscountConfigJson())
                .isActive("ACTIVE".equalsIgnoreCase(couponDetail.getStatus()))
                .createdAt(couponDetail.getCreatedAt())
                .expiryDate(couponDetail.getExpiryDate())
                .build();
        return CouponUser.builder()
                .id(claimInfo.getCouponUserId())
                .userId(claimInfo.getUserId())
                .couponId(claimInfo.getCouponId())
                .claimedAt(claimInfo.getClaimedDate())
                .expiryDate(claimInfo.getExpiryDate())
                .coupon(coupon)
                .build();
    }
}
