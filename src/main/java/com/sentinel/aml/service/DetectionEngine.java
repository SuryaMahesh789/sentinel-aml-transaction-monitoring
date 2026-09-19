package com.sentinel.aml.service;

import com.sentinel.aml.entity.Alert;
import com.sentinel.aml.entity.Transaction;
import com.sentinel.aml.service.rules.DetectionResult;
import com.sentinel.aml.service.rules.DetectionRule;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Orchestrates all registered DetectionRule implementations.
 *
 * Every rule is evaluated independently — multiple rules can fire for the
 * same transaction. The engine never stops early after the first match.
 *
 * Rules are injected as a List<DetectionRule> by Spring, so adding a new
 * rule class annotated with @Component automatically registers it here.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DetectionEngine {

    /** All DetectionRule beans — order is non-deterministic but all run */
    private final List<DetectionRule> rules;
    private final AlertService        alertService;

    /**
     * Evaluate all rules against the given transaction.
     * Creates an Alert for every rule that triggers.
     *
     * @param transaction fully-persisted transaction
     * @return list of created Alerts (may be empty if no rules fire)
     */
    public List<Alert> evaluate(Transaction transaction) {
        log.debug("Running detection engine on txn={}, account={}, amountInr={}",
                transaction.getTransactionRef(),
                transaction.getSourceAccount().getAccountId(),
                transaction.getAmountInr());

        List<Alert> createdAlerts = new ArrayList<>();

        for (DetectionRule rule : rules) {
            try {
                Optional<DetectionResult> result = rule.evaluate(transaction);
                result.ifPresent(dr -> {
                    Alert alert = alertService.createAlertIfNotDuplicate(transaction, dr);
                    if (alert != null) {
                        createdAlerts.add(alert);
                    }
                });
            } catch (RuntimeException e) {
                // Re-throw persistence/infrastructure failures — these must not be swallowed,
                // as they indicate the alert was NOT saved (e.g. FK violation, DB error).
                // Only catch-and-continue for rule evaluation bugs (logged below).
                log.error("Rule {} failed for txn {}: {}",
                        rule.getRuleCode(), transaction.getTransactionRef(), e.getMessage(), e);
                throw e;
            } catch (Exception e) {
                // Checked exceptions from rule evaluation — isolate and continue
                log.error("Rule {} threw checked exception for txn {}: {}",
                        rule.getRuleCode(), transaction.getTransactionRef(), e.getMessage(), e);
            }
        }

        if (createdAlerts.isEmpty()) {
            log.debug("No rules triggered for txn={}", transaction.getTransactionRef());
        } else {
            log.info("{} alert(s) generated for txn={}: {}",
                    createdAlerts.size(),
                    transaction.getTransactionRef(),
                    createdAlerts.stream().map(Alert::getAlertRef).toList());
        }

        return createdAlerts;
    }
}
