package com.sentinel.aml.controller;

import com.sentinel.aml.dto.*;
import com.sentinel.aml.entity.Case.CaseStatus;
import com.sentinel.aml.entity.Case.Priority;
import com.sentinel.aml.service.CaseService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/api/v1/cases")
@RequiredArgsConstructor
@Tag(name = "Cases", description = "AML investigation case management endpoints")
public class CaseController {

    private final CaseService caseService;

    @Operation(
        summary = "Create a new investigation case",
        description = "Links an existing alert to a new case. " +
                      "Only one active case may exist per alert."
    )
    @PostMapping
    public ResponseEntity<CaseDetailDto> createCase(
            @Valid @RequestBody CreateCaseRequestDto request) {
        CaseDetailDto result = caseService.createCase(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(result);
    }

    @Operation(
        summary = "List cases (paginated)",
        description = "Returns cases sorted by createdAt DESC. " +
                      "Optionally filter by status, assignedAnalystId, or priority."
    )
    @GetMapping
    public ResponseEntity<Page<CaseSummaryDto>> listCases(
            @Parameter(description = "Filter by case status")
            @RequestParam(required = false) CaseStatus status,

            @Parameter(description = "Filter by assigned analyst ID")
            @RequestParam(required = false) String assignedAnalystId,

            @Parameter(description = "Filter by priority")
            @RequestParam(required = false) Priority priority,

            @Parameter(description = "Zero-based page index (default 0)")
            @RequestParam(defaultValue = "0") int page,

            @Parameter(description = "Page size, max 100 (default 20)")
            @RequestParam(defaultValue = "20") int size) {

        Page<CaseSummaryDto> result = caseService.listCases(status, assignedAnalystId, priority, page, size);
        return ResponseEntity.ok(result);
    }

    @Operation(
        summary = "Get case details",
        description = "Returns full case detail including linked alerts and customer information."
    )
    @GetMapping("/{id}")
    public ResponseEntity<CaseDetailDto> getCase(
            @Parameter(description = "Internal case database ID", required = true)
            @PathVariable Long id) {
        return ResponseEntity.ok(caseService.getCaseById(id));
    }

    @Operation(
        summary = "Update a case",
        description = "Update status, analyst assignment, priority, or investigation notes. " +
                      "Closing a case (CLOSED_SAR or CLOSED_NO_ACTION) requires a resolutionReason."
    )
    @PatchMapping("/{id}")
    public ResponseEntity<CaseDetailDto> updateCase(
            @Parameter(description = "Internal case database ID", required = true)
            @PathVariable Long id,
            @Valid @RequestBody UpdateCaseRequestDto request) {
        return ResponseEntity.ok(caseService.updateCase(id, request));
    }
}
