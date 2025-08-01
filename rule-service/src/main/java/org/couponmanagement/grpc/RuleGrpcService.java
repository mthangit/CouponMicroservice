package org.couponmanagement.grpc;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.grpc.stub.StreamObserver;
import io.micrometer.observation.annotation.Observed;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.devh.boot.grpc.server.service.GrpcService;
import org.couponmanagement.cache.RuleCacheService;
import org.couponmanagement.engine.RuleEvaluationContext;
import org.couponmanagement.grpc.annotation.RequireAuth;
import org.couponmanagement.grpc.validation.RequestValidator;
import org.couponmanagement.rule.RuleServiceGrpc;
import org.couponmanagement.rule.RuleServiceProto;
import org.couponmanagement.service.RuleEvaluationService;
import org.couponmanagement.repository.RuleCollectionRepository;
import org.couponmanagement.repository.RuleRepository;
import org.couponmanagement.entity.RuleCollection;
import org.couponmanagement.entity.Rule;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@GrpcService
@RequiredArgsConstructor
@Slf4j
public class RuleGrpcService extends RuleServiceGrpc.RuleServiceImplBase {

    private final RuleEvaluationService ruleEvaluationService;
    private final RequestValidator validator;

    @Autowired
    private RuleCollectionRepository ruleCollectionRepository;

    @Autowired
    private RuleRepository ruleRepository;

    @Autowired
    private RuleCacheService ruleCacheService;

    @Override
    @Observed(name = "evaluate-rule-collections")
    public void evaluateRuleCollections(RuleServiceProto.EvaluateRuleRequest request,
                                       StreamObserver<RuleServiceProto.EvaluateRuleResponse> responseObserver) {
        log.info("Received gRPC request: requestId={}, userId={}, collectionIds={}, orderAmount={}",
                request.getRequestId(), request.getUserId(), request.getRuleCollectionIdsList(), request.getOrderAmount());

        try {
            validator.validateRequestId(request.getRequestId());
            validator.validateUserId(request.getUserId());
            validator.validateCollectionIds(request.getRuleCollectionIdsList());
            validator.validateOrderAmount(request.getOrderAmount());
            if (request.getDiscountAmount() != 0.0) {
                validator.validateDiscountAmount(request.getDiscountAmount());
            }
            LocalDateTime evaluationTime = parseOrderDateTime(request.getOrderDate());

            RuleEvaluationContext context = new RuleEvaluationContext(
                    request.getOrderAmount(),
                    evaluationTime,
                    request.getUserId(),
                    request.getOrderDate(),
                    request.getDiscountAmount()
            );

            List<RuleEvaluationService.RuleCollectionEvaluationResult> evalResults =
                    ruleEvaluationService.evaluateMultipleCollections(request.getRuleCollectionIdsList(), context);

            List<RuleServiceProto.RuleCollectionResult> results = new ArrayList<>();
            for (RuleEvaluationService.RuleCollectionEvaluationResult evalResult : evalResults) {
                RuleServiceProto.RuleCollectionResult grpcResult = RuleServiceProto.RuleCollectionResult.newBuilder()
                        .setRuleCollectionId(evalResult.collectionId())
                        .setIsSuccess(evalResult.success())
                        .setErrorMessage(evalResult.errorMessage() != null ? evalResult.errorMessage() : "")
                        .build();
                results.add(grpcResult);
            }

            RuleServiceProto.EvaluateRuleResponsePayload payload = RuleServiceProto.EvaluateRuleResponsePayload.newBuilder()
                    .setRequestId(request.getRequestId())
                    .setUserId(request.getUserId())
                    .setOrderAmount(request.getOrderAmount())
                    .setDiscountAmount(request.getDiscountAmount())
                    .setOrderDate(request.getOrderDate())
                    .addAllRuleCollectionResults(results)
                    .build();

            RuleServiceProto.Status status = RuleServiceProto.Status.newBuilder()
                    .setCode(RuleServiceProto.StatusCode.OK)
                    .setMessage("Rule evaluation completed successfully")
                    .build();

            RuleServiceProto.EvaluateRuleResponse response = RuleServiceProto.EvaluateRuleResponse.newBuilder()
                    .setStatus(status)
                    .setPayload(payload)
                    .build();

            responseObserver.onNext(response);
            responseObserver.onCompleted();

            log.info("gRPC request {} completed successfully with {} results",
                    request.getRequestId(), results.size());

        } catch (Exception e) {
            log.error("Error processing gRPC request: requestId={}", request.getRequestId(), e);

            RuleServiceProto.Status errorStatus = RuleServiceProto.Status.newBuilder()
                    .setCode(RuleServiceProto.StatusCode.INTERNAL)
                    .setMessage("Internal server error during rule evaluation")
                    .build();

            RuleServiceProto.Error error = RuleServiceProto.Error.newBuilder()
                    .setCode("RULE_EVALUATION_ERROR")
                    .setMessage(e.getMessage() != null ? e.getMessage() : "Unknown error occurred")
                    .build();

            RuleServiceProto.EvaluateRuleResponse errorResponse = RuleServiceProto.EvaluateRuleResponse.newBuilder()
                    .setStatus(errorStatus)
                    .setError(error)
                    .build();

            responseObserver.onNext(errorResponse);
            responseObserver.onCompleted();
        }
    }

