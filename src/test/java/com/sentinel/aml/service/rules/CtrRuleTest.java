package com.sentinel.aml.service.rules;

import com.sentinel.aml.config.AmlRuleProperties;
import com.sentinel.aml.entity.Account;
import com.sentinel.aml.entity.Customer;
import com.sentinel.aml.entity.Transaction;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class CtrRuleTest {

    private CtrRule rule;
    private AmlRuleProperties props;

    @BeforeEach
    void setUp() {
        props = new AmlRuleProperties();
        // threshold = 833000 INR (default from AmlRuleProperties)
        rule = new CtrRule(props);
    }

    // -------------------------------------------------------------------------
    // Helper: builds a minimal Transaction with the given INR amount
    // -------------------------------------------------------------------------
    private Transaction txn(BigDecimal amountInr) {
        Customer customer = new Customer();
        customer.setCustomerId("CUST_01");

        Account account = new Account();
        account.setAccountId("ACC_01");

        Transaction t = new Transaction();
        t.setTransactionRef("TXN-CTR-001");
        t.setAmountInr(amountInr);
        t.setAmount(amountInr);
        t.setCurrency("INR");
        t.setTransactionType(Transaction.TransactionType.DEPOSIT);
        t.setTransactionTimestamp(LocalDateTime.now());
        t.setSourceAccount(account);
        t.setCustomer(customer);
        return t;
    }

    @Test
    void shouldNotTriggerWhenAmountBelowThreshold() {
        // 832,999 INR — just under the 833,000 threshold
        Optional<DetectionResult> result = rule.evaluate(txn(new BigDecimal("832999.99")));
        assertThat(result).isEmpty();
    }

    @Test
    void shouldTriggerWhenAmountEqualsThreshold() {
        // Exactly at the threshold — must trigger
        Optional<DetectionResult> result = rule.evaluate(txn(new BigDecimal("833000.00")));
        assertThat(result).isPresent();
        assertThat(result.get().getRuleCode()).isEqualTo(CtrRule.RULE_CODE);
    }

    @Test
    void shouldTriggerWhenAmountAboveThreshold() {
        // 1,000,000 INR — well above threshold
        Optional<DetectionResult> result = rule.evaluate(txn(new BigDecimal("1000000.00")));
        assertThat(result).isPresent();
        assertThat(result.get().getExplanation()).contains("CTR threshold exceeded");
        assertThat(result.get().getSuggestedRiskScore()).isGreaterThan(0);
        assertThat(result.get().getEvidenceTransactionRefs()).contains("TXN-CTR-001");
    }

    @Test
    void shouldNotTriggerWhenAmountInrIsNull() {
        Optional<DetectionResult> result = rule.evaluate(txn(null));
        assertThat(result).isEmpty();
    }

    @Test
    void shouldRespectCustomThreshold() {
        // Override threshold to 500,000
        props.setCtrThresholdInr(new BigDecimal("500000"));

        // 600,000 should now trigger
        assertThat(rule.evaluate(txn(new BigDecimal("600000")))).isPresent();
        // 499,999 should not
        assertThat(rule.evaluate(txn(new BigDecimal("499999")))).isEmpty();
    }
}
