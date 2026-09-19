package com.sentinel.aml.service.rules;

import com.sentinel.aml.config.AmlRuleProperties;
import com.sentinel.aml.entity.Transaction;
import com.sentinel.aml.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * RULE 3 — Rapid Movement of Funds
 *
 * If deposits into an account are followed by >=80% being withdrawn/transferred
 * within a 48-hour window, trigger a layering alert.
 *
 * Evaluated on WITHDRAWAL and TRANSFER type transactions: we look back 48h
 * to find total deposits, then check what fraction flowed out.
 *
 * Business rule: BR-3 from problem statement.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RapidMovementRule implements DetectionRule {

    static final String RULE_CODE = "RAPID_MOVEMENT";

    private final AmlRuleProperties props;
    private final TransactionRepository transactionRepository;

    @Override
    public String getRuleCode() {
        return RULE_CODE;
    }

    @Override
    public Optional<DetectionResult> evaluate(Transaction transaction) {
        // Only evaluate on outflow transactions
        Transaction.TransactionType type = transaction.getTransactionType();
        if (type != Transaction.TransactionType.WITHDRAWAL
                && type != Transaction.TransactionType.TRANSFER
                && type != Transaction.TransactionType.REMITTANCE
                && type != Transaction.TransactionType.CASH_WITHDRAWAL) {
            return Optional.empty();
        }

        AmlRuleProperties.RapidMovement cfg = props.getRapidMovement();
        String accountId = transaction.getSourceAccount().getAccountId();

        LocalDateTime windowEnd   = transaction.getTransactionTimestamp();
        LocalDateTime windowStart = windowEnd.minusHours(cfg.getWindowHours());

        BigDecimal totalDeposits  = transactionRepository.sumDepositsInr(accountId, windowStart, windowEnd);
        BigDecimal totalOutflows  = transactionRepository.sumOutflowsInr(accountId, windowStart, windowEnd);

        // Cannot assess rapid movement with no deposit baseline
        if (totalDeposits == null || totalDeposits.compareTo(BigDecimal.ZERO) <= 0) {
            return Optional.empty();
        }

        // outflowPercent = (outflows / deposits) * 100
        BigDecimal outflowPercent = totalOutflows
                .multiply(BigDecimal.valueOf(100))
                .divide(totalDeposits, 2, RoundingMode.HALF_UP);

        BigDecimal threshold = BigDecimal.valueOf(cfg.getOutflowPercentThreshold());

        if (outflowPercent.compareTo(threshold) >= 0) {
            String explanation = String.format(
                    "Rapid fund movement detected on account %s: ₹%,.2f deposited and " +
                    "₹%,.2f (%.1f%%) transferred/withdrawn within %d hours. " +
                    "Threshold: %d%%. Triggering transaction: %s.",
                    accountId, totalDeposits, totalOutflows,
                    outflowPercent.doubleValue(), cfg.getWindowHours(),
                    cfg.getOutflowPercentThreshold(),
                    transaction.getTransactionRef());

            log.debug("RAPID_MOVEMENT rule TRIGGERED for account {}: {}% outflow",
                    accountId, outflowPercent);

            return Optional.of(DetectionResult.builder()
                    .ruleCode(RULE_CODE)
                    .explanation(explanation)
                    .suggestedRiskScore(80)
                    .evidenceTransactionRefs(List.of(transaction.getTransactionRef()))
                    .build());
        }

        return Optional.empty();
    }
}
