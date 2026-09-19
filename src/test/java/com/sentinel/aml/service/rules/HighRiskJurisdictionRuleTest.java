package com.sentinel.aml.service.rules;

import com.sentinel.aml.entity.Account;
import com.sentinel.aml.entity.Customer;
import com.sentinel.aml.entity.HighRiskJurisdiction;
import com.sentinel.aml.entity.Transaction;
import com.sentinel.aml.repository.HighRiskJurisdictionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HighRiskJurisdictionRuleTest {

    @Mock
    private HighRiskJurisdictionRepository jurisdictionRepository;

    private HighRiskJurisdictionRule rule;

    @BeforeEach
    void setUp() {
        rule = new HighRiskJurisdictionRule(jurisdictionRepository);
    }

    // -------------------------------------------------------------------------
    // Helper
    // -------------------------------------------------------------------------
    private Transaction txn(String ref, String jurisdiction, BigDecimal amountInr) {
        Customer customer = new Customer();
        customer.setCustomerId("CUST_01");

        Account account = new Account();
        account.setAccountId("ACC_01");

        Transaction t = new Transaction();
        t.setTransactionRef(ref);
        t.setAmountInr(amountInr);
        t.setAmount(amountInr);
        t.setCurrency("INR");
        t.setJurisdiction(jurisdiction);
        t.setTransactionType(Transaction.TransactionType.TRANSFER);
        t.setTransactionTimestamp(LocalDateTime.now());
        t.setSourceAccount(account);
        t.setCustomer(customer);
        return t;
    }

    private HighRiskJurisdiction hrj(String code, String name,
                                     HighRiskJurisdiction.RiskLevel level, String reason) {
        HighRiskJurisdiction h = new HighRiskJurisdiction();
        h.setCountryCode(code);
        h.setCountryName(name);
        h.setRiskLevel(level);
        h.setReason(reason);
        h.setActive(true);
        return h;
    }

    // -------------------------------------------------------------------------
    // Tests
    // -------------------------------------------------------------------------

    @Test
    void shouldTriggerForSanctionedJurisdiction() {
        HighRiskJurisdiction iran = hrj("IR", "Iran",
                HighRiskJurisdiction.RiskLevel.SANCTIONED,
                "OFAC comprehensive sanctions program");

        when(jurisdictionRepository.findByCountryCodeAndActiveTrue("IR"))
                .thenReturn(Optional.of(iran));

        // Even a tiny amount must trigger for sanctioned jurisdictions
        Optional<DetectionResult> result = rule.evaluate(txn("TXN-HR-001", "IR", new BigDecimal("100")));

        assertThat(result).isPresent();
        assertThat(result.get().getRuleCode()).isEqualTo(HighRiskJurisdictionRule.RULE_CODE);
        assertThat(result.get().getSuggestedRiskScore()).isEqualTo(95); // SANCTIONED = 95
        assertThat(result.get().getExplanation()).contains("Iran");
        assertThat(result.get().getExplanation()).contains("IR");
    }

    @Test
    void shouldTriggerForHighRiskJurisdiction() {
        HighRiskJurisdiction myanmar = hrj("MM", "Myanmar",
                HighRiskJurisdiction.RiskLevel.HIGH,
                "FATF High-Risk Jurisdiction");

        when(jurisdictionRepository.findByCountryCodeAndActiveTrue("MM"))
                .thenReturn(Optional.of(myanmar));

        Optional<DetectionResult> result = rule.evaluate(txn("TXN-HR-002", "MM", new BigDecimal("5000")));

        assertThat(result).isPresent();
        assertThat(result.get().getSuggestedRiskScore()).isEqualTo(85); // HIGH = 85
        assertThat(result.get().getExplanation()).contains("Myanmar");
    }

    @Test
    void shouldNotTriggerForNormalJurisdiction() {
        when(jurisdictionRepository.findByCountryCodeAndActiveTrue("US"))
                .thenReturn(Optional.empty());

        Optional<DetectionResult> result = rule.evaluate(txn("TXN-HR-003", "US", new BigDecimal("50000")));

        assertThat(result).isEmpty();
    }

    @Test
    void shouldNotTriggerWhenJurisdictionIsNull() {
        // Null jurisdiction — rule exits immediately without DB call
        Optional<DetectionResult> result = rule.evaluate(txn("TXN-HR-004", null, new BigDecimal("50000")));
        assertThat(result).isEmpty();
    }

    @Test
    void shouldNotTriggerWhenJurisdictionIsBlank() {
        Optional<DetectionResult> result = rule.evaluate(txn("TXN-HR-005", "   ", new BigDecimal("50000")));
        assertThat(result).isEmpty();
    }

    @Test
    void shouldNormaliseJurisdictionToUpperCase() {
        // Rule stores jurisdiction as uppercase; repo is queried with uppercase
        HighRiskJurisdiction kp = hrj("KP", "North Korea",
                HighRiskJurisdiction.RiskLevel.SANCTIONED, "UN sanctions");

        when(jurisdictionRepository.findByCountryCodeAndActiveTrue("KP"))
                .thenReturn(Optional.of(kp));

        // Pass lowercase "kp" — rule must uppercase it before lookup
        Optional<DetectionResult> result = rule.evaluate(txn("TXN-HR-006", "kp", new BigDecimal("1000")));

        assertThat(result).isPresent();
        assertThat(result.get().getExplanation()).contains("North Korea");
    }
}
