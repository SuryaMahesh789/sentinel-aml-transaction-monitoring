package com.sentinel.aml.service.rules;

import com.sentinel.aml.entity.Transaction;

import java.util.Optional;

/**
 * Common interface for all AML detection rules.
 *
 * Each rule evaluates a single transaction and returns either:
 *   - An Optional containing a DetectionResult if the rule fires
 *   - Optional.empty() if the rule does not fire
 *
 * Rules are independently testable and independently configurable.
 */
public interface DetectionRule {

    /**
     * The rule code this implementation handles.
     * Must match a row in alert_rules.rule_code.
     */
    String getRuleCode();

    /**
     * Evaluate the transaction against this rule.
     *
     * @param transaction fully-loaded transaction to evaluate
     * @return Optional.of(result) when the rule triggers, Optional.empty() otherwise
     */
    Optional<DetectionResult> evaluate(Transaction transaction);
}
