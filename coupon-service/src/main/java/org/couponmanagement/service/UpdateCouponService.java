package org.couponmanagement.service;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.couponmanagement.dto.UpdateCouponEvent;
import org.couponmanagement.repository.CouponRepository;
import org.couponmanagement.repository.CouponUserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Slf4j
@AllArgsConstructor
public class UpdateCouponService {
    private final CouponUserRepository couponUserRepository;

    @Transactional
    public void updateCouponUsageStatus(UpdateCouponEvent event) {
        try {
            int updated = couponUserRepository.markCouponUsed(
                    event.getUserId(),
                    event.getCouponId(),
                    event.getUsedAt(),
                    event.getNewStatus()
            );
            if (updated > 0) {
                log.info("Updated coupon usage status for couponId: {}, userId: {} to status: {}",
                        event.getCouponId(), event.getUserId(), event.getNewStatus());
            } else {
                log.warn("No coupon usage record found to update for couponId: {}, userId: {}",
                        event.getCouponId(), event.getUserId());
            }
        } catch (Exception e) {
            log.error("Error updating coupon usage status for couponId: {}, userId: {}: {}", event.getCouponId(), event.getUserId(), e.getMessage(), e);
            throw e;
        }
    }
}
