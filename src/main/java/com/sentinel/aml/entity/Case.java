package com.sentinel.aml.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "cases", indexes = {
        @Index(name = "idx_cases_status",      columnList = "status"),
        @Index(name = "idx_cases_assigned_to", columnList = "assigned_to"),
        @Index(name = "idx_cases_customer_id", columnList = "customer_id")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Case {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Human-readable unique reference, e.g. CASE-20240915-001 */
    @Column(name = "case_ref", nullable = false, unique = true, length = 80)
    private String caseRef;

    /** Customer the case is investigating */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id", nullable = false)
    private Customer customer;

    @Column(name = "title", nullable = false, length = 200)
    private String title;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private CaseStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "priority", length = 20)
    private Priority priority;

    /** Analyst username assigned to this case */
    @Column(name = "assigned_to", length = 100)
    private String assignedTo;

    @Column(name = "assigned_at")
    private LocalDateTime assignedAt;

    @Column(name = "closed_at")
    private LocalDateTime closedAt;

    /** Ongoing investigation notes — updated as analyst works the case */
    @Column(name = "investigation_notes", columnDefinition = "TEXT")
    private String investigationNotes;

    /** Required when closing a case — explains the outcome */
    @Column(name = "resolution_reason", columnDefinition = "TEXT")
    private String resolutionReason;

    /** Legacy closure notes field kept for schema compatibility */
    @Column(name = "closure_notes", columnDefinition = "TEXT")
    private String closureNotes;

    @Column(name = "created_by", length = 100)
    private String createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    /** Alerts linked to this case (via join table) */
    @OneToMany(mappedBy = "linkedCase", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @Builder.Default
    private List<CaseAlert> caseAlerts = new ArrayList<>();

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        if (status == null) {
            status = CaseStatus.OPEN;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public enum CaseStatus {
        OPEN,
        IN_PROGRESS,
        PENDING_REVIEW,
        ESCALATED,
        CLOSED_SAR,          // closed with SAR filed
        CLOSED_NO_ACTION     // closed with no further action
    }

    public enum Priority {
        LOW, MEDIUM, HIGH, CRITICAL
    }
}
