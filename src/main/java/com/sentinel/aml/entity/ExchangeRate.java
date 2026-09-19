package com.sentinel.aml.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Configurable exchange rate table.
 * All transaction amounts are normalised to INR using rates from this table.
 */
@Entity
@Table(name = "exchange_rates", indexes = {
        @Index(name = "idx_exchange_currency_active", columnList = "from_currency, is_active")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ExchangeRate {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Source currency code (ISO 4217), e.g. USD, EUR, GBP */
    @Column(name = "from_currency", nullable = false, length = 10)
    private String fromCurrency;

    /** Always INR for this system */
    @Column(name = "to_currency", nullable = false, length = 10)
    private String toCurrency;

    /** Multiplier: 1 fromCurrency = rate INR */
    @Column(name = "rate", nullable = false, precision = 18, scale = 6)
    private BigDecimal rate;

    @Column(name = "is_active", nullable = false)
    private boolean active;

    @Column(name = "effective_from", nullable = false)
    private LocalDateTime effectiveFrom;

    @Column(name = "effective_to")
    private LocalDateTime effectiveTo;

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
