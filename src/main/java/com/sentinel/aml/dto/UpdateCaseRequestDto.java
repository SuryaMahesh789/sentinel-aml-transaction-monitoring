package com.sentinel.aml.dto;

import com.sentinel.aml.entity.Case.CaseStatus;
import com.sentinel.aml.entity.Case.Priority;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class UpdateCaseRequestDto {

    /** New status. Null = no change. */
    private CaseStatus status;

    @Size(max = 100)
    private String assignedAnalystId;

    private Priority priority;

    private String investigationNotes;

    /**
     * Required when status is CLOSED_SAR or CLOSED_NO_ACTION.
     * Must be provided to close a case.
     */
    private String resolutionReason;
}
