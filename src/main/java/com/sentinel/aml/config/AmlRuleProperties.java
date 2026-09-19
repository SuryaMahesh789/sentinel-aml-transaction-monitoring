package com.sentinel.aml.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * Binds aml.rules.* from application.yml.
 * All thresholds are tunable without code redeployment.
 */
@Component
@ConfigurationProperties(prefix = "aml.rules")
@Getter
@Setter
public class AmlRuleProperties {

    /** Single-transaction CTR threshold in INR (~$10,000 USD) */
    private BigDecimal ctrThresholdInr = new BigDecimal("833000");

    private Structuring structuring = new Structuring();
    private RapidMovement rapidMovement = new RapidMovement();
    private BehavioralDeviation behavioralDeviation = new BehavioralDeviation();

    @Getter
    @Setter
    public static class Structuring {
        /** Lower bound of structuring range in INR (~$9,000 USD) */
        private BigDecimal minAmountInr = new BigDecimal("749700");
        /** Upper bound of structuring range in INR (~$9,999 USD) */
        private BigDecimal maxAmountInr = new BigDecimal("832999");
        /** Minimum number of such transactions within the window */
        private int countThreshold = 3;
        /** Time window in hours */
        private int windowHours = 24;
    }

    @Getter
    @Setter
    public static class RapidMovement {
        /** Percentage of deposited funds that must flow out to trigger (0–100) */
        private int outflowPercentThreshold = 80;
        /** Time window in hours after first deposit */
        private int windowHours = 48;
    }

    @Getter
    @Setter
    public static class BehavioralDeviation {
        /** Number of days for the rolling average baseline */
        private int rollingDays = 90;
        /** Multiplier above which a day's volume is considered anomalous */
        private double multiplierThreshold = 3.0;
    }
}
