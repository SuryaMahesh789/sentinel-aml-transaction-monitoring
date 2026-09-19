package com.sentinel.aml.controller;

import com.sentinel.aml.dto.AlertDetailDto;
import com.sentinel.aml.dto.AlertDispositionRequestDto;
import com.sentinel.aml.dto.AlertSummaryDto;
import com.sentinel.aml.entity.Alert.AlertStatus;
import com.sentinel.aml.service.AlertService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/api/v1/alerts")
@RequiredArgsConstructor
@Tag(name = "Alerts", description = "AML alert queue management endpoints")
public class AlertController {

    private final AlertService alertService;

    // -------------------------------------------------------------------------
    // GET /api/v1/alerts
    // -------------------------------------------------------------------------
    @Operation(
        summary = "List alerts (paginated)",
        description = "Returns a paginated, sorted list of alerts. " +
                      "Filter by status and/or rule code. " +
                      "Default sort: risk score descending, then created date descending."
    )
    @GetMapping
    public ResponseEntity<Page<AlertSummaryDto>> listAlerts(
            @Parameter(description = "Filter by alert status")
            @RequestParam(required = false) AlertStatus status,

            @Parameter(description = "Filter by rule code, e.g. CTR_THRESHOLD")
            @RequestParam(required = false) String ruleCode,

            @Parameter(description = "Zero-based page index (default 0)")
            @RequestParam(defaultValue = "0") int page,

            @Parameter(description = "Page size, max 100 (default 20)")
            @RequestParam(defaultValue = "20") int size,

            @Parameter(description = "Sort field: riskScore (default) or createdAt")
            @RequestParam(defaultValue = "riskScore") String sortBy) {

        Page<AlertSummaryDto> result = alertService.listAlerts(status, ruleCode, page, size, sortBy);
        return ResponseEntity.ok(result);
    }

    // -------------------------------------------------------------------------
    // GET /api/v1/alerts/{id}
    // -------------------------------------------------------------------------
    @Operation(
        summary = "Get alert details",
        description = "Returns full alert details including related transaction, " +
                      "customer, account, and rule information."
    )
    @GetMapping("/{id}")
    public ResponseEntity<AlertDetailDto> getAlert(
            @Parameter(description = "Internal alert database ID", required = true)
            @PathVariable Long id) {

        AlertDetailDto detail = alertService.getAlertById(id);
        return ResponseEntity.ok(detail);
    }

    // -------------------------------------------------------------------------
    // PATCH /api/v1/alerts/{id}/disposition
    // -------------------------------------------------------------------------
    @Operation(
        summary = "Disposition (resolve/clear/escalate) an alert",
        description = "Updates the alert status with analyst ID, reason, and timestamp. " +
                      "Alerts are NEVER deleted. " +
                      "Valid target statuses: UNDER_REVIEW, ESCALATED, CLEARED, SAR_FILED, CLOSED."
    )
    @PatchMapping("/{id}/disposition")
    public ResponseEntity<AlertDetailDto> disposeAlert(
            @Parameter(description = "Internal alert database ID", required = true)
            @PathVariable Long id,

            @Valid @RequestBody AlertDispositionRequestDto request) {

        log.info("Alert disposition request: alertId={}, analyst={}, targetStatus={}",
                id, request.getAnalystId(), request.getTargetStatus());

        AlertDetailDto result = alertService.disposeAlert(id, request);
        return ResponseEntity.ok(result);
    }
}
