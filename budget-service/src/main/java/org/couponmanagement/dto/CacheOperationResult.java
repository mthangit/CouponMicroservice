package org.couponmanagement.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class CacheOperationResult {
    private boolean success;
    private boolean redisUsed;
}
