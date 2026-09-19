package com.sentinel.aml.service;

import com.sentinel.aml.dto.AlertDetailDto;
import com.sentinel.aml.dto.AlertDispositionRequestDto;
import com.sentinel.aml.dto.AlertSummaryDto;
import com.sentinel.aml.entity.*;
import com.sentinel.aml.entity.Alert.AlertStatus;
import com.sentinel.aml.exception.ResourceNotFoundException;
import com.sentinel.aml.exception.ValidationException;
import com.sentinel.aml.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class AlertManagementServiceTest {

    @Autowired AlertService        alertService;
    @Autowired AlertRepository     alertRepository;
    @Autowired AlertRuleRepository alertRuleRepository;
    @Autowired CustomerRepository  customerRepository;
    @Autowired AccountRepository   accountRepository;

    private Alert savedAlert;

    @BeforeEach
    void setUp() {
        // Customer
        Customer customer = Customer.builder()
                .customerId("CUST_AM_01")
                .firstName("Ana")
                .lastName("Test")
                .kycStatus(Customer.KycStatus.VERIFIED)
                .riskRating(Customer.RiskRating.HIGH)
                .build();
        customerRepository.save(customer);

        // Account
        Account account = Account.builder()
                .accountId("ACC_AM_01")
                .customer(customer)
                .accountType("SAVINGS")
                .accountStatus(Account.AccountStatus.ACTIVE)
                .currency("INR")
                .currentBalance(BigDecimal.ZERO)
                .build();
        accountRepository.save(account);

        // AlertRule
        AlertRule rule = new AlertRule();
        rule.setRuleCode("CTR_THRESHOLD");
        rule.setRuleName("CTR Threshold");
        rule.setEnabled(true);
        rule.setBaseRiskScore(60);
        alertRuleRepository.save(rule);

        // Alert — no transaction FK to avoid H2 constraint issue in unit tests
        Alert alert = Alert.builder()
                .alertRef("ALT-TEST-001")
                .customer(customer)
                .account(account)
                .rule(rule)
                .riskScore(60)
                .explanation("Test CTR alert explanation")
                .status(AlertStatus.OPEN)
                .build();
        savedAlert = alertRepository.save(alert);
    }

    // -------------------------------------------------------------------------
    // listAlerts
    // -------------------------------------------------------------------------

    @Test
    void shouldReturnPaginatedAlerts() {
        Page<AlertSummaryDto> page = alertService.listAlerts(null, null, 0, 20, "riskScore");
        assertThat(page.getTotalElements()).isGreaterThanOrEqualTo(1);
        assertThat(page.getContent()).isNotEmpty();
    }

    @Test
    void shouldFilterByStatus() {
        Page<AlertSummaryDto> open = alertService.listAlerts(AlertStatus.OPEN, null, 0, 20, "riskScore");
        assertThat(open.getContent()).allMatch(a -> a.getStatus() == AlertStatus.OPEN);
    }

    @Test
    void shouldFilterByRuleCode() {
        Page<AlertSummaryDto> filtered = alertService.listAlerts(null, "CTR_THRESHOLD", 0, 20, "riskScore");
        assertThat(filtered.getContent()).allMatch(a -> "CTR_THRESHOLD".equals(a.getRuleCode()));
    }

    @Test
    void shouldReturnEmptyPageWhenNoMatchForStatus() {
        Page<AlertSummaryDto> cleared = alertService.listAlerts(AlertStatus.CLEARED, null, 0, 20, "riskScore");
        assertThat(cleared.getTotalElements()).isZero();
    }

    @Test
    void shouldMaskCustomerIdInListView() {
        Page<AlertSummaryDto> page = alertService.listAlerts(null, null, 0, 20, "riskScore");
        AlertSummaryDto dto = page.getContent().get(0);
        // Full customerId is "CUST_AM_01"; masked should not equal full value
        assertThat(dto.getCustomerId()).isNotEqualTo("CUST_AM_01");
        assertThat(dto.getCustomerId()).endsWith("****");
    }

    // -------------------------------------------------------------------------
    // getAlertById
    // -------------------------------------------------------------------------

    @Test
    void shouldReturnFullDetailById() {
        AlertDetailDto detail = alertService.getAlertById(savedAlert.getId());
        assertThat(detail.getId()).isEqualTo(savedAlert.getId());
        assertThat(detail.getAlertRef()).isEqualTo("ALT-TEST-001");
        assertThat(detail.getRuleCode()).isEqualTo("CTR_THRESHOLD");
        assertThat(detail.getRiskScore()).isEqualTo(60);
        assertThat(detail.getExplanation()).contains("CTR");
        // Detail view shows full customer ID (not masked)
        assertThat(detail.getCustomerId()).isEqualTo("CUST_AM_01");
    }

    @Test
    void shouldThrowWhenAlertNotFound() {
        assertThatThrownBy(() -> alertService.getAlertById(99999L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("99999");
    }

    // -------------------------------------------------------------------------
    // disposeAlert
    // -------------------------------------------------------------------------

    @Test
    void shouldClearAlertWithAnalystAndReason() {
        AlertDispositionRequestDto req = new AlertDispositionRequestDto();
        req.setAnalystId("analyst_jane");
        req.setTargetStatus(AlertStatus.CLEARED);
        req.setReason("Verified as false positive — internal test transfer");

        AlertDetailDto result = alertService.disposeAlert(savedAlert.getId(), req);

        assertThat(result.getStatus()).isEqualTo(AlertStatus.CLEARED);
        assertThat(result.getReviewedBy()).isEqualTo("analyst_jane");
        assertThat(result.getClearanceReason()).contains("false positive");
        assertThat(result.getReviewedAt()).isNotNull();
    }

    @Test
    void shouldPersistDispositionToDatabaseNotDelete() {
        AlertDispositionRequestDto req = new AlertDispositionRequestDto();
        req.setAnalystId("analyst_bob");
        req.setTargetStatus(AlertStatus.CLOSED);
        req.setReason("Investigated and closed");

        alertService.disposeAlert(savedAlert.getId(), req);

        // Alert must still exist
        assertThat(alertRepository.findById(savedAlert.getId())).isPresent();
        Alert updated = alertRepository.findById(savedAlert.getId()).get();
        assertThat(updated.getStatus()).isEqualTo(AlertStatus.CLOSED);
    }

    @Test
    void shouldThrowWhenDisposingAlreadyClearedAlert() {
        // First clear
        AlertDispositionRequestDto req1 = new AlertDispositionRequestDto();
        req1.setAnalystId("analyst_a");
        req1.setTargetStatus(AlertStatus.CLEARED);
        req1.setReason("First clear");
        alertService.disposeAlert(savedAlert.getId(), req1);

        // Attempt second disposition
        AlertDispositionRequestDto req2 = new AlertDispositionRequestDto();
        req2.setAnalystId("analyst_b");
        req2.setTargetStatus(AlertStatus.CLOSED);
        req2.setReason("Try again");

        assertThatThrownBy(() -> alertService.disposeAlert(savedAlert.getId(), req2))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("terminal status");
    }

    @Test
    void shouldThrowForInvalidTargetStatus() {
        AlertDispositionRequestDto req = new AlertDispositionRequestDto();
        req.setAnalystId("analyst_x");
        req.setTargetStatus(AlertStatus.OPEN); // OPEN is not valid as a disposition target
        req.setReason("Bad request");

        assertThatThrownBy(() -> alertService.disposeAlert(savedAlert.getId(), req))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("Invalid target status");
    }

    @Test
    void shouldThrowWhenDisposingNonExistentAlert() {
        AlertDispositionRequestDto req = new AlertDispositionRequestDto();
        req.setAnalystId("analyst_x");
        req.setTargetStatus(AlertStatus.CLEARED);
        req.setReason("Does not matter");

        assertThatThrownBy(() -> alertService.disposeAlert(88888L, req))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("88888");
    }

    @Test
    void shouldAllowEscalation() {
        AlertDispositionRequestDto req = new AlertDispositionRequestDto();
        req.setAnalystId("analyst_senior");
        req.setTargetStatus(AlertStatus.ESCALATED);
        req.setReason("Requires senior analyst review");

        AlertDetailDto result = alertService.disposeAlert(savedAlert.getId(), req);
        assertThat(result.getStatus()).isEqualTo(AlertStatus.ESCALATED);
    }
}
