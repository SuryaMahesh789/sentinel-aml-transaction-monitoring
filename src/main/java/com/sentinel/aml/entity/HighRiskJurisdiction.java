package com.sentinel.aml.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Configurable list of high-risk or sanctioned jurisdictions.
 * Transactions involving these country codes must always generate an alert.
 */
@Entity
@Table(name = "high_risk_jurisdictions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class HighRiskJurisdiction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** ISO 3166-1 alpha-2 country code, e.g. "IR", "KP", "SY" */
    @Column(name = "country_code", nullable = false, unique = true, length = 5)
    private String countryCode;

    @Column(name = "country_name", nullable = false, length = 100)
    private String countryName;

    @Enumerated(EnumType.STRING)
    @Column(name = "risk_level", nullable = false, length = 20)
    private RiskLevel riskLevel;

    @Column(name = "reason", length = 300)
    private String reason;

    @Column(name = "is_active", nullable = false)
    private boolean active;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }

    public enum RiskLevel {
        HIGH, SANCTIONED
    }
}
