package com.sentinel.aml.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "alert_rules")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AlertRule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Unique code used by the detection engine to identify this rule */
    @Column(name = "rule_code", nullable = false, unique = true, length = 50)
    private String ruleCode;

    @Column(name = "rule_name", nullable = false, length = 100)
    private String ruleName;

    @Column(name = "description", length = 500)
    private String description;

    @Column(name = "enabled", nullable = false)
    private boolean enabled;

    /** Base risk score contribution (0–100) when this rule fires */
    @Column(name = "base_risk_score", nullable = false)
    private int baseRiskScore;

    /**
     * JSON blob storing rule-specific thresholds/parameters.
     * Avoids creating separate tables for each rule's config.
     * Example: {"threshold":833000,"windowHours":24}
     */
    @Column(name = "parameters", columnDefinition = "TEXT")
    private String parameters;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
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
}
