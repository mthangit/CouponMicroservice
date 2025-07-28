package org.couponmanagement.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.List;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ModifyRuleCollectionResponse {
    private Integer collectionId;
    private String name;
    private List<Integer> ruleIds;
    private String createdAt;
    private String updatedAt;
}
