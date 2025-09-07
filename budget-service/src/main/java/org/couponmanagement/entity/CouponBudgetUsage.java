package org.couponmanagement.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Table(name = "coupon_budget_usage")
@Entity
@Data
@NoArgsConstructor
@AllArgsConstructor
@Slf4j
@Builder
public class CouponBudgetUsage {

    @Id
    private String id;

    @Column(name = "coupon_user_id", nullable = false)
    private Long couponUserId;

    @Column(name = "budget_id", nullable = false)
    private Integer budgetId;

    @Column(name = "coupon_id", nullable = false)
    private Integer couponId;

    @Column(name = "user_id", nullable = false)
    private Integer userId;

    @Column(name = "amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    private RegisterStatus status;

    @Column(name = "usage_time", nullable = false)
    private LocalDateTime usage_time;

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at", nullable = false)
    @Builder.Default
    private LocalDateTime updatedAt = LocalDateTime.now();
}
