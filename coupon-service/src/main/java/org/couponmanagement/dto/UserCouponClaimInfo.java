package org.couponmanagement.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;

import java.time.LocalDateTime;

@Data
@RequiredArgsConstructor
@AllArgsConstructor
@Builder
public class UserCouponClaimInfo{
    private Long couponUserId;
    private Integer userId;
    private Integer couponId;
    private LocalDateTime claimedDate;
    private LocalDateTime expiryDate;
}
