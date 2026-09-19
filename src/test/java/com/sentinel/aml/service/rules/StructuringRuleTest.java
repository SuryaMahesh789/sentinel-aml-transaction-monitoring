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
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StructuringRuleTest {

    @Mock
    private TransactionRepository transactionRepository;

    private StructuringRule rule;
    private AmlRuleProperties props;

    // Structuring band defaults: 749700–832999 INR, count=3, window=24h
    private static final BigDecimal IN_BAND   = new BigDecimal("800000"); // ~$9,580
    private static final BigDecimal BELOW_BAND = new BigDecimal("600000");
    private static final BigDecimal ABOVE_BAND = new BigDecimal("900000");

    private static final String ACCOUNT_ID = "ACC_STRUCT_01";
    private static final LocalDateTime NOW  = LocalDateTime.of(2024, 6, 1, 12, 0, 0);

    @BeforeEach
    void setUp() {
        props = new AmlRuleProperties();
        rule  = new StructuringRule(props, transactionRepository);
    }

    // -------------------------------------------------------------------------
    // Helper
    // -------------------------------------------------------------------------
    private Transaction txn(String ref, BigDecimal amountInr, LocalDateTime timestamp) {
        Customer customer = new Customer();
        customer.setCustomerId("CUST_01");

        Account account = new Account();
        account.setAccountId(ACCOUNT_ID);

        Transaction t = new Transaction();
        t.setTransactionRef(ref);
        t.setAmountInr(amountInr);
        t.setAmount(amountInr);
        t.setCurrency("INR");
        t.setTransactionType(Transaction.TransactionType.DEPOSIT);
        t.setTransactionTimestamp(timestamp);
        t.setSourceAccount(account);
        t.setCustomer(customer);
        return t;
    }

    private Transaction currentTxn() {
        return txn("TXN-003", IN_BAND, NOW);
    }

    // -------------------------------------------------------------------------
    // Tests
    // -------------------------------------------------------------------------

    @Test
    void shouldTriggerWhenThreeInBandTransactionsWithin24Hours() {
        Transaction t1 = txn("TXN-001", IN_BAND, NOW.minusHours(10));
        Transaction t2 = txn("TXN-002", IN_BAND, NOW.minusHours(5));
        Transaction t3 = currentTxn(); // current

        when(transactionRepository.findByAccountAndTimeWindow(
                eq(ACCOUNT_ID), any(), any()))
                .thenReturn(List.of(t1, t2, t3));

        Optional<DetectionResult> result = rule.evaluate(t3);

        assertThat(result).isPresent();
        assertThat(result.get().getRuleCode()).isEqualTo(StructuringRule.RULE_CODE);
        assertThat(result.get().getExplanation()).contains("Structuring pattern");
        assertThat(result.get().getEvidenceTransactionRefs()).hasSize(3);
    }

    @Test
    void shouldNotTriggerWhenFewerThanThreeInBandTransactions() {
        Transaction t1 = txn("TXN-001", IN_BAND, NOW.minusHours(10));
        Transaction t2 = currentTxn();

        when(transactionRepository.findByAccountAndTimeWindow(
                eq(ACCOUNT_ID), any(), any()))
                .thenReturn(List.of(t1, t2));

        Optional<DetectionResult> result = rule.evaluate(t2);

        assertThat(result).isEmpty();
    }

    @Test
    void shouldNotTriggerWhenCurrentTransactionBelowBand() {
        // Current txn is below the structuring band — rule exits early
        Transaction t = txn("TXN-LOW", BELOW_BAND, NOW);
        Optional<DetectionResult> result = rule.evaluate(t);

        assertThat(result).isEmpty();
        // Repository should NOT be queried (not in band)
    }

    @Test
    void shouldNotTriggerWhenCurrentTransactionAboveBand() {
        Transaction t = txn("TXN-HIGH", ABOVE_BAND, NOW);
        Optional<DetectionResult> result = rule.evaluate(t);
        assertThat(result).isEmpty();
    }

    @Test
    void shouldNotCountOutOfWindowTransactionTowardThreshold() {
        // t1 is 25h ago — outside the 24h window, so repo returns only t2 + t3
        Transaction t2 = txn("TXN-002", IN_BAND, NOW.minusHours(5));
        Transaction t3 = currentTxn();

        // Repo simulates window-filtered query — only returns 2 in-window txns
        when(transactionRepository.findByAccountAndTimeWindow(
                eq(ACCOUNT_ID), any(), any()))
                .thenReturn(List.of(t2, t3)); // t1 excluded by DB query

        Optional<DetectionResult> result = rule.evaluate(t3);

        // Only 2 in-band transactions -> no alert
        assertThat(result).isEmpty();
    }

    @Test
    void shouldNotTriggerWhenThreeTransactionsButOneMixedOutOfBand() {
        Transaction t1 = txn("TXN-001", IN_BAND,    NOW.minusHours(10));
        Transaction t2 = txn("TXN-002", ABOVE_BAND, NOW.minusHours(5)); // out of band
        Transaction t3 = currentTxn();

        when(transactionRepository.findByAccountAndTimeWindow(
                eq(ACCOUNT_ID), any(), any()))
                .thenReturn(List.of(t1, t2, t3));

        Optional<DetectionResult> result = rule.evaluate(t3);

        // Only t1 and t3 are in-band → count=2 → no alert
        assertThat(result).isEmpty();
    }
}
