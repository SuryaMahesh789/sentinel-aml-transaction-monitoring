package com.sentinel.aml.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "transactions", indexes = {
        @Index(name = "idx_txn_source_account", columnList = "source_account_id"),
        @Index(name = "idx_txn_timestamp", columnList = "transaction_timestamp"),
        @Index(name = "idx_txn_customer", columnList = "customer_id")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Transaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "transaction_ref", nullable = false, unique = true, length = 80)
    private String transactionRef;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "source_account_id", nullable = false)
    private Account sourceAccount;

    // Destination can be internal or external; stored as plain string for flexibility
    @Column(name = "destination_account_ref", length = 80)
    private String destinationAccountRef;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id", nullable = false)
    private Customer customer;

    @Column(name = "amount", nullable = false, precision = 18, scale = 2)
    private BigDecimal amount;

    @Column(name = "currency", nullable = false, length = 10)
    private String currency;

    /** Amount normalised to INR using exchange rate at time of transaction */
    @Column(name = "amount_inr", precision = 18, scale = 2)
    private BigDecimal amountInr;

    @Enumerated(EnumType.STRING)
    @Column(name = "transaction_type", nullable = false, length = 20)
    private TransactionType transactionType;

    @Enumerated(EnumType.STRING)
    @Column(name = "channel", length = 30)
    private Channel channel;

    @Column(name = "counterparty_name", length = 200)
    private String counterpartyName;

    @Column(name = "counterparty_account", length = 80)
    private String counterpartyAccount;

    @Column(name = "counterparty_bank", length = 100)
    private String counterpartyBank;

    /** ISO 3166-1 alpha-2 country code of counterparty jurisdiction */
    @Column(name = "jurisdiction", length = 5)
    private String jurisdiction;

    @Column(name = "transaction_timestamp", nullable = false)
    private LocalDateTime transactionTimestamp;

    @Column(name = "description", length = 500)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private TransactionStatus status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @OneToMany(mappedBy = "transaction", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @Builder.Default
    private List<Alert> alerts = new ArrayList<>();

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        if (status == null) {
            status = TransactionStatus.COMPLETED;
        }
    }

    public enum TransactionType {
        DEPOSIT, WITHDRAWAL, TRANSFER, PAYMENT, REMITTANCE, CASH_DEPOSIT, CASH_WITHDRAWAL
    }

    public enum Channel {
        ONLINE, BRANCH, ATM, MOBILE, POS, WIRE, INTERNAL
    }

    public enum TransactionStatus {
        PENDING, COMPLETED, FAILED, REVERSED
    }
}
