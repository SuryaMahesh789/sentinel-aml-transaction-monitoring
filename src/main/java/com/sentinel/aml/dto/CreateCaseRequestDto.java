package com.sentinel.aml.dto;

import com.sentinel.aml.entity.Case.Priority;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class CreateCaseRequestDto {

    /** The alert that triggered the investigation */
    @NotNull(message = "alertId is required")
    private Long alertId;

    @Size(max = 100, message = "assignedAnalystId must be 100 characters or fewer")
    private String assignedAnalystId;

    /** Defaults to MEDIUM if not provided */
    private Priority priority;

    @Size(max = 200, message = "title must be 200 characters or fewer")
    private String title;

    private String investigationNotes;
}
