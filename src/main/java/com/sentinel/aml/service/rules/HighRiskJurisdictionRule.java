package com.sentinel.aml.service.rules;

import com.sentinel.aml.entity.HighRiskJurisdiction;
import com.sentinel.aml.entity.Transaction;
import com.sentinel.aml.repository.HighRiskJurisdictionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

/**
 * RULE 4 — High-Risk / Sanctioned Jurisdiction Transfer
 *
 * ANY transaction involving a counterparty jurisdiction on the high-risk or
 * sanctions list must generate an alert — regardless of amount.
 *
 * Uses the high_risk_jurisdictions reference table seeded in V2 migration.
 *
 * Business rule: BR-4 from problem statement.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class HighRiskJurisdictionRule implements DetectionRule {

    static final String RULE_CODE = "HIGH_RISK_JURISDICTION";

    private final HighRiskJurisdictionRepository jurisdictionRepository;

    @Override
    public String getRuleCode() {
        return RULE_CODE;
    }

    @Override
    public Optional<DetectionResult> evaluate(Transaction transaction) {
        String jurisdiction = transaction.getJurisdiction();
        if (jurisdiction == null || jurisdiction.isBlank()) {
            return Optional.empty();
        }

        Optional<HighRiskJurisdiction> match =
                jurisdictionRepository.findByCountryCodeAndActiveTrue(jurisdiction.toUpperCase());

        if (match.isPresent()) {
            HighRiskJurisdiction hrj = match.get();

            // SANCTIONED jurisdictions get a higher score than HIGH-risk
            int score = hrj.getRiskLevel() == HighRiskJurisdiction.RiskLevel.SANCTIONED ? 95 : 85;

            String explanation = String.format(
                    "Transaction involves %s jurisdiction %s (%s). " +
                    "Reason: %s. Transaction ref: %s, amount: ₹%,.2f INR.",
                    hrj.getRiskLevel().name().toLowerCase().replace("_", "-"),
                    hrj.getCountryName(), hrj.getCountryCode(),
                    hrj.getReason(),
                    transaction.getTransactionRef(),
                    transaction.getAmountInr() != null ? transaction.getAmountInr() : java.math.BigDecimal.ZERO);

            log.debug("HIGH_RISK_JURISDICTION rule TRIGGERED for txn {}: jurisdiction={}",
                    transaction.getTransactionRef(), jurisdiction);

            return Optional.of(DetectionResult.builder()
                    .ruleCode(RULE_CODE)
                    .explanation(explanation)
                    .suggestedRiskScore(score)
                    .evidenceTransactionRefs(List.of(transaction.getTransactionRef()))
                    .build());
        }

        return Optional.empty();
    }
}
