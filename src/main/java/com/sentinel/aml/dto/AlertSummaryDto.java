package com.sentinel.aml.dto;

import com.sentinel.aml.entity.Alert;
import com.sentinel.aml.entity.Alert.AlertStatus;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

/**
 * Compact alert representation for the paginated list view.
 * PII is intentionally minimal — full customer/transaction detail
 * is only exposed in the detail endpoint.
 */
@Getter
@Builder
public class AlertSummaryDto {

    private Long   id;
    private String alertRef;
    private String customerId;
    private String accountId;
    private String transactionRef;
    private String ruleCode;
    private String ruleName;
    private int    riskScore;
    private String explanation;
    private AlertStatus status;
    private String reviewedBy;
    private LocalDateTime reviewedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static AlertSummaryDto from(Alert a) {
        return AlertSummaryDto.builder()
                .id(a.getId())
                .alertRef(a.getAlertRef())
                .customerId(a.getCustomer() != null ? mask(a.getCustomer().getCustomerId()) : null)
                .accountId(a.getAccount() != null ? a.getAccount().getAccountId() : null)
                .transactionRef(a.getTransaction() != null ? a.getTransaction().getTransactionRef() : null)
                .ruleCode(a.getRule() != null ? a.getRule().getRuleCode() : null)
                .ruleName(a.getRule() != null ? a.getRule().getRuleName() : null)
                .riskScore(a.getRiskScore())
                .explanation(a.getExplanation())
                .status(a.getStatus())
                .reviewedBy(a.getReviewedBy())
                .reviewedAt(a.getReviewedAt())
                .createdAt(a.getCreatedAt())
                .updatedAt(a.getUpdatedAt())
                .build();
    }

    /** Mask customer ID for list views per PII policy (show first 4 chars + ***) */
    private static String mask(String value) {
        if (value == null || value.length() <= 4) return "****";
        return value.substring(0, 4) + "****";
    }
}
