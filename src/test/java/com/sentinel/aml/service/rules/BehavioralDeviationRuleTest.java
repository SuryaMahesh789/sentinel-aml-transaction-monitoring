package com.sentinel.aml.service.rules;

import com.sentinel.aml.config.AmlRuleProperties;
import com.sentinel.aml.entity.Account;
import com.sentinel.aml.entity.Customer;
import com.sentinel.aml.entity.Transaction;
import com.sentinel.aml.repository.TransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BehavioralDeviationRuleTest {

    @Mock
    private TransactionRepository transactionRepository;

    private BehavioralDeviationRule rule;

    private static final String CUSTOMER_ID = "CUST_BD_01";
    private static final LocalDateTime NOW  = LocalDateTime.of(2024, 6, 15, 14, 0, 0);

    @BeforeEach
    void setUp() {
        rule = new BehavioralDeviationRule(new AmlRuleProperties(), transactionRepository);
    }

    // -------------------------------------------------------------------------
    // Helper
    // -------------------------------------------------------------------------
    private Transaction txn(String ref, BigDecimal amountInr) {
        Customer customer = new Customer();
        customer.setCustomerId(CUSTOMER_ID);

        Account account = new Account();
        account.setAccountId("ACC_BD_01");

        Transaction t = new Transaction();
        t.setTransactionRef(ref);
        t.setAmountInr(amountInr);
        t.setAmount(amountInr);
        t.setCurrency("INR");
        t.setTransactionType(Transaction.TransactionType.DEPOSIT);
        t.setTransactionTimestamp(NOW);
        t.setSourceAccount(account);
        t.setCustomer(customer);
        return t;
    }

    // -------------------------------------------------------------------------
    // Tests
    // -------------------------------------------------------------------------

    @Test
    void shouldTriggerWhenDailyVolumeExceedsThreexAverage() {
        // Rolling avg = 10,000 INR/day → threshold = 30,000
        // Today's volume = 40,000 → triggers (> 3×)
        Transaction t = txn("TXN-BD-001", new BigDecimal("40000"));

        when(transactionRepository.sumDailyVolumeInr(eq(CUSTOMER_ID), any(), any()))
                .thenReturn(new BigDecimal("40000"));
        when(transactionRepository.avgDailyVolumeInr(eq(CUSTOMER_ID), any()))
                .thenReturn(new BigDecimal("10000"));

        Optional<DetectionResult> result = rule.evaluate(t);

        assertThat(result).isPresent();
        assertThat(result.get().getRuleCode()).isEqualTo(BehavioralDeviationRule.RULE_CODE);
        assertThat(result.get().getExplanation()).contains("Behavioral deviation");
        assertThat(result.get().getSuggestedRiskScore()).isGreaterThan(0);
    }

    @Test
    void shouldNotTriggerWhenDailyVolumeEqualsExactlyThreexAverage() {
        // Today = exactly 3× average → does NOT trigger (rule uses > not >=)
        Transaction t = txn("TXN-BD-002", new BigDecimal("30000"));

        when(transactionRepository.sumDailyVolumeInr(eq(CUSTOMER_ID), any(), any()))
                .thenReturn(new BigDecimal("30000"));
        when(transactionRepository.avgDailyVolumeInr(eq(CUSTOMER_ID), any()))
                .thenReturn(new BigDecimal("10000"));

        Optional<DetectionResult> result = rule.evaluate(t);

        assertThat(result).isEmpty();
    }

    @Test
    void shouldNotTriggerWhenDailyVolumeBelowThreexAverage() {
        // Today = 20,000, avg = 10,000 → 2× → no trigger
        Transaction t = txn("TXN-BD-003", new BigDecimal("20000"));

        when(transactionRepository.sumDailyVolumeInr(eq(CUSTOMER_ID), any(), any()))
                .thenReturn(new BigDecimal("20000"));
        when(transactionRepository.avgDailyVolumeInr(eq(CUSTOMER_ID), any()))
                .thenReturn(new BigDecimal("10000"));

        Optional<DetectionResult> result = rule.evaluate(t);

        assertThat(result).isEmpty();
    }

    @Test
    void shouldNotTriggerWhenNoRollingHistoryAvailable() {
        // No historical data — baseline = 0, rule must skip safely
        Transaction t = txn("TXN-BD-004", new BigDecimal("1000000"));

        when(transactionRepository.sumDailyVolumeInr(eq(CUSTOMER_ID), any(), any()))
                .thenReturn(new BigDecimal("1000000"));
        when(transactionRepository.avgDailyVolumeInr(eq(CUSTOMER_ID), any()))
                .thenReturn(BigDecimal.ZERO);

        Optional<DetectionResult> result = rule.evaluate(t);

        assertThat(result).isEmpty();
    }

    @Test
    void shouldNotTriggerWhenNullRollingAverage() {
        Transaction t = txn("TXN-BD-005", new BigDecimal("500000"));

        when(transactionRepository.sumDailyVolumeInr(eq(CUSTOMER_ID), any(), any()))
                .thenReturn(new BigDecimal("500000"));
        when(transactionRepository.avgDailyVolumeInr(eq(CUSTOMER_ID), any()))
                .thenReturn(null);

        Optional<DetectionResult> result = rule.evaluate(t);

        assertThat(result).isEmpty();
    }

    @Test
    void shouldNotTriggerWhenTodayVolumeIsZero() {
        Transaction t = txn("TXN-BD-006", BigDecimal.ZERO);

        when(transactionRepository.sumDailyVolumeInr(eq(CUSTOMER_ID), any(), any()))
                .thenReturn(BigDecimal.ZERO);

        Optional<DetectionResult> result = rule.evaluate(t);

        assertThat(result).isEmpty();
    }

    @Test
    void shouldIncludeCustomerIdInExplanation() {
        Transaction t = txn("TXN-BD-007", new BigDecimal("50000"));

        when(transactionRepository.sumDailyVolumeInr(eq(CUSTOMER_ID), any(), any()))
                .thenReturn(new BigDecimal("50000"));
        when(transactionRepository.avgDailyVolumeInr(eq(CUSTOMER_ID), any()))
                .thenReturn(new BigDecimal("10000"));

        Optional<DetectionResult> result = rule.evaluate(t);

        assertThat(result).isPresent();
        assertThat(result.get().getExplanation()).contains(CUSTOMER_ID);
        assertThat(result.get().getEvidenceTransactionRefs()).contains("TXN-BD-007");
    }
}
