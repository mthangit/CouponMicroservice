package org.couponmanagement.dto.response;

import org.couponmanagement.rule.RuleServiceProto;

import java.util.List;

public class RuleCollectionDTO {

    public record ListRuleCollectionsDto(
            List<RuleCollectionSummaryDto> collections,
            long totalCount,
            int page,
            int size
    ) {
        public static ListRuleCollectionsDto fromProto(RuleServiceProto.ListRuleCollectionsResponsePayload proto) {
            List<RuleCollectionSummaryDto> list = proto.getCollectionsList().stream()
                    .map(RuleCollectionSummaryDto::fromProto)
                    .toList();

            return new ListRuleCollectionsDto(
                    list,
                    proto.getTotalCount(),
                    proto.getPage(),
                    proto.getSize()
            );
        }
    }

    public record RuleCollectionSummaryDto(
            Integer collectionId,
            List<Integer> ruleIds,
            String name,
            String description,
            boolean isActive,
            String createdAt,
            String updatedAt
    ) {
        public static RuleCollectionSummaryDto fromProto(RuleServiceProto.RuleCollectionSummary proto) {
            return new RuleCollectionSummaryDto(
                    proto.getCollectionId(),
                    proto.getRuleIdsList(),
                    proto.getName(),
                    proto.getDescription(),
                    proto.getIsActive(),
                    proto.getCreatedAt(),
                    proto.getUpdatedAt()
            );
        }
    }
}
