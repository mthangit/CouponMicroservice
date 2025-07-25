package org.couponmanagement.grpc;

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
import java.util.List;

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

            // Fetch rule from DB
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

            // Update fields
            rule.setDescription(request.getDescription());
            rule.setType(request.getRuleType());
            rule.setRuleConfiguration(request.getRuleConfig().getConfigMap().toString());
            rule.setUpdatedAt(LocalDateTime.now());
            rule.setIsActive(request.getIsActive());

            ruleRepository.save(rule);

            RuleCollection ruleCollection;
            List<RuleCollection> collections = ruleCollectionRepository.findAll();
            ruleCollection = collections.stream().filter(rc -> rc.getRuleIdsList().contains(rule.getId())).findFirst().orElse(null);

            if (ruleCollection != null) {
                // Update RuleCollectionWithRulesCacheInfo in cache
                Integer collectionId = ruleCollection.getId();
                var cachedOpt = ruleCacheService.getCachedRuleCollectionWithRules(collectionId);
                RuleCacheService.RuleCollectionWithRulesCacheInfo cacheInfo = cachedOpt.orElseGet(() ->
                    RuleCacheService.RuleCollectionWithRulesCacheInfo.fromRuleCollectionWithRules(ruleCollection, new ArrayList<>())
                );
                List<RuleCacheService.RuleConfigCacheInfo> rules = cacheInfo.getRules();
                if (rules == null) rules = new ArrayList<>();
                // Remove old rule if exists
                rules.removeIf(r -> r.getRuleId().equals(rule.getId()));
                if (Boolean.TRUE.equals(rule.getIsActive())) {
                    // Add updated rule if still active
                    RuleCacheService.RuleConfigCacheInfo updatedRuleCache = RuleCacheService.RuleConfigCacheInfo.fromRule(rule);
                    rules.add(updatedRuleCache);
                }
                cacheInfo.setRules(rules);
                ruleCacheService.cacheRuleCollectionWithRules(collectionId, cacheInfo);
            }

            // Update cache for rule config
            ruleCacheService.cacheRuleConfig(rule.getId(),
                    RuleCacheService.RuleConfigCacheInfo.fromRule(rule));

            // Build response
            var response = RuleServiceProto.ModifyRuleResponse.newBuilder()
                    .setStatus(RuleServiceProto.Status.newBuilder()
                            .setCode(RuleServiceProto.StatusCode.OK)
                            .setMessage("Rule updated successfully")
                            .build())
                    .setPayload(RuleServiceProto.RuleDetailsPayload.newBuilder()
                            .setRuleId(rule.getId())
                            .setDescription(rule.getDescription() != null ? rule.getDescription() : "")
                            .setIsActive(rule.getIsActive() != null ? rule.getIsActive() : false)
                            .setRuleType(rule.getType() != null ? rule.getType() : "")
                            .setRuleConfig(
                                RuleServiceProto.RuleConfiguration.newBuilder()
                                    .putAllConfig(request.getRuleConfig().getConfigMap())
                                    .build()
                            )
                            .setCreatedAt(rule.getCreatedAt().toString())
                            .setUpdatedAt(rule.getUpdatedAt().toString())
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
            // Update fields
            collection.setName(request.getName());
            collection.setRuleIdsList(request.getRuleIdsList());
            collection.setUpdatedAt(LocalDateTime.now());
            // Save
            ruleCollectionRepository.save(collection);
            // Cache logic
            if (!request.getIsActive()) {
                ruleCacheService.invalidateRuleCollectionCache(collectionId);
            } else {
                ruleCacheService.cacheRuleCollection(collectionId, RuleCacheService.RuleCollectionCacheInfo.fromRuleCollection(collection));
            }
            // Build response
            var response = RuleServiceProto.ModifyRuleCollectionResponse.newBuilder()
                    .setStatus(RuleServiceProto.Status.newBuilder()
                            .setCode(RuleServiceProto.StatusCode.OK)
                            .setMessage("Rule collection updated successfully")
                            .build())
                    .setPayload(RuleServiceProto.RuleCollectionDetailsPayload.newBuilder()
                            .setCollectionId(collection.getId())
                            .setName(collection.getName())
                            .setDescription("")
                            .setIsActive(request.getIsActive())
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
}