    private LocalDateTime parseOrderDateTime(String orderDate) {
        if (orderDate == null || orderDate.trim().isEmpty()) {
            return LocalDateTime.now();
        }

        try {
            return LocalDateTime.parse(orderDate, DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        } catch (DateTimeParseException e) {
            log.warn("Error parsing order_date: {}, using current datetime", orderDate, e);
            return LocalDateTime.now();
        }
    }

    @Override
    @RequireAuth("MANAGE_RULES")
    public void getRuleCollection(RuleServiceProto.GetRuleCollectionRequest request,
                                 StreamObserver<RuleServiceProto.GetRuleCollectionResponse> responseObserver) {
        log.info("Received getRuleCollection request: collectionId={}", request.getCollectionId());

        try {
            validator.validatePositive(request.getCollectionId(), "collectionId");
            RuleCollection collection = ruleCollectionRepository.findById(request.getCollectionId()).orElse(null);

            if (collection == null) {
                RuleServiceProto.Status notFoundStatus = RuleServiceProto.Status.newBuilder()
                        .setCode(RuleServiceProto.StatusCode.NOT_FOUND)
                        .setMessage("Rule collection not found")
                        .build();

                RuleServiceProto.Error error = RuleServiceProto.Error.newBuilder()
                        .setCode("COLLECTION_NOT_FOUND")
                        .setMessage("Rule collection with ID " + request.getCollectionId() + " not found")
                        .build();

                RuleServiceProto.GetRuleCollectionResponse errorResponse =
                        RuleServiceProto.GetRuleCollectionResponse.newBuilder()
                                .setStatus(notFoundStatus)
                                .setError(error)
                                .build();

                responseObserver.onNext(errorResponse);
                responseObserver.onCompleted();
                return;
            }

            // Load rules from rule IDs
            List<RuleServiceProto.RuleInfo> ruleInfos = new ArrayList<>();
            List<Integer> ruleIdsList = collection.getRuleIdsList();
            if (!ruleIdsList.isEmpty()) {
                List<Rule> rules = ruleRepository.findByIdIn(ruleIdsList);
                for (Rule rule : rules) {
                    RuleServiceProto.RuleInfo ruleInfo = RuleServiceProto.RuleInfo.newBuilder()
                            .setRuleId(rule.getId())
                            .setDescription(rule.getDescription() != null ? rule.getDescription() : "")
                            .setType(rule.getType() != null ? rule.getType() : "")
                            .setRuleConfiguration(rule.getRuleConfiguration() != null ? rule.getRuleConfiguration() : "")
                            .setCreatedAt(rule.getCreatedAt().toString())
                            .setUpdatedAt(rule.getUpdatedAt().toString())
                            .setIsActive(rule.getIsActive())
                            .build();
                    ruleInfos.add(ruleInfo);
                }
            }

            // Build response payload
            RuleServiceProto.GetRuleCollectionResponsePayload payload =
                    RuleServiceProto.GetRuleCollectionResponsePayload.newBuilder()
                            .setCollectionId(collection.getId())
                            .setCollectionName(collection.getName() != null ? collection.getName() : "")
                            .addAllRules(ruleInfos)
                            .setCreatedAt(collection.getCreatedAt().toString())
                            .setUpdatedAt(collection.getUpdatedAt().toString())
                            .build();

            RuleServiceProto.Status successStatus = RuleServiceProto.Status.newBuilder()
                    .setCode(RuleServiceProto.StatusCode.OK)
                    .setMessage("Rule collection retrieved successfully")
                    .build();

            RuleServiceProto.GetRuleCollectionResponse response =
                    RuleServiceProto.GetRuleCollectionResponse.newBuilder()
                            .setStatus(successStatus)
                            .setPayload(payload)
                            .build();

            log.info("Successfully retrieved rule collection: collectionId={}, name={}, rulesCount={}",
                    collection.getId(), collection.getName(), ruleInfos.size());

            responseObserver.onNext(response);
            responseObserver.onCompleted();

        } catch (Exception e) {
            log.error("Error retrieving rule collection: collectionId={}", request.getCollectionId(), e);

            RuleServiceProto.Status errorStatus = RuleServiceProto.Status.newBuilder()
                    .setCode(RuleServiceProto.StatusCode.INTERNAL)
                    .setMessage("Internal server error while retrieving rule collection")
                    .build();

            RuleServiceProto.Error error = RuleServiceProto.Error.newBuilder()
                    .setCode("RULE_COLLECTION_RETRIEVAL_ERROR")
                    .setMessage(e.getMessage() != null ? e.getMessage() : "Unknown error occurred")
                    .build();

            RuleServiceProto.GetRuleCollectionResponse errorResponse =
                    RuleServiceProto.GetRuleCollectionResponse.newBuilder()
                            .setStatus(errorStatus)
                            .setError(error)
                            .build();

            responseObserver.onNext(errorResponse);
            responseObserver.onCompleted();
        }
    }


    @Override
    @RequireAuth("MANAGE_RULES")
    @Transactional
    public void modifyRule(
            RuleServiceProto.ModifyRuleRequest request,
            StreamObserver<RuleServiceProto.ModifyRuleResponse> responseObserver) {

        try {
            log.info("Admin modifying rule: ruleId={}", request.getRuleId());

            validator.validatePositive(request.getRuleId(), "ruleId");
            if (!request.getDescription().isEmpty()) {
                validator.validateNotBlank(request.getDescription(), "description");
            }

            Rule rule = ruleRepository.findById(request.getRuleId()).orElse(null);
            if (rule == null) {
                var notFoundStatus = RuleServiceProto.Status.newBuilder()
                        .setCode(RuleServiceProto.StatusCode.NOT_FOUND)
                        .setMessage("Rule not found")
                        .build();
                var error = RuleServiceProto.Error.newBuilder()
                        .setCode("RULE_NOT_FOUND")
                        .setMessage("Rule with ID " + request.getRuleId() + " not found")
                        .build();
                var errorResponse = RuleServiceProto.ModifyRuleResponse.newBuilder()
                        .setStatus(notFoundStatus)
                        .setError(error)
                        .build();
                responseObserver.onNext(errorResponse);
                responseObserver.onCompleted();
                return;
            }

            // Update fields using custom query
            String ruleConfigJson = null;
            if (request.hasRuleConfig() && request.getRuleConfig().getConfigCount() > 0) {
                // Convert protobuf Struct to JSON string
                try {
                    var configMap = request.getRuleConfig().getConfigMap();
                    // Convert protobuf Values to plain Java objects to avoid circular reference
                    Map<String, Object> plainConfigMap = new HashMap<>();
                    for (Map.Entry<String, com.google.protobuf.Value> entry : configMap.entrySet()) {
                        plainConfigMap.put(entry.getKey(), convertProtobufValueToJavaObject(entry.getValue()));
                    }

                    ObjectMapper objectMapper = new ObjectMapper();
                    ruleConfigJson = objectMapper.writeValueAsString(plainConfigMap);
                } catch (Exception e) {
                    log.error("Error converting rule config to JSON: {}", e.getMessage());
                    var errorStatus = RuleServiceProto.Status.newBuilder()
                            .setCode(RuleServiceProto.StatusCode.INVALID_ARGUMENT)
                            .setMessage("Invalid rule configuration format")
                            .build();
                    var error = RuleServiceProto.Error.newBuilder()
                            .setCode("INVALID_CONFIG")
                            .setMessage("Failed to process rule configuration: " + e.getMessage())
                            .build();
                    var errorResponse = RuleServiceProto.ModifyRuleResponse.newBuilder()
                            .setStatus(errorStatus)
                            .setError(error)
                            .build();
                    responseObserver.onNext(errorResponse);
                    responseObserver.onCompleted();
                    return;
                }
            }

            // Use custom query to update only non-null fields
            String description = request.getDescription().isEmpty() ? null : request.getDescription();
            String ruleType = request.getRuleType().isEmpty() ? null : request.getRuleType();

            int updatedRows = ruleRepository.updateRuleSelectively(
                    request.getRuleId(),
                    description,
                    ruleType,
                    ruleConfigJson,
                    request.getIsActive(),
                    LocalDateTime.now()
            );

            if (updatedRows == 0) {
                var errorStatus = RuleServiceProto.Status.newBuilder()
                        .setCode(RuleServiceProto.StatusCode.INTERNAL)
                        .setMessage("Failed to update rule")
                        .build();
                var error = RuleServiceProto.Error.newBuilder()
                        .setCode("UPDATE_FAILED")
                        .setMessage("No rows were updated for rule ID " + request.getRuleId())
                        .build();
                var errorResponse = RuleServiceProto.ModifyRuleResponse.newBuilder()
                        .setStatus(errorStatus)
                        .setError(error)
                        .build();
                responseObserver.onNext(errorResponse);
                responseObserver.onCompleted();
                return;
            }

            Rule updatedRule = ruleRepository.findById(request.getRuleId()).orElse(null);
            if (updatedRule == null) {
                log.error("Rule not found after update: {}", request.getRuleId());
                var errorStatus = RuleServiceProto.Status.newBuilder()
                        .setCode(RuleServiceProto.StatusCode.INTERNAL)
                        .setMessage("Rule not found after update")
                        .build();
                var errorResponse = RuleServiceProto.ModifyRuleResponse.newBuilder()
                        .setStatus(errorStatus)
                        .build();
                responseObserver.onNext(errorResponse);
                responseObserver.onCompleted();
                return;
            }

            // Cache updated rule config
            ruleCacheService.cacheRuleConfig(updatedRule.getId(),
                    RuleCacheService.RuleConfigCacheInfo.fromRule(updatedRule));

            // Build response
            var response = RuleServiceProto.ModifyRuleResponse.newBuilder()
                    .setStatus(RuleServiceProto.Status.newBuilder()
                            .setCode(RuleServiceProto.StatusCode.OK)
                            .setMessage("Rule updated successfully")
                            .build())
                    .setPayload(RuleServiceProto.RuleDetailsPayload.newBuilder()
                            .setRuleId(updatedRule.getId())
                            .setDescription(updatedRule.getDescription() != null ? updatedRule.getDescription() : "")
                            .setIsActive(updatedRule.getIsActive() != null ? updatedRule.getIsActive() : false)
                            .setRuleType(updatedRule.getType() != null ? updatedRule.getType() : "")
                            .setRuleConfig(
                                RuleServiceProto.RuleConfiguration.newBuilder()
                                    .putAllConfig(request.getRuleConfig().getConfigMap())
                                    .build()
                            )
                            .setCreatedAt(updatedRule.getCreatedAt().toString())
                            .setUpdatedAt(updatedRule.getUpdatedAt().toString())
                            .build())
                    .build();

            responseObserver.onNext(response);
            responseObserver.onCompleted();

            log.info("Rule updated successfully: ruleId={}", request.getRuleId());

        } catch (Exception e) {
            log.error("Error updating rule: {}", e.getMessage(), e);
            var errorResponse = RuleServiceProto.ModifyRuleResponse.newBuilder()
                    .setStatus(RuleServiceProto.Status.newBuilder()
                            .setCode(RuleServiceProto.StatusCode.INTERNAL)
                            .setMessage("Internal server error")
                            .build())
                    .setError(RuleServiceProto.Error.newBuilder()
                            .setCode("UPDATE_RULE_ERROR")
                            .setMessage(e.getMessage())
                            .build())
                    .build();

            responseObserver.onNext(errorResponse);
            responseObserver.onCompleted();
        }
    }

    @Override
    @RequireAuth("MANAGE_RULES")
    public void listRules(
            RuleServiceProto.ListRulesRequest request,
            StreamObserver<RuleServiceProto.ListRulesResponse> responseObserver) {

        try {
            log.info("Admin listing rules: page={}, size={}, status={}",
                    request.getPage(), request.getSize(), request.getStatus());

            validator.validateNonNegative(request.getPage(), "page");
            validator.validatePositive(request.getSize(), "size");

            int page = Math.max(0, request.getPage());
            int size = Math.min(Math.max(1, request.getSize()), 100);

            org.springframework.data.domain.Pageable pageable = org.springframework.data.domain.PageRequest.of(page, size);
            org.springframework.data.domain.Page<org.couponmanagement.entity.Rule> rulePage = ruleRepository.findAll(pageable);
            List<org.couponmanagement.entity.Rule> rules = rulePage.getContent();

            List<RuleServiceProto.RuleSummary> ruleSummaries = new ArrayList<>();
            for (org.couponmanagement.entity.Rule rule : rules) {
                ruleSummaries.add(RuleServiceProto.RuleSummary.newBuilder()
                        .setRuleId(rule.getId())
                        .setName(rule.getType())
                        .setDescription(rule.getDescription() != null ? rule.getDescription() : "")
                        .setIsActive(rule.getIsActive() != null ? rule.getIsActive() : false)
                        .setRuleType(rule.getType() != null ? rule.getType() : "")
                        .setRuleConfiguration(rule.getRuleConfiguration())
                        .setCreatedAt(rule.getCreatedAt() != null ? rule.getCreatedAt().toString() : "")
                        .setUpdatedAt(rule.getUpdatedAt() != null ? rule.getUpdatedAt().toString() : "")
                        .build());
            }

            var response = RuleServiceProto.ListRulesResponse.newBuilder()
                    .setStatus(RuleServiceProto.Status.newBuilder()
                            .setCode(RuleServiceProto.StatusCode.OK)
                            .setMessage("Rules listed successfully")
                            .build())
                    .setPayload(RuleServiceProto.ListRulesResponsePayload.newBuilder()
                            .addAllRules(ruleSummaries)
                            .setTotalCount(rulePage.getTotalElements())
                            .setPage(page)
                            .setSize(size)
                            .build())
                    .build();

            responseObserver.onNext(response);
            responseObserver.onCompleted();
            log.info("Rules listed successfully: count={}, total={}", ruleSummaries.size(), rulePage.getTotalElements());
        } catch (Exception e) {
            log.error("Error listing rules: {}", e.getMessage(), e);
            var errorResponse = RuleServiceProto.ListRulesResponse.newBuilder()
                    .setStatus(RuleServiceProto.Status.newBuilder()
                            .setCode(RuleServiceProto.StatusCode.INTERNAL)
                            .setMessage("Internal server error")
                            .build())
                    .setError(RuleServiceProto.Error.newBuilder()
                            .setCode("LIST_RULES_ERROR")
                            .setMessage(e.getMessage())
                            .build())
                    .build();
            responseObserver.onNext(errorResponse);
            responseObserver.onCompleted();
        }
    }

    @Override
    @RequireAuth("MANAGE_RULES")
    public void listRuleCollections(
            RuleServiceProto.ListRuleCollectionsRequest request,
            StreamObserver<RuleServiceProto.ListRuleCollectionsResponse> responseObserver) {
        try {
            int page = Math.max(0, request.getPage());
            int size = Math.min(Math.max(1, request.getSize()), 100);
            org.springframework.data.domain.Pageable pageable = org.springframework.data.domain.PageRequest.of(page, size);
            org.springframework.data.domain.Page<RuleCollection> collectionPage = ruleCollectionRepository.findAll(pageable);
            List<RuleCollection> collections = collectionPage.getContent();

            List<RuleServiceProto.RuleCollectionSummary> summaries = new ArrayList<>();
            for (RuleCollection rc : collections) {
                summaries.add(RuleServiceProto.RuleCollectionSummary.newBuilder()
                        .addCollectionId(rc.getId())
                        .setName(rc.getName())
                        .setDescription("")
                        .setIsActive(true)
                        .setCreatedAt(rc.getCreatedAt().toString())
                        .setUpdatedAt(rc.getUpdatedAt().toString())
                        .build());
            }

            var response = RuleServiceProto.ListRuleCollectionsResponse.newBuilder()
                    .setStatus(RuleServiceProto.Status.newBuilder()
                            .setCode(RuleServiceProto.StatusCode.OK)
                            .setMessage("Collections listed successfully")
                            .build())
                    .setPayload(RuleServiceProto.ListRuleCollectionsResponsePayload.newBuilder()
                            .addAllCollections(summaries)
                            .setTotalCount(collectionPage.getTotalElements())
                            .setPage(page)
                            .setSize(size)
                            .build())
                    .build();
            responseObserver.onNext(response);
            responseObserver.onCompleted();
        } catch (Exception e) {
            var errorResponse = RuleServiceProto.ListRuleCollectionsResponse.newBuilder()
                    .setStatus(RuleServiceProto.Status.newBuilder()
                            .setCode(RuleServiceProto.StatusCode.INTERNAL)
                            .setMessage("Internal server error")
                            .build())
                    .setError(RuleServiceProto.Error.newBuilder()
                            .setCode("LIST_COLLECTIONS_ERROR")
                            .setMessage(e.getMessage())
                            .build())
                    .build();
            responseObserver.onNext(errorResponse);
            responseObserver.onCompleted();
        }
    }

    @Override
    @RequireAuth("MANAGE_RULES")
    @Transactional
    public void modifyRuleCollection(
            RuleServiceProto.ModifyRuleCollectionRequest request,
            StreamObserver<RuleServiceProto.ModifyRuleCollectionResponse> responseObserver) {
        try {
            Integer collectionId = request.getCollectionId();
            RuleCollection collection = ruleCollectionRepository.findById(collectionId).orElse(null);
            if (collection == null) {
                var notFoundStatus = RuleServiceProto.Status.newBuilder()
                        .setCode(RuleServiceProto.StatusCode.NOT_FOUND)
                        .setMessage("Rule collection not found")
                        .build();
                var error = RuleServiceProto.Error.newBuilder()
                        .setCode("COLLECTION_NOT_FOUND")
                        .setMessage("Rule collection with ID " + collectionId + " not found")
                        .build();
                var errorResponse = RuleServiceProto.ModifyRuleCollectionResponse.newBuilder()
                        .setStatus(notFoundStatus)
                        .setError(error)
                        .build();
                responseObserver.onNext(errorResponse);
                responseObserver.onCompleted();
                return;
            }
            collection.setName(request.getName());
            collection.setRuleIdsList(request.getRuleIdsList());
            collection.setUpdatedAt(LocalDateTime.now());

            ruleCollectionRepository.save(collection);
            ruleCacheService.cacheRuleCollection(collectionId, RuleCacheService.RuleCollectionCacheInfo.fromRuleCollection(collection));

            if (!request.getRuleIdsList().isEmpty()) {
                try {
                    List<Rule> updatedRules = ruleRepository.findByIdIn(request.getRuleIdsList());

                    List<Rule> activeRules = updatedRules.stream()
                            .filter(rule -> Boolean.TRUE.equals(rule.getIsActive()))
                            .toList();

                    RuleCacheService.RuleCollectionWithRulesCacheInfo updatedCacheInfo =
                        RuleCacheService.RuleCollectionWithRulesCacheInfo.fromRuleCollectionWithRules(collection, activeRules);

                    ruleCacheService.cacheRuleCollectionWithRules(collectionId, updatedCacheInfo);

                    log.info("Updated RuleCollectionWithRulesCacheInfo for collection: {}, activeRules: {}",
                            collectionId, activeRules.size());
                } catch (Exception e) {
                    log.error("Error updating RuleCollectionWithRulesCacheInfo for collection {}: {}",
                            collectionId, e.getMessage(), e);
                }
            }
            var response = RuleServiceProto.ModifyRuleCollectionResponse.newBuilder()
                    .setStatus(RuleServiceProto.Status.newBuilder()
                            .setCode(RuleServiceProto.StatusCode.OK)
                            .setMessage("Rule collection updated successfully")
                            .build())
                    .setPayload(RuleServiceProto.RuleCollectionDetailsPayload.newBuilder()
                            .setCollectionId(collection.getId())
                            .setName(collection.getName())
                            .addAllRuleIds(collection.getRuleIdsList())
                            .setCreatedAt(collection.getCreatedAt().toString())
                            .setUpdatedAt(collection.getUpdatedAt().toString())
                            .build())
                    .build();
            responseObserver.onNext(response);
            responseObserver.onCompleted();
        } catch (Exception e) {
            var errorResponse = RuleServiceProto.ModifyRuleCollectionResponse.newBuilder()
                    .setStatus(RuleServiceProto.Status.newBuilder()
                            .setCode(RuleServiceProto.StatusCode.INTERNAL)
                            .setMessage("Internal server error")
                            .build())
                    .setError(RuleServiceProto.Error.newBuilder()
                            .setCode("MODIFY_COLLECTION_ERROR")
                            .setMessage(e.getMessage())
                            .build())
                    .build();
            responseObserver.onNext(errorResponse);
            responseObserver.onCompleted();
        }
    }

    private Object convertProtobufValueToJavaObject(com.google.protobuf.Value value) {
        switch (value.getKindCase()) {
            case NULL_VALUE:
                return null;
            case NUMBER_VALUE:
                return value.getNumberValue();
            case STRING_VALUE:
                return value.getStringValue();
            case BOOL_VALUE:
                return value.getBoolValue();
            case STRUCT_VALUE:
                // Handle nested Structs by converting to Map
                Map<String, Object> structMap = new HashMap<>();
                for (Map.Entry<String, com.google.protobuf.Value> entry : value.getStructValue().getFieldsMap().entrySet()) {
                    structMap.put(entry.getKey(), convertProtobufValueToJavaObject(entry.getValue()));
                }
                return structMap;
            case LIST_VALUE:
                // Handle Lists by converting each value
                List<Object> list = new ArrayList<>();
                for (com.google.protobuf.Value listValue : value.getListValue().getValuesList()) {
                    list.add(convertProtobufValueToJavaObject(listValue));
                }
                return list;
            case KIND_NOT_SET:
            default:
                return null;
        }
    }
}
