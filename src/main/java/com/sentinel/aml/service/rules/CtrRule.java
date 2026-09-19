package com.sentinel.aml.service.rules;

import com.sentinel.aml.config.AmlRuleProperties;
import com.sentinel.aml.entity.Transaction;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

/**
 * RULE 1 — Currency Transaction Report (CTR) Threshold
 *
 * Any single transaction with an INR-normalised amount >= configured threshold
 * (~$10,000 USD equivalent) must be flagged for mandatory CTR review.
 *
 * Business rule: BR-1 from problem statement.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CtrRule implements DetectionRule {

    static final String RULE_CODE = "CTR_THRESHOLD";

    private final AmlRuleProperties props;

    @Override
    public String getRuleCode() {
        return RULE_CODE;
    }

    @Override
    public Optional<DetectionResult> evaluate(Transaction transaction) {
        BigDecimal amountInr = transaction.getAmountInr();
        if (amountInr == null) {
            log.warn("Transaction {} has null amountInr — skipping CTR check",
                    transaction.getTransactionRef());
            return Optional.empty();
        }

        BigDecimal threshold = props.getCtrThresholdInr();

        if (amountInr.compareTo(threshold) >= 0) {
            String explanation = String.format(
                    "CTR threshold exceeded: transaction amount ₹%,.2f >= CTR threshold ₹%,.2f. " +
                    "Transaction ref: %s, account: %s, type: %s.",
                    amountInr, threshold,
                    transaction.getTransactionRef(),
                    transaction.getSourceAccount().getAccountId(),
                    transaction.getTransactionType());

            log.debug("CTR rule TRIGGERED for txn {}: amountInr={}",
                    transaction.getTransactionRef(), amountInr);

            return Optional.of(DetectionResult.builder()
                    .ruleCode(RULE_CODE)
                    .explanation(explanation)
                    .suggestedRiskScore(60)
                    .evidenceTransactionRefs(List.of(transaction.getTransactionRef()))
                    .build());
        }

        return Optional.empty();
    }
}
