package com.sentinel.aml.service.rules;

import com.sentinel.aml.config.AmlRuleProperties;
import com.sentinel.aml.entity.Transaction;
import com.sentinel.aml.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

/**
 * RULE 5 — Unusual Volume / Behavioral Deviation
 *
 * If a customer's total transaction volume on a given day exceeds
 * 3× their 90-day rolling average daily volume, trigger an alert.
 *
 * Steps:
 *   1. Compute today's total INR volume for the customer.
 *   2. Compute the average daily INR volume over the past 90 days.
 *   3. If today > 3× average → trigger.
 *
 * Business rule: BR-5 from problem statement.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BehavioralDeviationRule implements DetectionRule {

    static final String RULE_CODE = "BEHAVIORAL_DEVIATION";

    private final AmlRuleProperties props;
    private final TransactionRepository transactionRepository;

    @Override
    public String getRuleCode() {
        return RULE_CODE;
    }

    @Override
    public Optional<DetectionResult> evaluate(Transaction transaction) {
        AmlRuleProperties.BehavioralDeviation cfg = props.getBehavioralDeviation();
        String customerId = transaction.getCustomer().getCustomerId();

        LocalDate today       = transaction.getTransactionTimestamp().toLocalDate();
        LocalDateTime dayStart = today.atStartOfDay();
        LocalDateTime dayEnd   = today.atTime(LocalTime.MAX);

        // Total INR volume for this customer today (includes the current transaction)
        BigDecimal todayVolume = transactionRepository
                .sumDailyVolumeInr(customerId, dayStart, dayEnd);

        if (todayVolume == null || todayVolume.compareTo(BigDecimal.ZERO) <= 0) {
            return Optional.empty();
        }

        // Rolling average over past N days (exclude today to avoid self-inflation)
        LocalDateTime rollingStart = dayStart.minusDays(cfg.getRollingDays());
        BigDecimal rollingAvg = transactionRepository
                .avgDailyVolumeInr(customerId, rollingStart);

        // Not enough history to establish baseline — skip
        if (rollingAvg == null || rollingAvg.compareTo(BigDecimal.ZERO) <= 0) {
            log.debug("BEHAVIORAL_DEVIATION: no rolling history for customer {} — skipping",
                    customerId);
            return Optional.empty();
        }

        BigDecimal threshold = rollingAvg.multiply(
                BigDecimal.valueOf(cfg.getMultiplierThreshold())
        ).setScale(2, RoundingMode.HALF_UP);

        if (todayVolume.compareTo(threshold) > 0) {
            BigDecimal multiple = todayVolume.divide(rollingAvg, 2, RoundingMode.HALF_UP);

            String explanation = String.format(
                    "Behavioral deviation detected for customer %s: today's transaction volume " +
                    "₹%,.2f is %.2f× the %d-day rolling average of ₹%,.2f " +
                    "(threshold: %.1f×). Triggering transaction: %s.",
                    customerId, todayVolume, multiple.doubleValue(),
                    cfg.getRollingDays(), rollingAvg,
                    cfg.getMultiplierThreshold(),
                    transaction.getTransactionRef());

            log.debug("BEHAVIORAL_DEVIATION rule TRIGGERED for customer {}: {}× of average",
                    customerId, multiple);

            return Optional.of(DetectionResult.builder()
                    .ruleCode(RULE_CODE)
                    .explanation(explanation)
                    .suggestedRiskScore(70)
                    .evidenceTransactionRefs(List.of(transaction.getTransactionRef()))
                    .build());
        }

        return Optional.empty();
    }
}
