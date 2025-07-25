package org.couponmanagement.grpc;

import net.devh.boot.grpc.client.inject.GrpcClient;
import org.couponmanagement.rule.*;
import org.springframework.stereotype.Service;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class RuleServiceClient {

    @GrpcClient("rule-service")
    private RuleServiceGrpc.RuleServiceBlockingStub ruleServiceStub;

    public RuleServiceProto.EvaluateRuleResponse evaluateRuleCollections(RuleServiceProto.EvaluateRuleRequest request) {
        try {
            log.info("Calling rule service - evaluate: user={}, collections={}",
                    request.getUserId(), request.getRuleCollectionIdsList());
            return ruleServiceStub.evaluateRuleCollections(request);
        } catch (Exception e) {
            log.error("Error in evaluateRuleCollections: {}", e.getMessage(), e);
            throw e;
        }
    }

    public RuleServiceProto.GetRuleCollectionResponse getRuleCollection(RuleServiceProto.GetRuleCollectionRequest request) {
        try {
            log.info("Calling rule service - get collection: id={}", request.getCollectionId());
            return ruleServiceStub.getRuleCollection(request);
        } catch (Exception e) {
            log.error("Error in getRuleCollection: {}", e.getMessage(), e);
            throw e;
        }
    }

    public RuleServiceProto.ModifyRuleResponse modifyRule(RuleServiceProto.ModifyRuleRequest request) {
        try {
            log.info("Calling rule service - modify: id={}", request.getRuleId());
            return ruleServiceStub.modifyRule(request);
        } catch (Exception e) {
            log.error("Error in modifyRule: {}", e.getMessage(), e);
            throw e;
        }
    }

    public RuleServiceProto.ListRulesResponse listRules(RuleServiceProto.ListRulesRequest request) {
        try {
            log.info("Calling rule service - list: page={}, size={}",
                    request.getPage(), request.getSize());
            return ruleServiceStub.listRules(request);
        } catch (Exception e) {
            log.error("Error in listRules: {}", e.getMessage(), e);
            throw e;
        }
    }

    public RuleServiceProto.ListRuleCollectionsResponse listRuleCollections(RuleServiceProto.ListRuleCollectionsRequest request) {
        try {
            log.info("Calling rule service - list collections: page={}, size={}",
                    request.getPage(), request.getSize());
            return ruleServiceStub.listRuleCollections(request);
        } catch (Exception e) {
            log.error("Error in listRuleCollections: {}", e.getMessage(), e);
            throw e;
        }
    }

    public RuleServiceProto.ModifyRuleCollectionResponse modifyRuleCollection(RuleServiceProto.ModifyRuleCollectionRequest request) {
        try {
            log.info("Calling rule service - modify collection: ids={}", request.getCollectionId());
            return ruleServiceStub.modifyRuleCollection(request);
        } catch (Exception e) {
            log.error("Error in modifyRuleCollection: {}", e.getMessage(), e);
            throw e;
        }
    }
}
