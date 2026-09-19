package com.sentinel.aml.dto;

import com.sentinel.aml.entity.Alert.AlertStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Request body for clearing / resolving an alert.
 *
 * Permitted target statuses for this endpoint:
 *   CLEARED, SAR_FILED, CLOSED, ESCALATED, UNDER_REVIEW
 *
 * OPEN is not a valid target (it is the initial state).
 */
@Getter
@Setter
@NoArgsConstructor
public class AlertDispositionRequestDto {

    @NotBlank(message = "analystId is required")
    @Size(max = 100, message = "analystId must be 100 characters or fewer")
    private String analystId;

    @NotNull(message = "targetStatus is required")
    private AlertStatus targetStatus;

    @NotBlank(message = "reason is required")
    @Size(max = 500, message = "reason must be 500 characters or fewer")
    private String reason;
}
