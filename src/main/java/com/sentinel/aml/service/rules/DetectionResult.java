package com.sentinel.aml.service.rules;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

/**
 * Immutable result produced by a DetectionRule when it fires.
 * Contains everything AlertService needs to create an Alert.
 */
@Getter
@Builder
public class DetectionResult {

    /** Rule code matching alert_rules.rule_code in the database */
    private final String ruleCode;

    /** Human-readable explanation shown to analysts */
    private final String explanation;

    /**
     * Suggested risk score contribution from this rule (0–100).
     * Final alert score = min(100, base_risk_score from DB + any boosters).
     * Using the DB-stored base_risk_score keeps scoring configurable.
     */
    private final int suggestedRiskScore;

    /**
     * Transaction refs forming the supporting evidence.
     * Stored as JSON array in alerts.evidence_transaction_ids.
     */
    private final List<String> evidenceTransactionRefs;
}
