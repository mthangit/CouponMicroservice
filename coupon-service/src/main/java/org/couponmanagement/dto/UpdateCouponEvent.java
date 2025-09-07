package org.couponmanagement.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.couponmanagement.entity.CouponUser.CouponUserStatus;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UpdateCouponEvent {
    private Integer couponId;
    private Integer userId;
    private CouponUserStatus newStatus;
    private LocalDateTime usedAt;
    private LocalDateTime updatedAt;
}
