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
public class ListCouponsResponse {
    private List<CouponSummaryResponse> coupons;
    private int page;
    private int size;
    private long totalElements;
    private int totalPages;
}

