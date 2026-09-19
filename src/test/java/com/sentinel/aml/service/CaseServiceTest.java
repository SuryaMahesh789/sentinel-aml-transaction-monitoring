package com.sentinel.aml.service;

import com.sentinel.aml.dto.*;
import com.sentinel.aml.entity.*;
import com.sentinel.aml.entity.Case.CaseStatus;
import com.sentinel.aml.entity.Case.Priority;
import com.sentinel.aml.exception.DuplicateResourceException;
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

import static org.assertj.core.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class CaseServiceTest {

    @Autowired CaseService         caseService;
    @Autowired CaseRepository      caseRepository;
    @Autowired CaseAlertRepository caseAlertRepository;
    @Autowired AlertRepository     alertRepository;
    @Autowired AlertRuleRepository alertRuleRepository;
    @Autowired CustomerRepository  customerRepository;
    @Autowired AccountRepository   accountRepository;

    private Alert savedAlert;
    private Alert secondAlert;

    @BeforeEach
    void setUp() {
        Customer customer = Customer.builder()
                .customerId("CUST_CASE_01")
                .firstName("Test").lastName("Analyst")
                .kycStatus(Customer.KycStatus.VERIFIED)
                .riskRating(Customer.RiskRating.HIGH)
                .build();
        customerRepository.save(customer);

        Account account = Account.builder()
                .accountId("ACC_CASE_01").customer(customer)
                .accountType("SAVINGS")
                .accountStatus(Account.AccountStatus.ACTIVE)
                .currency("INR").currentBalance(BigDecimal.ZERO)
                .build();
        accountRepository.save(account);

        AlertRule rule = new AlertRule();
        rule.setRuleCode("CTR_THRESHOLD");
        rule.setRuleName("CTR Threshold");
        rule.setEnabled(true);
        rule.setBaseRiskScore(60);
        alertRuleRepository.save(rule);

        savedAlert = Alert.builder()
                .alertRef("ALT-CASE-001").customer(customer).account(account)
                .rule(rule).riskScore(60)
                .explanation("Large cash deposit")
                .status(Alert.AlertStatus.OPEN)
                .build();
        alertRepository.save(savedAlert);

        secondAlert = Alert.builder()
                .alertRef("ALT-CASE-002").customer(customer).account(account)
                .rule(rule).riskScore(75)
                .explanation("Structuring detected")
                .status(Alert.AlertStatus.OPEN)
                .build();
        alertRepository.save(secondAlert);
    }

    // =========================================================================
    // Create
    // =========================================================================

    @Test
    void shouldCreateCaseSuccessfully() {
        CreateCaseRequestDto req = new CreateCaseRequestDto();
        req.setAlertId(savedAlert.getId());
        req.setAssignedAnalystId("analyst_one");
        req.setPriority(Priority.HIGH);
        req.setInvestigationNotes("Initial review started");

        CaseDetailDto result = caseService.createCase(req);

        assertThat(result.getId()).isNotNull();
        assertThat(result.getCaseRef()).startsWith("CASE-");
        assertThat(result.getStatus()).isEqualTo(CaseStatus.OPEN);
        assertThat(result.getPriority()).isEqualTo(Priority.HIGH);
        assertThat(result.getAssignedTo()).isEqualTo("analyst_one");
        assertThat(result.getLinkedAlerts()).hasSize(1);
        assertThat(result.getLinkedAlerts().get(0).getAlertId()).isEqualTo(savedAlert.getId());
    }

    @Test
    void shouldDefaultPriorityToMediumWhenNotProvided() {
        CreateCaseRequestDto req = new CreateCaseRequestDto();
        req.setAlertId(savedAlert.getId());

        CaseDetailDto result = caseService.createCase(req);
        assertThat(result.getPriority()).isEqualTo(Priority.MEDIUM);
    }

    @Test
    void shouldGenerateTitleFromAlertWhenNotProvided() {
        CreateCaseRequestDto req = new CreateCaseRequestDto();
        req.setAlertId(savedAlert.getId());

        CaseDetailDto result = caseService.createCase(req);
        assertThat(result.getTitle()).contains("CTR_THRESHOLD");
    }

    @Test
    void shouldUseTitleFromRequestWhenProvided() {
        CreateCaseRequestDto req = new CreateCaseRequestDto();
        req.setAlertId(savedAlert.getId());
        req.setTitle("My Custom Title");

        CaseDetailDto result = caseService.createCase(req);
        assertThat(result.getTitle()).isEqualTo("My Custom Title");
    }

    @Test
    void shouldFailWhenAlertDoesNotExist() {
        CreateCaseRequestDto req = new CreateCaseRequestDto();
        req.setAlertId(99999L);

        assertThatThrownBy(() -> caseService.createCase(req))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("99999");
    }

    @Test
    void shouldRejectDuplicateActiveCaseForSameAlert() {
        CreateCaseRequestDto req = new CreateCaseRequestDto();
        req.setAlertId(savedAlert.getId());
        caseService.createCase(req); // first case

        assertThatThrownBy(() -> caseService.createCase(req))
                .isInstanceOf(DuplicateResourceException.class)
                .hasMessageContaining("active case already exists");
    }

    @Test
    void shouldAllowNewCaseForSameAlertAfterPreviousClosed() {
        CreateCaseRequestDto req = new CreateCaseRequestDto();
        req.setAlertId(savedAlert.getId());
        CaseDetailDto first = caseService.createCase(req);

        // Close the first case
        UpdateCaseRequestDto closeReq = new UpdateCaseRequestDto();
        closeReq.setStatus(CaseStatus.CLOSED_NO_ACTION);
        closeReq.setResolutionReason("False positive confirmed");
        caseService.updateCase(first.getId(), closeReq);

        // Now create a new case for the same alert — should succeed
        CaseDetailDto second = caseService.createCase(req);
        assertThat(second.getId()).isNotEqualTo(first.getId());
    }

    @Test
    void shouldPersistCaseToDatabase() {
        CreateCaseRequestDto req = new CreateCaseRequestDto();
        req.setAlertId(savedAlert.getId());

        CaseDetailDto result = caseService.createCase(req);

        assertThat(caseRepository.findById(result.getId())).isPresent();
        assertThat(caseAlertRepository.count()).isGreaterThanOrEqualTo(1);
    }

    // =========================================================================
    // Get
    // =========================================================================

    @Test
    void shouldGetCaseById() {
        CreateCaseRequestDto req = new CreateCaseRequestDto();
        req.setAlertId(savedAlert.getId());
        CaseDetailDto created = caseService.createCase(req);

        CaseDetailDto fetched = caseService.getCaseById(created.getId());
        assertThat(fetched.getId()).isEqualTo(created.getId());
        assertThat(fetched.getCaseRef()).isEqualTo(created.getCaseRef());
        assertThat(fetched.getCustomerId()).isEqualTo("CUST_CASE_01");
    }

    @Test
    void shouldThrowWhenCaseNotFound() {
        assertThatThrownBy(() -> caseService.getCaseById(88888L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("88888");
    }

    // =========================================================================
    // List
    // =========================================================================

    @Test
    void shouldReturnPaginatedCases() {
        CreateCaseRequestDto req1 = new CreateCaseRequestDto();
        req1.setAlertId(savedAlert.getId());
        caseService.createCase(req1);

        CreateCaseRequestDto req2 = new CreateCaseRequestDto();
        req2.setAlertId(secondAlert.getId());
        caseService.createCase(req2);

        Page<CaseSummaryDto> page = caseService.listCases(null, null, null, 0, 20);
        assertThat(page.getTotalElements()).isGreaterThanOrEqualTo(2);
    }

    @Test
    void shouldFilterByStatus() {
        CreateCaseRequestDto req = new CreateCaseRequestDto();
        req.setAlertId(savedAlert.getId());
        caseService.createCase(req);

        Page<CaseSummaryDto> open = caseService.listCases(CaseStatus.OPEN, null, null, 0, 20);
        assertThat(open.getContent()).allMatch(c -> c.getStatus() == CaseStatus.OPEN);
    }

    @Test
    void shouldFilterByAssignedAnalyst() {
        CreateCaseRequestDto req = new CreateCaseRequestDto();
        req.setAlertId(savedAlert.getId());
        req.setAssignedAnalystId("analyst_filter");
        caseService.createCase(req);

        Page<CaseSummaryDto> filtered = caseService.listCases(null, "analyst_filter", null, 0, 20);
        assertThat(filtered.getContent()).allMatch(c -> "analyst_filter".equals(c.getAssignedTo()));
    }

    @Test
    void shouldFilterByPriority() {
        CreateCaseRequestDto req = new CreateCaseRequestDto();
        req.setAlertId(savedAlert.getId());
        req.setPriority(Priority.CRITICAL);
        caseService.createCase(req);

        Page<CaseSummaryDto> filtered = caseService.listCases(null, null, Priority.CRITICAL, 0, 20);
        assertThat(filtered.getContent()).allMatch(c -> c.getPriority() == Priority.CRITICAL);
    }

    // =========================================================================
    // Update
    // =========================================================================

    @Test
    void shouldUpdateStatusToInProgress() {
        CreateCaseRequestDto req = new CreateCaseRequestDto();
        req.setAlertId(savedAlert.getId());
        CaseDetailDto created = caseService.createCase(req);

        UpdateCaseRequestDto update = new UpdateCaseRequestDto();
        update.setStatus(CaseStatus.IN_PROGRESS);

        CaseDetailDto result = caseService.updateCase(created.getId(), update);
        assertThat(result.getStatus()).isEqualTo(CaseStatus.IN_PROGRESS);
    }

    @Test
    void shouldAssignAnalyst() {
        CreateCaseRequestDto req = new CreateCaseRequestDto();
        req.setAlertId(savedAlert.getId());
        CaseDetailDto created = caseService.createCase(req);

        UpdateCaseRequestDto update = new UpdateCaseRequestDto();
        update.setAssignedAnalystId("analyst_assigned");

        CaseDetailDto result = caseService.updateCase(created.getId(), update);
        assertThat(result.getAssignedTo()).isEqualTo("analyst_assigned");
        assertThat(result.getAssignedAt()).isNotNull();
    }

    @Test
    void shouldUpdateInvestigationNotes() {
        CreateCaseRequestDto req = new CreateCaseRequestDto();
        req.setAlertId(savedAlert.getId());
        CaseDetailDto created = caseService.createCase(req);

        UpdateCaseRequestDto update = new UpdateCaseRequestDto();
        update.setInvestigationNotes("Customer contacted. Awaiting documentation.");

        CaseDetailDto result = caseService.updateCase(created.getId(), update);
        assertThat(result.getInvestigationNotes()).contains("Awaiting documentation");
    }

    @Test
    void shouldCloseCaseWithResolutionReason() {
        CreateCaseRequestDto req = new CreateCaseRequestDto();
        req.setAlertId(savedAlert.getId());
        CaseDetailDto created = caseService.createCase(req);

        UpdateCaseRequestDto close = new UpdateCaseRequestDto();
        close.setStatus(CaseStatus.CLOSED_NO_ACTION);
        close.setResolutionReason("Verified as legitimate corporate payroll transfer");

        CaseDetailDto result = caseService.updateCase(created.getId(), close);

        assertThat(result.getStatus()).isEqualTo(CaseStatus.CLOSED_NO_ACTION);
        assertThat(result.getClosedAt()).isNotNull();
        assertThat(result.getResolutionReason()).contains("payroll transfer");
    }

    @Test
    void shouldCloseWithSarFiled() {
        CreateCaseRequestDto req = new CreateCaseRequestDto();
        req.setAlertId(savedAlert.getId());
        CaseDetailDto created = caseService.createCase(req);

        UpdateCaseRequestDto close = new UpdateCaseRequestDto();
        close.setStatus(CaseStatus.CLOSED_SAR);
        close.setResolutionReason("SAR filed with FIU on 2024-09-15");

        CaseDetailDto result = caseService.updateCase(created.getId(), close);
        assertThat(result.getStatus()).isEqualTo(CaseStatus.CLOSED_SAR);
        assertThat(result.getClosedAt()).isNotNull();
    }

    @Test
    void shouldFailWhenClosingWithoutResolutionReason() {
        CreateCaseRequestDto req = new CreateCaseRequestDto();
        req.setAlertId(savedAlert.getId());
        CaseDetailDto created = caseService.createCase(req);

        UpdateCaseRequestDto close = new UpdateCaseRequestDto();
        close.setStatus(CaseStatus.CLOSED_NO_ACTION);
        // resolutionReason intentionally omitted

        assertThatThrownBy(() -> caseService.updateCase(created.getId(), close))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("resolutionReason is required");
    }

    @Test
    void shouldFailWhenUpdatingClosedCase() {
        CreateCaseRequestDto req = new CreateCaseRequestDto();
        req.setAlertId(savedAlert.getId());
        CaseDetailDto created = caseService.createCase(req);

        // Close it first
        UpdateCaseRequestDto close = new UpdateCaseRequestDto();
        close.setStatus(CaseStatus.CLOSED_NO_ACTION);
        close.setResolutionReason("Closed");
        caseService.updateCase(created.getId(), close);

        // Try updating again
        UpdateCaseRequestDto update = new UpdateCaseRequestDto();
        update.setStatus(CaseStatus.IN_PROGRESS);

        assertThatThrownBy(() -> caseService.updateCase(created.getId(), update))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("already closed");
    }

    @Test
    void shouldNotDeleteCaseAfterClosure() {
        CreateCaseRequestDto req = new CreateCaseRequestDto();
        req.setAlertId(savedAlert.getId());
        CaseDetailDto created = caseService.createCase(req);

        UpdateCaseRequestDto close = new UpdateCaseRequestDto();
        close.setStatus(CaseStatus.CLOSED_NO_ACTION);
        close.setResolutionReason("Confirmed false positive");
        caseService.updateCase(created.getId(), close);

        // Case must still exist in the database
        assertThat(caseRepository.findById(created.getId())).isPresent();
        assertThat(caseRepository.count()).isGreaterThanOrEqualTo(1);
    }

    @Test
    void shouldSetTimestampsOnCreate() {
        CreateCaseRequestDto req = new CreateCaseRequestDto();
        req.setAlertId(savedAlert.getId());

        CaseDetailDto result = caseService.createCase(req);
        assertThat(result.getCreatedAt()).isNotNull();
        assertThat(result.getUpdatedAt()).isNotNull();
        assertThat(result.getClosedAt()).isNull();
    }
}
