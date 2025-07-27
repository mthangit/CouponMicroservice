package org.couponmanagement.dto.response;

import lombok.Data;
import lombok.Builder;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import java.util.List;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class GetUserCouponsResponse {
    private List<UserCouponResponse> userCoupons;
    private Long totalCount;
    private Integer page;
    private Integer size;
}
