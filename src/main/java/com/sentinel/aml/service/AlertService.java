package com.sentinel.aml.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sentinel.aml.entity.Alert;
import com.sentinel.aml.entity.AlertRule;
import com.sentinel.aml.entity.Transaction;
import com.sentinel.aml.repository.AlertRepository;
import com.sentinel.aml.repository.AlertRuleRepository;
import com.sentinel.aml.service.rules.DetectionResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;

/**
 * Creates, deduplicates, and persists Alert entities.
 *
 * Alerts are NEVER physically deleted — only their status changes.
 * Duplicate alert detection: if an OPEN/UNDER_REVIEW alert already exists
 * for the same transaction + rule, skip creation.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AlertService {

    private static final DateTimeFormatter REF_FMT =
            DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private final AlertRepository     alertRepository;
    private final AlertRuleRepository alertRuleRepository;
    private final ObjectMapper        objectMapper;

    /**
     * Persists an Alert from a DetectionResult.
     *
     * Risk score = base_risk_score from the DB-configured AlertRule.
     * This makes the score configurable without code changes.
     * The suggestedRiskScore in DetectionResult is used as a fallback if
     * the DB rule is not found (defensive coding).
     *
     * Returns the saved Alert, or null if deduplicated.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Alert createAlertIfNotDuplicate(Transaction transaction, DetectionResult result) {

        // 1. Deduplication check: open/under-review alert for same txn+rule
        List<Alert> existing = alertRepository.findOpenAlertsByTransactionAndRule(
                transaction.getTransactionRef(), result.getRuleCode());
        if (!existing.isEmpty()) {
            log.debug("Alert already exists for txn={} rule={} — skipping",
                    transaction.getTransactionRef(), result.getRuleCode());
            return null;
        }

        // 2. Resolve the AlertRule entity (must exist — seeded in V2 migration)
        AlertRule rule = alertRuleRepository.findByRuleCode(result.getRuleCode())
                .orElseGet(() -> {
                    log.warn("AlertRule not found for code={} — creating minimal placeholder",
                            result.getRuleCode());
                    return createPlaceholderRule(result.getRuleCode(),
                            result.getSuggestedRiskScore());
                });

        // 3. Risk score: use DB-configured base score; cap at 100
        int riskScore = Math.min(100, rule.getBaseRiskScore());

        // 4. Serialize evidence refs to JSON array string
        String evidenceJson = toJson(result.getEvidenceTransactionRefs());

        // 5. Build alert ref
        String alertRef = "ALT-" + transaction.getTransactionRef()
                + "-" + result.getRuleCode().substring(0, 3)
                + "-" + UUID.randomUUID().toString().replace("-", "").substring(0, 6);

        Alert alert = Alert.builder()
                .alertRef(alertRef)
                .customer(transaction.getCustomer())
                .account(transaction.getSourceAccount())
                .transaction(transaction)
                .rule(rule)
                .riskScore(riskScore)
                .explanation(result.getExplanation())
                .evidenceTransactionIds(evidenceJson)
                .status(Alert.AlertStatus.OPEN)
                .build();

        Alert saved = alertRepository.save(alert);

        log.info("Alert created: ref={}, rule={}, score={}, txn={}",
                saved.getAlertRef(), result.getRuleCode(),
                saved.getRiskScore(), transaction.getTransactionRef());

        return saved;
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private String toJson(List<String> list) {
        try {
            return objectMapper.writeValueAsString(list);
        } catch (Exception e) {
            return list != null ? "[\"" + String.join("\",\"", list) + "\"]" : "[]";
        }
    }

    private AlertRule createPlaceholderRule(String ruleCode, int score) {
        AlertRule r = new AlertRule();
        r.setRuleCode(ruleCode);
        r.setRuleName(ruleCode);
        r.setEnabled(true);
        r.setBaseRiskScore(Math.min(100, score));
        return alertRuleRepository.save(r);
    }
}
