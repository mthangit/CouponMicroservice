package org.couponmanagement.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserCouponIds {
    private Map<Integer, UserCouponClaimInfo> userCouponInfo;
    private LocalDateTime cacheTimestamp;
    private Integer totalCount;

    public static UserCouponIds of(Map<Integer, UserCouponClaimInfo> userCouponInfo) {
        return new UserCouponIds(userCouponInfo, LocalDateTime.now(), userCouponInfo.size());
    }

    public List<Integer> getCouponIds(){
        return new ArrayList<>(userCouponInfo.keySet());
    }

    public List<UserCouponClaimInfo> getCouponDetails(){
        return new ArrayList<>(userCouponInfo.values());
    }

    public void removeUserClaimInfo(Integer couponID){
        UserCouponClaimInfo removed = userCouponInfo.remove(couponID);
    }

    public UserCouponClaimInfo getCouponClaimInfo(Integer couponId){
        return userCouponInfo.get(couponId);
    }

}
