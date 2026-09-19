package com.sentinel.aml.dto;

import com.sentinel.aml.entity.Case;
import com.sentinel.aml.entity.Case.CaseStatus;
import com.sentinel.aml.entity.Case.Priority;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class CaseSummaryDto {

    private Long          id;
    private String        caseRef;
    private String        customerId;
    private CaseStatus    status;
    private Priority      priority;
    private String        assignedTo;
    private String        title;
    private int           linkedAlertCount;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime closedAt;

    public static CaseSummaryDto from(Case c) {
        return CaseSummaryDto.builder()
                .id(c.getId())
                .caseRef(c.getCaseRef())
                .customerId(c.getCustomer() != null ? c.getCustomer().getCustomerId() : null)
                .status(c.getStatus())
                .priority(c.getPriority())
                .assignedTo(c.getAssignedTo())
                .title(c.getTitle())
                .linkedAlertCount(c.getCaseAlerts() != null ? c.getCaseAlerts().size() : 0)
                .createdAt(c.getCreatedAt())
                .updatedAt(c.getUpdatedAt())
                .closedAt(c.getClosedAt())
                .build();
    }
}
