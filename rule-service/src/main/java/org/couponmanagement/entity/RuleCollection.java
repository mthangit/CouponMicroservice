package org.couponmanagement.entity;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Data
@AllArgsConstructor
@NoArgsConstructor
@Table(name = "rule_collection")
@Slf4j
public class RuleCollection {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "name", length = 50)
    private String name;

    @Column(name = "rule_ids", columnDefinition = "JSON", nullable = false)
    private String ruleIds;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
    
    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }
    
    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
    
    // Helper methods to work with JSON rule_ids
    public List<Integer> getRuleIdsList() {
        if (ruleIds == null || ruleIds.trim().isEmpty()) {
            return new ArrayList<>();
        }
        
        try {
            ObjectMapper objectMapper = new ObjectMapper();
            return objectMapper.readValue(ruleIds, new TypeReference<List<Integer>>() {});
        } catch (JsonProcessingException e) {
            log.error("Error parsing rule IDs JSON: {}", ruleIds, e);
            return new ArrayList<>();
        }
    }
    
    public void setRuleIdsList(List<Integer> ruleIdsList) {
        try {
            ObjectMapper objectMapper = new ObjectMapper();
            this.ruleIds = objectMapper.writeValueAsString(ruleIdsList);
        } catch (JsonProcessingException e) {
            log.error("Error serializing rule IDs to JSON: {}", ruleIdsList, e);
            this.ruleIds = "[]";
        }
    }
}
