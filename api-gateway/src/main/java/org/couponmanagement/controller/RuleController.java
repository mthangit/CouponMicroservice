package org.couponmanagement.controller;

import jakarta.validation.Valid;
import org.couponmanagement.dto.ModifyRuleCollectionRequest;
import org.couponmanagement.dto.response.ErrorResponse;
import org.couponmanagement.dto.response.ApiResponse;
import org.couponmanagement.dto.response.RuleCollectionDTO;
import org.couponmanagement.grpc.RuleServiceClient;
import org.couponmanagement.grpc.annotation.PerformanceMonitor;
import org.couponmanagement.rule.*;
import org.couponmanagement.security.RequireAdmin;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import lombok.extern.slf4j.Slf4j;
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
    public ResponseEntity<?> modifyRule(@PathVariable int ruleId, @Valid @RequestBody RuleServiceProto.ModifyRuleRequest request) {
        try {
            log.info("Modify rule: id={}", ruleId);

            RuleServiceProto.ModifyRuleRequest.Builder requestBuilder = request.toBuilder();
            requestBuilder.setRuleId(ruleId);
            if (request.getRequestId().isEmpty()) {
                requestBuilder.setRequestId(UUID.randomUUID().toString());
            }

            RuleServiceProto.ModifyRuleResponse response = ruleServiceClient.modifyRule(requestBuilder.build());

            if (response.getStatus().getCode() == RuleServiceProto.StatusCode.OK) {
                return ResponseEntity.ok(response.getPayload());
            } else {
                return ResponseEntity.badRequest().body(response.getError());
            }
        } catch (Exception e) {
            log.error("Error in modify rule: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().body("Internal server error");
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

            if (response.getStatus().getCode() == RuleServiceProto.StatusCode.OK) {
                return ResponseEntity.ok(response.getPayload());
            } else {
                return ResponseEntity.badRequest().body(response.getError());
            }
        } catch (Exception e) {
            log.error("Error in list rules: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().body("Internal server error");
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

            // Convert DTO to protobuf
            RuleServiceProto.ModifyRuleCollectionRequest.Builder protoRequestBuilder =
                RuleServiceProto.ModifyRuleCollectionRequest.newBuilder()
                    .setCollectionId(request.getCollectionId());

            if (request.getRequestId() != null && !request.getRequestId().trim().isEmpty()) {
                protoRequestBuilder.setRequestId(request.getRequestId());
            } else {
                protoRequestBuilder.setRequestId(UUID.randomUUID().toString());
            }

            if (request.getName() != null && !request.getName().trim().isEmpty()) {
                protoRequestBuilder.setName(request.getName());
            }

            if (request.getDescription() != null && !request.getDescription().trim().isEmpty()) {
                protoRequestBuilder.setDescription(request.getDescription());
            }

            if (request.getIsActive() != null) {
                protoRequestBuilder.setIsActive(request.getIsActive());
            }

            RuleServiceProto.ModifyRuleCollectionResponse response = ruleServiceClient.modifyRuleCollection(protoRequestBuilder.build());

            if (response.getStatus().getCode() == RuleServiceProto.StatusCode.OK) {
                return ResponseEntity.ok(response.getPayload());
            } else {
                // Convert protobuf error to DTO
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
}
