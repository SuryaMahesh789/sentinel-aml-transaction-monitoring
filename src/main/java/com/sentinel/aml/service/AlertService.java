package com.sentinel.aml.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sentinel.aml.dto.AlertDetailDto;
import com.sentinel.aml.dto.AlertDispositionRequestDto;
import com.sentinel.aml.dto.AlertSummaryDto;
import com.sentinel.aml.entity.Alert;
import com.sentinel.aml.entity.Alert.AlertStatus;
import com.sentinel.aml.entity.AlertRule;
import com.sentinel.aml.entity.Transaction;
import com.sentinel.aml.exception.ResourceNotFoundException;
import com.sentinel.aml.exception.ValidationException;
import com.sentinel.aml.repository.AlertRepository;
import com.sentinel.aml.repository.AlertRuleRepository;
import com.sentinel.aml.service.rules.DetectionResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Creates, deduplicates, and persists Alert entities.
 * Also provides management operations: list, get, and disposition.
 *
 * Alerts are NEVER physically deleted — only their status changes.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AlertService {

    private static final DateTimeFormatter REF_FMT =
            DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    /** Statuses that are valid disposition targets via the API */
    private static final Set<AlertStatus> DISPOSITIONABLE = Set.of(
            AlertStatus.UNDER_REVIEW, AlertStatus.ESCALATED,
            AlertStatus.CLEARED, AlertStatus.SAR_FILED, AlertStatus.CLOSED);

    private final AlertRepository     alertRepository;
    private final AlertRuleRepository alertRuleRepository;
    private final ObjectMapper        objectMapper;

    // =========================================================================
    // Detection-time alert creation (called by DetectionEngine)
    // =========================================================================

    /**
     * Persists an Alert from a DetectionResult.
     * Risk score = base_risk_score from the DB-configured AlertRule (configurable).
     * Returns null if a duplicate open alert already exists for the same txn + rule.
     *
     * Intentionally NOT annotated with REQUIRES_NEW — must participate in the
     * outer TransactionService transaction so the Transaction FK is visible to
     * PostgreSQL when the alert row is inserted.
     */
    @Transactional
    public Alert createAlertIfNotDuplicate(Transaction transaction, DetectionResult result) {

        // Deduplication: skip if OPEN/UNDER_REVIEW alert exists for same txn+rule
        List<Alert> existing = alertRepository.findOpenAlertsByTransactionAndRule(
                transaction.getTransactionRef(), result.getRuleCode());
        if (!existing.isEmpty()) {
            log.debug("Alert already exists for txn={} rule={} — skipping",
                    transaction.getTransactionRef(), result.getRuleCode());
            return null;
        }

        AlertRule rule = alertRuleRepository.findByRuleCode(result.getRuleCode())
                .orElseGet(() -> {
                    log.warn("AlertRule not found for code={} — creating minimal placeholder",
                            result.getRuleCode());
                    return createPlaceholderRule(result.getRuleCode(),
                            result.getSuggestedRiskScore());
                });

        int riskScore     = Math.min(100, rule.getBaseRiskScore());
        String evidenceJson = toJson(result.getEvidenceTransactionRefs());
        String alertRef   = "ALT-" + transaction.getTransactionRef()
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
                .status(AlertStatus.OPEN)
                .build();

        Alert saved = alertRepository.save(alert);
        log.info("Alert created: ref={}, rule={}, score={}, txn={}",
                saved.getAlertRef(), result.getRuleCode(),
                saved.getRiskScore(), transaction.getTransactionRef());
        return saved;
    }

    // =========================================================================
    // Management API operations
    // =========================================================================

    /**
     * Paginated alert list with optional status and rule-code filters.
     * Default sort: risk score DESC, then createdAt DESC.
     */
    @Transactional(readOnly = true)
    public Page<AlertSummaryDto> listAlerts(AlertStatus status, String ruleCode,
                                             int page, int size, String sortBy) {
        int clampedSize = Math.min(size, 100);
        Sort sort = "createdAt".equalsIgnoreCase(sortBy)
                ? Sort.by(Sort.Direction.DESC, "createdAt")
                : Sort.by(Sort.Direction.DESC, "riskScore")
                        .and(Sort.by(Sort.Direction.DESC, "createdAt"));

        Pageable pageable = PageRequest.of(page, clampedSize, sort);

        boolean noFilters = status == null && (ruleCode == null || ruleCode.isBlank());
        Page<Alert> alerts = noFilters
                ? alertRepository.findAll(pageable)
                : alertRepository.findByFilters(status, ruleCode, pageable);

        return alerts.map(AlertSummaryDto::from);
    }

    /**
     * Full alert detail by internal database ID.
     */
    @Transactional(readOnly = true)
    public AlertDetailDto getAlertById(Long id) {
        Alert alert = alertRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Alert not found with id: " + id));
        return AlertDetailDto.from(alert);
    }

    /**
     * Disposition an alert: clear, close, escalate, file SAR, or mark under review.
     *
     * Business rules:
     * - OPEN is not a valid target status (it is the initial state only).
     * - Terminal statuses (CLEARED, CLOSED, SAR_FILED) cannot be changed again.
     * - The alert record is NEVER deleted.
     * - Analyst ID, reason, and timestamp are always persisted.
     */
    @Transactional
    public AlertDetailDto disposeAlert(Long id, AlertDispositionRequestDto req) {
        Alert alert = alertRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Alert not found with id: " + id));

        AlertStatus target = req.getTargetStatus();

        if (!DISPOSITIONABLE.contains(target)) {
            throw new ValidationException(
                    "Invalid target status '" + target + "'. " +
                    "Permitted values: UNDER_REVIEW, ESCALATED, CLEARED, SAR_FILED, CLOSED");
        }

        if (alert.getStatus() == AlertStatus.CLEARED
                || alert.getStatus() == AlertStatus.CLOSED
                || alert.getStatus() == AlertStatus.SAR_FILED) {
            throw new ValidationException(
                    "Alert " + id + " is already in terminal status '" + alert.getStatus() +
                    "' and cannot be changed.");
        }

        AlertStatus previous = alert.getStatus();
        alert.setStatus(target);
        alert.setReviewedBy(req.getAnalystId());
        alert.setReviewedAt(LocalDateTime.now());
        alert.setClearanceReason(req.getReason());

        Alert saved = alertRepository.save(alert);
        log.info("Alert {} dispositioned: {} -> {} by analyst={}",
                id, previous, target, req.getAnalystId());

        return AlertDetailDto.from(saved);
    }

    // =========================================================================
    // Helpers
    // =========================================================================

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
