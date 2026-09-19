package com.sentinel.aml.service;

import com.sentinel.aml.dto.*;
import com.sentinel.aml.entity.*;
import com.sentinel.aml.entity.Case.CaseStatus;
import com.sentinel.aml.entity.Case.Priority;
import com.sentinel.aml.exception.DuplicateResourceException;
import com.sentinel.aml.exception.ResourceNotFoundException;
import com.sentinel.aml.exception.ValidationException;
import com.sentinel.aml.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class CaseService {

    private static final DateTimeFormatter REF_FMT =
            DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    /** Statuses considered "closed" — no further transitions allowed */
    private static final Set<CaseStatus> CLOSED_STATUSES =
            Set.of(CaseStatus.CLOSED_SAR, CaseStatus.CLOSED_NO_ACTION);

    private final CaseRepository      caseRepository;
    private final CaseAlertRepository caseAlertRepository;
    private final AlertRepository     alertRepository;

    // =========================================================================
    // Create
    // =========================================================================

    @Transactional
    public CaseDetailDto createCase(CreateCaseRequestDto req) {

        // 1. Validate alert exists
        Alert alert = alertRepository.findById(req.getAlertId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Alert not found with id: " + req.getAlertId()));

        // 2. Prevent duplicate active cases for the same alert
        List<Case> activeCases = caseRepository.findActiveCasesByAlertId(alert.getId());
        if (!activeCases.isEmpty()) {
            throw new DuplicateResourceException(
                    "An active case already exists for alert id " + req.getAlertId() +
                    " (caseRef=" + activeCases.get(0).getCaseRef() + ")");
        }

        // 3. Derive customer from the alert
        Customer customer = alert.getCustomer();

        // 4. Build title from alert if caller didn't provide one
        String title = (req.getTitle() != null && !req.getTitle().isBlank())
                ? req.getTitle()
                : "Investigation: " + alert.getRule().getRuleCode()
                  + " — " + alert.getAlertRef();

        // 5. Build and save case
        String caseRef = "CASE-" + LocalDateTime.now().format(REF_FMT)
                + "-" + UUID.randomUUID().toString().replace("-", "").substring(0, 6).toUpperCase();

        Case newCase = Case.builder()
                .caseRef(caseRef)
                .customer(customer)
                .title(title)
                .status(CaseStatus.OPEN)
                .priority(req.getPriority() != null ? req.getPriority() : Priority.MEDIUM)
                .assignedTo(req.getAssignedAnalystId())
                .assignedAt(req.getAssignedAnalystId() != null ? LocalDateTime.now() : null)
                .investigationNotes(req.getInvestigationNotes())
                .createdBy(req.getAssignedAnalystId())
                .build();

        Case saved = caseRepository.save(newCase);

        // 6. Link the alert to the case via the join table
        CaseAlert caseAlert = CaseAlert.builder()
                .linkedCase(saved)
                .alert(alert)
                .build();
        CaseAlert savedLink = caseAlertRepository.save(caseAlert);

        // Add the link directly to the in-memory collection so the DTO reflects it
        // (avoids lazy-load issue within the same transaction)
        saved.getCaseAlerts().add(savedLink);

        log.info("Case created: ref={}, alertId={}, status={}",
                saved.getCaseRef(), req.getAlertId(), saved.getStatus());
        return CaseDetailDto.from(saved);
    }

    // =========================================================================
    // Read
    // =========================================================================

    @Transactional(readOnly = true)
    public CaseDetailDto getCaseById(Long id) {
        Case c = caseRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Case not found with id: " + id));
        return CaseDetailDto.from(c);
    }

    @Transactional(readOnly = true)
    public Page<CaseSummaryDto> listCases(CaseStatus status, String assignedTo,
                                           Priority priority, int page, int size) {
        int clampedSize = Math.min(size, 100);
        Pageable pageable = PageRequest.of(page, clampedSize,
                Sort.by(Sort.Direction.DESC, "createdAt"));

        boolean noFilters = status == null
                && (assignedTo == null || assignedTo.isBlank())
                && priority == null;

        Page<Case> cases = noFilters
                ? caseRepository.findAll(pageable)
                : caseRepository.findByFilters(
                        status,
                        (assignedTo != null && assignedTo.isBlank()) ? null : assignedTo,
                        priority,
                        pageable);

        return cases.map(CaseSummaryDto::from);
    }

    // =========================================================================
    // Update
    // =========================================================================

    @Transactional
    public CaseDetailDto updateCase(Long id, UpdateCaseRequestDto req) {
        Case c = caseRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Case not found with id: " + id));

        // Cannot update a closed case
        if (CLOSED_STATUSES.contains(c.getStatus())) {
            throw new ValidationException(
                    "Case " + id + " is already closed (" + c.getStatus() + ") and cannot be modified.");
        }

        // Closing requires a resolution reason
        if (req.getStatus() != null && CLOSED_STATUSES.contains(req.getStatus())) {
            if (req.getResolutionReason() == null || req.getResolutionReason().isBlank()) {
                throw new ValidationException(
                        "A resolutionReason is required when closing a case.");
            }
            c.setClosedAt(LocalDateTime.now());
            c.setResolutionReason(req.getResolutionReason());
        }

        if (req.getStatus() != null) {
            c.setStatus(req.getStatus());
        }
        if (req.getAssignedAnalystId() != null) {
            c.setAssignedTo(req.getAssignedAnalystId());
            c.setAssignedAt(LocalDateTime.now());
        }
        if (req.getPriority() != null) {
            c.setPriority(req.getPriority());
        }
        if (req.getInvestigationNotes() != null) {
            c.setInvestigationNotes(req.getInvestigationNotes());
        }
        // Allow updating resolution reason on non-closing updates too
        if (req.getResolutionReason() != null && !CLOSED_STATUSES.contains(
                req.getStatus() != null ? req.getStatus() : c.getStatus())) {
            c.setResolutionReason(req.getResolutionReason());
        }

        Case saved = caseRepository.save(c);
        log.info("Case {} updated: status={}, assignedTo={}", id, saved.getStatus(), saved.getAssignedTo());
        return CaseDetailDto.from(saved);
    }
}
