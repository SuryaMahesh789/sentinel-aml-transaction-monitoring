package com.sentinel.aml.service.rules;

import com.sentinel.aml.config.AmlRuleProperties;
import com.sentinel.aml.entity.Transaction;
import com.sentinel.aml.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * RULE 2 — Structuring / Smurfing
 *
 * If 3+ transactions from the same account within a 24-hour window
 * each fall within the $9,000–$9,999 INR-equivalent range, trigger an alert.
 *
 * This detects attempts to avoid CTR filing thresholds by breaking large
 * amounts into just-below-threshold transactions.
 *
 * Business rule: BR-2 from problem statement.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class StructuringRule implements DetectionRule {

    static final String RULE_CODE = "STRUCTURING";

    private final AmlRuleProperties props;
    private final TransactionRepository transactionRepository;

    @Override
    public String getRuleCode() {
        return RULE_CODE;
    }

    @Override
    public Optional<DetectionResult> evaluate(Transaction transaction) {
        BigDecimal amountInr = transaction.getAmountInr();
        if (amountInr == null) return Optional.empty();

        AmlRuleProperties.Structuring cfg = props.getStructuring();

        // Only consider transactions within the structuring range for the current txn
        if (amountInr.compareTo(cfg.getMinAmountInr()) < 0
                || amountInr.compareTo(cfg.getMaxAmountInr()) > 0) {
            return Optional.empty();
        }

        // Look back windowHours from this transaction's timestamp
        LocalDateTime windowStart = transaction.getTransactionTimestamp()
                .minusHours(cfg.getWindowHours());
        LocalDateTime windowEnd   = transaction.getTransactionTimestamp();

        String accountId = transaction.getSourceAccount().getAccountId();

        List<Transaction> windowTxns = transactionRepository
                .findByAccountAndTimeWindow(accountId, windowStart, windowEnd);

        // Count only those within the structuring band (including current transaction)
        List<Transaction> structuringTxns = windowTxns.stream()
                .filter(t -> t.getAmountInr() != null
                        && t.getAmountInr().compareTo(cfg.getMinAmountInr()) >= 0
                        && t.getAmountInr().compareTo(cfg.getMaxAmountInr()) <= 0)
                .toList();

        if (structuringTxns.size() >= cfg.getCountThreshold()) {
            List<String> evidenceRefs = structuringTxns.stream()
                    .map(Transaction::getTransactionRef)
                    .toList();

            String explanation = String.format(
                    "Structuring pattern detected: %d transactions from account %s " +
                    "within %d hours each in the ₹%,.0f–₹%,.0f range " +
                    "(just below CTR threshold). Evidence refs: %s",
                    structuringTxns.size(), accountId, cfg.getWindowHours(),
                    cfg.getMinAmountInr(), cfg.getMaxAmountInr(),
                    String.join(", ", evidenceRefs));

            log.debug("STRUCTURING rule TRIGGERED for account {}: {} txns in window",
                    accountId, structuringTxns.size());

            return Optional.of(DetectionResult.builder()
                    .ruleCode(RULE_CODE)
                    .explanation(explanation)
                    .suggestedRiskScore(75)
                    .evidenceTransactionRefs(evidenceRefs)
                    .build());
        }

        return Optional.empty();
    }
}
