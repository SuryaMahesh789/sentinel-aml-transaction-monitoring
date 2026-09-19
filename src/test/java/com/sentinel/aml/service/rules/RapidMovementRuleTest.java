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
class RapidMovementRuleTest {

    @Mock
    private TransactionRepository transactionRepository;

    private RapidMovementRule rule;

    private static final String ACCOUNT_ID = "ACC_RAPID_01";
    private static final LocalDateTime NOW = LocalDateTime.of(2024, 6, 1, 12, 0, 0);

    @BeforeEach
    void setUp() {
        rule = new RapidMovementRule(new AmlRuleProperties(), transactionRepository);
    }

    // -------------------------------------------------------------------------
    // Helper: builds a WITHDRAWAL transaction
    // -------------------------------------------------------------------------
    private Transaction withdrawalTxn(String ref, BigDecimal amountInr) {
        Customer customer = new Customer();
        customer.setCustomerId("CUST_01");

        Account account = new Account();
        account.setAccountId(ACCOUNT_ID);

        Transaction t = new Transaction();
        t.setTransactionRef(ref);
        t.setAmountInr(amountInr);
        t.setAmount(amountInr);
        t.setCurrency("INR");
        t.setTransactionType(Transaction.TransactionType.WITHDRAWAL);
        t.setTransactionTimestamp(NOW);
        t.setSourceAccount(account);
        t.setCustomer(customer);
        return t;
    }

    private Transaction depositTxn(String ref, BigDecimal amountInr) {
        Transaction t = withdrawalTxn(ref, amountInr);
        t.setTransactionType(Transaction.TransactionType.DEPOSIT);
        return t;
    }

    // -------------------------------------------------------------------------
    // Tests
    // -------------------------------------------------------------------------

    @Test
    void shouldTriggerWhenOutflowExceedsEightyPercent() {
        // 100,000 deposited, 90,000 withdrawn (90%) — exceeds 80% threshold
        Transaction withdrawal = withdrawalTxn("TXN-W-001", new BigDecimal("90000"));

        when(transactionRepository.sumDepositsInr(eq(ACCOUNT_ID), any(), any()))
                .thenReturn(new BigDecimal("100000"));
        when(transactionRepository.sumOutflowsInr(eq(ACCOUNT_ID), any(), any()))
                .thenReturn(new BigDecimal("90000"));

        Optional<DetectionResult> result = rule.evaluate(withdrawal);

        assertThat(result).isPresent();
        assertThat(result.get().getRuleCode()).isEqualTo(RapidMovementRule.RULE_CODE);
        assertThat(result.get().getExplanation()).contains("Rapid fund movement");
        assertThat(result.get().getSuggestedRiskScore()).isGreaterThan(0);
    }

    @Test
    void shouldTriggerWhenOutflowEqualsExactlyEightyPercent() {
        // Exactly 80% — boundary condition, must trigger
        Transaction withdrawal = withdrawalTxn("TXN-W-002", new BigDecimal("80000"));

        when(transactionRepository.sumDepositsInr(eq(ACCOUNT_ID), any(), any()))
                .thenReturn(new BigDecimal("100000"));
        when(transactionRepository.sumOutflowsInr(eq(ACCOUNT_ID), any(), any()))
                .thenReturn(new BigDecimal("80000"));

        Optional<DetectionResult> result = rule.evaluate(withdrawal);

        assertThat(result).isPresent();
    }

    @Test
    void shouldNotTriggerWhenOutflowBelowEightyPercent() {
        // 100,000 deposited, 70,000 withdrawn (70%) — below threshold
        Transaction withdrawal = withdrawalTxn("TXN-W-003", new BigDecimal("70000"));

        when(transactionRepository.sumDepositsInr(eq(ACCOUNT_ID), any(), any()))
                .thenReturn(new BigDecimal("100000"));
        when(transactionRepository.sumOutflowsInr(eq(ACCOUNT_ID), any(), any()))
                .thenReturn(new BigDecimal("70000"));

        Optional<DetectionResult> result = rule.evaluate(withdrawal);

        assertThat(result).isEmpty();
    }

    @Test
    void shouldNotTriggerWhenNoDepositsExist() {
        // No deposits in the 48h window — cannot assess movement
        Transaction withdrawal = withdrawalTxn("TXN-W-004", new BigDecimal("50000"));

        when(transactionRepository.sumDepositsInr(eq(ACCOUNT_ID), any(), any()))
                .thenReturn(BigDecimal.ZERO);
        when(transactionRepository.sumOutflowsInr(eq(ACCOUNT_ID), any(), any()))
                .thenReturn(new BigDecimal("50000"));

        Optional<DetectionResult> result = rule.evaluate(withdrawal);

        assertThat(result).isEmpty();
    }

    @Test
    void shouldNotEvaluateDepositTransactions() {
        // Rule should only fire on outflow types — DEPOSIT should return empty immediately
        Transaction deposit = depositTxn("TXN-D-001", new BigDecimal("200000"));
        Optional<DetectionResult> result = rule.evaluate(deposit);
        assertThat(result).isEmpty();
    }

    @Test
    void shouldTriggerForTransferType() {
        // TRANSFER is also an outflow type
        Transaction transfer = withdrawalTxn("TXN-T-001", new BigDecimal("95000"));
        transfer.setTransactionType(Transaction.TransactionType.TRANSFER);

        when(transactionRepository.sumDepositsInr(eq(ACCOUNT_ID), any(), any()))
                .thenReturn(new BigDecimal("100000"));
        when(transactionRepository.sumOutflowsInr(eq(ACCOUNT_ID), any(), any()))
                .thenReturn(new BigDecimal("95000"));

        Optional<DetectionResult> result = rule.evaluate(transfer);
        assertThat(result).isPresent();
    }
}
