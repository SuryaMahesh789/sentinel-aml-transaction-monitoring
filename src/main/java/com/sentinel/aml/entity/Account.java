package com.sentinel.aml.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "accounts")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Account {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "account_id", nullable = false, unique = true, length = 50)
    private String accountId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id", nullable = false)
    private Customer customer;

    @Column(name = "account_type", nullable = false, length = 30)
    private String accountType;

    @Enumerated(EnumType.STRING)
    @Column(name = "account_status", nullable = false, length = 20)
    private AccountStatus accountStatus;

    @Column(name = "currency", nullable = false, length = 10)
    private String currency;

    @Column(name = "open_date")
    private LocalDate openDate;

    @Column(name = "close_date")
    private LocalDate closeDate;

    @Column(name = "branch_code", length = 20)
    private String branchCode;

    @Column(name = "branch_city", length = 100)
    private String branchCity;

    @Column(name = "current_balance", precision = 18, scale = 2)
    private BigDecimal currentBalance;

    @Column(name = "avg_monthly_balance_6m", precision = 18, scale = 2)
    private BigDecimal avgMonthlyBalance6m;

    @Column(name = "avg_monthly_txn_count")
    private Integer avgMonthlyTxnCount;

    @Enumerated(EnumType.STRING)
    @Column(name = "risk_rating", length = 20)
    private Customer.RiskRating riskRating;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @OneToMany(mappedBy = "sourceAccount", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @Builder.Default
    private List<Transaction> transactions = new ArrayList<>();

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public enum AccountStatus {
        ACTIVE, INACTIVE, FROZEN, CLOSED
    }
}
