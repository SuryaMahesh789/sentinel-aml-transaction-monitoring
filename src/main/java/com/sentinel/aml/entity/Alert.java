package com.sentinel.aml.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "alerts", indexes = {
        @Index(name = "idx_alert_customer", columnList = "customer_id"),
        @Index(name = "idx_alert_status", columnList = "status"),
        @Index(name = "idx_alert_risk_score", columnList = "risk_score")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Alert {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "alert_ref", nullable = false, unique = true, length = 80)
    private String alertRef;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id", nullable = false)
    private Customer customer;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "account_id")
    private Account account;

    /** The specific transaction that triggered this alert (may be null for pattern-based alerts) */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "transaction_id")
    private Transaction transaction;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "rule_id", nullable = false)
    private AlertRule rule;

    /** Calculated risk score 0–100 */
    @Column(name = "risk_score", nullable = false)
    private int riskScore;

    /** Human-readable explanation of why this alert was triggered */
    @Column(name = "explanation", nullable = false, columnDefinition = "TEXT")
    private String explanation;

    /**
     * JSON array of transaction IDs that constitute the supporting evidence.
     * Example: ["TXN001","TXN002","TXN003"]
     */
    @Column(name = "evidence_transaction_ids", columnDefinition = "TEXT")
    private String evidenceTransactionIds;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private AlertStatus status;

    /** Analyst who reviewed / cleared this alert */
    @Column(name = "reviewed_by", length = 100)
    private String reviewedBy;

    /** Timestamp when analyst reviewed */
    @Column(name = "reviewed_at")
    private LocalDateTime reviewedAt;

    /**
     * Mandatory when clearing/dismissing an alert.
     * Alerts are NEVER deleted — they are dispositioned with a reason.
     */
    @Column(name = "clearance_reason", length = 500)
    private String clearanceReason;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        if (status == null) {
            status = AlertStatus.OPEN;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public enum AlertStatus {
        OPEN,           // newly generated, awaiting review
        UNDER_REVIEW,   // analyst has picked it up
        ESCALATED,      // sent to senior analyst / management
        CLEARED,        // reviewed and dismissed (false positive)
        SAR_FILED,      // suspicious activity report filed
        CLOSED          // resolved without SAR
    }
}
