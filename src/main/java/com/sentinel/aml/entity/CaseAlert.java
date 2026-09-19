package com.sentinel.aml.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Join entity linking a Case to one or more Alerts.
 * A single case can consolidate multiple related alerts for the same customer.
 */
@Entity
@Table(name = "case_alerts")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CaseAlert {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "case_id", nullable = false)
    private Case linkedCase;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "alert_id", nullable = false)
    private Alert alert;

    @Column(name = "linked_at", nullable = false, updatable = false)
    private LocalDateTime linkedAt;

    @PrePersist
    protected void onCreate() {
        linkedAt = LocalDateTime.now();
    }
}
