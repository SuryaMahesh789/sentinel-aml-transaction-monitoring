package com.sentinel.aml.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "cases")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Case {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "case_ref", nullable = false, unique = true, length = 80)
    private String caseRef;

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

    @Column(name = "closure_notes", columnDefinition = "TEXT")
    private String closureNotes;

    @Column(name = "created_by", length = 100)
    private String createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    /** Alerts linked to this case */
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
        OPEN, IN_PROGRESS, PENDING_REVIEW, ESCALATED, CLOSED_SAR, CLOSED_NO_ACTION
    }

    public enum Priority {
        LOW, MEDIUM, HIGH, CRITICAL
    }
}
