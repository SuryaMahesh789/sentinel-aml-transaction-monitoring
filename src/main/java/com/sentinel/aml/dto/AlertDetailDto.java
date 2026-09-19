package com.sentinel.aml.dto;

import com.sentinel.aml.entity.Alert;
import com.sentinel.aml.entity.Alert.AlertStatus;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Full alert detail including transaction and customer information.
 * Exposes unmasked PII — intended for authorized analyst detail view only.
 */
@Getter
@Builder
public class AlertDetailDto {

    // Alert fields
    private Long        id;
    private String      alertRef;
    private int         riskScore;
    private String      explanation;
    private String      evidenceTransactionIds;
    private AlertStatus status;
    private String      reviewedBy;
    private LocalDateTime reviewedAt;
    private String      clearanceReason;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    // Rule fields
    private String ruleCode;
    private String ruleName;
    private String ruleDescription;

    // Customer fields (full — detail view)
    private String customerId;
    private String customerFirstName;
    private String customerLastName;
    private String customerRiskRating;

    // Account fields
    private String accountId;
    private String accountType;

    // Transaction fields
    private String      transactionRef;
    private BigDecimal  transactionAmount;
    private String      transactionCurrency;
    private BigDecimal  transactionAmountInr;
    private String      transactionType;
    private String      jurisdiction;
    private LocalDateTime transactionTimestamp;

    public static AlertDetailDto from(Alert a) {
        var b = AlertDetailDto.builder()
                .id(a.getId())
                .alertRef(a.getAlertRef())
                .riskScore(a.getRiskScore())
                .explanation(a.getExplanation())
                .evidenceTransactionIds(a.getEvidenceTransactionIds())
                .status(a.getStatus())
                .reviewedBy(a.getReviewedBy())
                .reviewedAt(a.getReviewedAt())
                .clearanceReason(a.getClearanceReason())
                .createdAt(a.getCreatedAt())
                .updatedAt(a.getUpdatedAt());

        if (a.getRule() != null) {
            b.ruleCode(a.getRule().getRuleCode())
             .ruleName(a.getRule().getRuleName())
             .ruleDescription(a.getRule().getDescription());
        }
        if (a.getCustomer() != null) {
            b.customerId(a.getCustomer().getCustomerId())
             .customerFirstName(a.getCustomer().getFirstName())
             .customerLastName(a.getCustomer().getLastName())
             .customerRiskRating(a.getCustomer().getRiskRating() != null
                     ? a.getCustomer().getRiskRating().name() : null);
        }
        if (a.getAccount() != null) {
            b.accountId(a.getAccount().getAccountId())
             .accountType(a.getAccount().getAccountType());
        }
        if (a.getTransaction() != null) {
            var t = a.getTransaction();
            b.transactionRef(t.getTransactionRef())
             .transactionAmount(t.getAmount())
             .transactionCurrency(t.getCurrency())
             .transactionAmountInr(t.getAmountInr())
             .transactionType(t.getTransactionType() != null ? t.getTransactionType().name() : null)
             .jurisdiction(t.getJurisdiction())
             .transactionTimestamp(t.getTransactionTimestamp());
        }
        return b.build();
    }
}
