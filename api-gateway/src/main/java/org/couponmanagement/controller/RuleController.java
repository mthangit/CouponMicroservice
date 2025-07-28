package org.couponmanagement.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.protobuf.ListValue;
import com.google.protobuf.NullValue;
import com.google.protobuf.Struct;
import com.google.protobuf.Value;
import jakarta.validation.Valid;
import org.couponmanagement.dto.ModifyRuleCollectionRequest;
import org.couponmanagement.dto.ModifyRuleRequest;
import org.couponmanagement.dto.response.*;
import org.couponmanagement.grpc.RuleServiceClient;
import org.couponmanagement.grpc.annotation.PerformanceMonitor;
import org.couponmanagement.rule.*;
import org.couponmanagement.security.RequireAdmin;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import lombok.extern.slf4j.Slf4j;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.JsonProcessingException;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/rules")
@Slf4j
public class RuleController {

    @Autowired
    private RuleServiceClient ruleServiceClient;

    @GetMapping("/collections/{collectionId}")
    @RequireAdmin
    public ResponseEntity<?> getRuleCollection(@PathVariable int collectionId) {
        try {
            log.info("Get rule collection: id={}", collectionId);

            RuleServiceProto.GetRuleCollectionRequest request = RuleServiceProto.GetRuleCollectionRequest.newBuilder()
                    .setRequestId(UUID.randomUUID().toString())
                    .setCollectionId(collectionId)
                    .build();

            RuleServiceProto.GetRuleCollectionResponse response = ruleServiceClient.getRuleCollection(request);

            if (response.getStatus().getCode() == RuleServiceProto.StatusCode.OK) {
                return ResponseEntity.ok(response.getPayload());
            } else {
                return ResponseEntity.badRequest().body(response.getError());
            }
        } catch (Exception e) {
            log.error("Error in get rule collection: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().body("Internal server error");
        }
    }

    @PutMapping("/{ruleId}")
    @RequireAdmin
    public ResponseEntity<?> modifyRule(@PathVariable int ruleId, @Valid @RequestBody ModifyRuleRequest request) {
        try {
            log.info("Modify rule: id={}", ruleId);
            ObjectMapper objectMapper = new ObjectMapper();

            RuleServiceProto.ModifyRuleRequest.Builder protoRequestBuilder = RuleServiceProto.ModifyRuleRequest.newBuilder()
                .setRuleId(ruleId)
                .setRequestId(request.getRequestId() != null ? request.getRequestId() : UUID.randomUUID().toString());

            if (request.getDescription() != null) {
                protoRequestBuilder.setDescription(request.getDescription());
            }
            if (request.getIsActive() != null) {
                protoRequestBuilder.setIsActive(request.getIsActive());
            }
            if (request.getRuleType() != null) {
                protoRequestBuilder.setRuleType(request.getRuleType());
            }

            if (request.getRuleConfig() != null) {
                try {
                    JsonNode configNode = objectMapper.valueToTree(request.getRuleConfig());
                    Struct.Builder configBuilder = Struct.newBuilder();

                    configNode.properties().forEach(entry -> {
                        configBuilder.putFields(entry.getKey(), convertJsonNodeToValue(entry.getValue()));
                    });

                    protoRequestBuilder.setRuleConfig(
                        RuleServiceProto.RuleConfiguration.newBuilder()
                            .putAllConfig(configBuilder.build().getFieldsMap())
                            .build()
                    );
                } catch (Exception e) {
                    log.error("Error serializing ruleConfig: {}", e.getMessage(), e);
                    ErrorResponse errorResponse = ErrorResponse.builder()
                            .code("INVALID_CONFIG")
                            .message("Invalid rule configuration format")
                            .build();
                    return ResponseEntity.badRequest().body(errorResponse);
                }
            }

            RuleServiceProto.ModifyRuleResponse response = ruleServiceClient.modifyRule(protoRequestBuilder.build());

            if (response.getStatus().getCode() == RuleServiceProto.StatusCode.OK) {
                RuleServiceProto.RuleDetailsPayload payload = response.getPayload();

                String configJson = "{}";
                if (payload.hasRuleConfig() && payload.getRuleConfig().getConfigCount() > 0) {
                    try {
                        Map<String, Object> configMap = new HashMap<>();
                        for (Map.Entry<String, com.google.protobuf.Value> entry : payload.getRuleConfig().getConfigMap().entrySet()) {
                            configMap.put(entry.getKey(), convertProtobufValueToJavaObject(entry.getValue()));
                        }
                        configJson = objectMapper.writeValueAsString(configMap);
                    } catch (Exception e) {
                        log.warn("Error converting config to JSON for response: {}", e.getMessage());
                        configJson = "{}";
                    }
                }

                ModifyRuleResponse modifyRuleResponse = ModifyRuleResponse.builder()
                        .ruleId(payload.getRuleId())
                        .description(payload.getDescription())
                        .isActive(payload.getIsActive())
                        .ruleType(payload.getRuleType())
                        .ruleConfiguration(configJson)
                        .createdAt(payload.getCreatedAt())
                        .updatedAt(payload.getUpdatedAt())
                        .build();

                return ResponseEntity.ok(modifyRuleResponse);
            } else {
                ErrorResponse errorResponse = ErrorResponse.builder()
                        .code(response.getError().getCode())
                        .message(response.getError().getMessage())
                        .details(response.getError().getDetailsMap())
                        .build();
                return ResponseEntity.badRequest().body(errorResponse);
            }
        } catch (Exception e) {
            log.error("Error in modify rule: {}", e.getMessage(), e);
            ErrorResponse errorResponse = ErrorResponse.builder()
                    .code("INTERNAL_ERROR")
                    .message("Internal server error")
                    .build();
            return ResponseEntity.internalServerError().body(errorResponse);
        }
    }

    @GetMapping
    @RequireAdmin
    public ResponseEntity<?> listRules(
            @RequestParam(name = "page",defaultValue = "0") int page,
            @RequestParam(name = "size",defaultValue = "10") int size,
            @RequestParam(name = "status",required = false) String status) {
        try {
            log.info("List rules: page={}, size={}, status={}", page, size, status);

            RuleServiceProto.ListRulesRequest.Builder requestBuilder = RuleServiceProto.ListRulesRequest.newBuilder()
                    .setRequestId(UUID.randomUUID().toString())
                    .setPage(page)
                    .setSize(size);

            if (status != null && !status.isEmpty()) {
                requestBuilder.setStatus(status);
            }

            RuleServiceProto.ListRulesResponse response = ruleServiceClient.listRules(requestBuilder.build());
            ObjectMapper objectMapper = new ObjectMapper();

            if (response.getStatus().getCode() == RuleServiceProto.StatusCode.OK) {
                // Map protobuf response to new DTO
                List<RuleSummaryResponse> ruleList = response.getPayload().getRulesList().stream().map(protoRule -> {
                    String configJson = protoRule.getRuleConfiguration();
                    try {
                        Object configObj = objectMapper.readValue(configJson, Object.class);
                        configJson = objectMapper.writeValueAsString(configObj);
                    } catch (JsonProcessingException ignored) {
                    }
                    return RuleSummaryResponse.builder()
                        .ruleId(protoRule.getRuleId())
                        .name(protoRule.getName())
                        .description(protoRule.getDescription())
                        .isActive(protoRule.getIsActive())
                        .createdAt(protoRule.getCreatedAt())
                        .updatedAt(protoRule.getUpdatedAt())
                        .ruleType(protoRule.getRuleType())
                        .ruleConfiguration(configJson)
                        .build();
                }).toList();

                ListRulesResponse listRulesResponse = ListRulesResponse.builder()
                    .rules(ruleList)
                    .page(response.getPayload().getPage())
                    .size(response.getPayload().getSize())
                    .totalElements(response.getPayload().getTotalCount())
                    .totalPages((int) ((response.getPayload().getTotalCount()/size) + 1))
                    .build();

                return ResponseEntity.ok(listRulesResponse);
            } else {
                ErrorResponse errorResponse = ErrorResponse.builder()
                        .code(response.getError().getCode())
                        .message(response.getError().getMessage())
                        .details(response.getError().getDetailsMap())
                        .build();

                return ResponseEntity.badRequest().body(errorResponse);
            }
        } catch (Exception e) {
            log.error("Error in list rules: {}", e.getMessage(), e);
            ErrorResponse errorResponse = ErrorResponse.builder()
                    .code("INTERNAL_ERROR")
                    .message("Internal server error")
                    .build();
            return ResponseEntity.internalServerError().body(errorResponse);
        }
    }

    @GetMapping("/collections")
    @RequireAdmin
    @PerformanceMonitor
    public ResponseEntity<?> listRuleCollections(
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size",defaultValue = "10") int size,
            @RequestParam(name = "status",required = false) String status) {
        try {
            log.info("List rule collections: page={}, size={}, status={}", page, size, status);
            RuleServiceProto.ListRuleCollectionsRequest.Builder requestBuilder = RuleServiceProto.ListRuleCollectionsRequest.newBuilder()
                    .setRequestId(UUID.randomUUID().toString())
                    .setPage(page)
                    .setSize(size);
            if (status != null && !status.isEmpty()) {
                requestBuilder.setStatus(status);
            }
            RuleServiceProto.ListRuleCollectionsResponse response = ruleServiceClient.listRuleCollections(requestBuilder.build());
            if (response.getStatus().getCode() == RuleServiceProto.StatusCode.OK) {
                RuleCollectionDTO.ListRuleCollectionsDto dto = RuleCollectionDTO.ListRuleCollectionsDto.fromProto(response.getPayload());
                return ResponseEntity.ok(ApiResponse.success(dto));
            } else {
                return ResponseEntity.badRequest().body(ApiResponse.error(response.getStatus().getMessage(), response.getStatus().getCode().name()));
            }
        } catch (Exception e) {
            log.error("Error in list rule collections: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().body("Internal server error");
        }
    }

    @PutMapping("/collections")
    @RequireAdmin
    public ResponseEntity<?> modifyRuleCollection(@Valid @RequestBody ModifyRuleCollectionRequest request) {
        try {
            log.info("Modify rule collection: id={}", request.getCollectionId());
            RuleServiceProto.ModifyRuleCollectionRequest.Builder protoRequestBuilder = RuleServiceProto.ModifyRuleCollectionRequest.newBuilder()
                .setCollectionId(request.getCollectionId())
                .setRequestId(request.getRequestId() != null ? request.getRequestId() : UUID.randomUUID().toString());
            if (request.getName() != null) {
                protoRequestBuilder.setName(request.getName());
            }
            if (request.getRuleIds() != null) {
                protoRequestBuilder.addAllRuleIds(request.getRuleIds());
            }
            RuleServiceProto.ModifyRuleCollectionResponse response = ruleServiceClient.modifyRuleCollection(protoRequestBuilder.build());
            if (response.getStatus().getCode() == RuleServiceProto.StatusCode.OK) {
                RuleServiceProto.RuleCollectionDetailsPayload payload = response.getPayload();

                ModifyRuleCollectionResponse modifyResponse = ModifyRuleCollectionResponse.builder()
                        .collectionId(payload.getCollectionId())
                        .name(payload.getName())
                        .ruleIds(payload.getRuleIdsList())
                        .createdAt(payload.getCreatedAt())
                        .updatedAt(payload.getUpdatedAt())
                        .build();

                return ResponseEntity.ok(modifyResponse);
            } else {
                ErrorResponse errorResponse = ErrorResponse.builder()
                        .code(response.getError().getCode())
                        .message(response.getError().getMessage())
                        .details(response.getError().getDetailsMap())
                        .build();
                return ResponseEntity.badRequest().body(errorResponse);
            }
        } catch (Exception e) {
            log.error("Error in modify rule collection: {}", e.getMessage(), e);
            ErrorResponse errorResponse = ErrorResponse.builder()
                    .code("INTERNAL_ERROR")
                    .message("Internal server error")
                    .build();
            return ResponseEntity.internalServerError().body(errorResponse);
        }
    }

    private Value convertJsonNodeToValue(JsonNode node) {
        Value.Builder valueBuilder = Value.newBuilder();

        if (node.isTextual()) {
            valueBuilder.setStringValue(node.textValue());
        } else if (node.isNumber()) {
            valueBuilder.setNumberValue(node.doubleValue());
        } else if (node.isBoolean()) {
            valueBuilder.setBoolValue(node.booleanValue());
        } else if (node.isObject()) {
            Struct.Builder structBuilder = Struct.newBuilder();
            node.properties().forEach(entry -> {
                structBuilder.putFields(entry.getKey(), convertJsonNodeToValue(entry.getValue()));
            });
            valueBuilder.setStructValue(structBuilder.build());
        } else if (node.isArray()) {
            ListValue.Builder listBuilder = ListValue.newBuilder();
            node.forEach(item -> listBuilder.addValues(convertJsonNodeToValue(item)));
            valueBuilder.setListValue(listBuilder.build());
        } else if (node.isNull()) {
            valueBuilder.setNullValue(NullValue.NULL_VALUE);
        }

        return valueBuilder.build();
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
                Map<String, Object> structMap = new HashMap<>();
                for (Map.Entry<String, com.google.protobuf.Value> entry : value.getStructValue().getFieldsMap().entrySet()) {
                    structMap.put(entry.getKey(), convertProtobufValueToJavaObject(entry.getValue()));
                }
                return structMap;
            case LIST_VALUE:
                List<Object> list = new java.util.ArrayList<>();
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
