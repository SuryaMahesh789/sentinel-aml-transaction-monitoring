package com.sentinel.aml.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Immutable audit trail for all state transitions on Alerts and Cases.
 * Records are INSERT-only — never updated or deleted.
 */
@Entity
@Table(name = "audit_logs", indexes = {
        @Index(name = "idx_audit_entity", columnList = "entity_type, entity_id"),
        @Index(name = "idx_audit_actor", columnList = "actor")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Type of entity being audited: ALERT, CASE, TRANSACTION etc. */
    @Column(name = "entity_type", nullable = false, length = 30)
    private String entityType;

    /** Primary key of the entity being audited */
    @Column(name = "entity_id", nullable = false)
    private Long entityId;

    /** Action performed: STATUS_CHANGE, ASSIGNED, CLEARED, SAR_FILED etc. */
    @Column(name = "action", nullable = false, length = 50)
    private String action;

    @Column(name = "previous_value", length = 100)
    private String previousValue;

    @Column(name = "new_value", length = 100)
    private String newValue;

    /** Username of the system or analyst who performed the action */
    @Column(name = "actor", nullable = false, length = 100)
    private String actor;

    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;

    @Column(name = "timestamp", nullable = false, updatable = false)
    private LocalDateTime timestamp;

    @PrePersist
    protected void onCreate() {
        timestamp = LocalDateTime.now();
    }
}
