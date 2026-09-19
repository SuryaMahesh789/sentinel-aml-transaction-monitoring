package com.sentinel.aml.dto;

import com.sentinel.aml.entity.Case;
import com.sentinel.aml.entity.Case.CaseStatus;
import com.sentinel.aml.entity.Case.Priority;
import com.sentinel.aml.entity.CaseAlert;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Getter
@Builder
public class CaseDetailDto {

    // Case core fields
    private Long          id;
    private String        caseRef;
    private CaseStatus    status;
    private Priority      priority;
    private String        title;
    private String        description;
    private String        assignedTo;
    private LocalDateTime assignedAt;
    private String        investigationNotes;
    private String        resolutionReason;
    private String        closureNotes;
    private String        createdBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime closedAt;

    // Customer summary
    private String customerId;
    private String customerFirstName;
    private String customerLastName;
    private String customerRiskRating;

    // Linked alerts summary
    private List<LinkedAlertDto> linkedAlerts;

    public static CaseDetailDto from(Case c) {
        List<LinkedAlertDto> alerts = c.getCaseAlerts() == null ? List.of() :
                c.getCaseAlerts().stream()
                        .map(ca -> LinkedAlertDto.from(ca))
                        .toList();

        var b = CaseDetailDto.builder()
                .id(c.getId())
                .caseRef(c.getCaseRef())
                .status(c.getStatus())
                .priority(c.getPriority())
                .title(c.getTitle())
                .description(c.getDescription())
                .assignedTo(c.getAssignedTo())
                .assignedAt(c.getAssignedAt())
                .investigationNotes(c.getInvestigationNotes())
                .resolutionReason(c.getResolutionReason())
                .closureNotes(c.getClosureNotes())
                .createdBy(c.getCreatedBy())
                .createdAt(c.getCreatedAt())
                .updatedAt(c.getUpdatedAt())
                .closedAt(c.getClosedAt())
                .linkedAlerts(alerts);

        if (c.getCustomer() != null) {
            b.customerId(c.getCustomer().getCustomerId())
             .customerFirstName(c.getCustomer().getFirstName())
             .customerLastName(c.getCustomer().getLastName())
             .customerRiskRating(c.getCustomer().getRiskRating() != null
                     ? c.getCustomer().getRiskRating().name() : null);
        }
        return b.build();
    }

    @Getter
    @Builder
    public static class LinkedAlertDto {
        private Long       alertId;
        private String     alertRef;
        private String     ruleCode;
        private int        riskScore;
        private String     status;
        private String     transactionRef;
        private BigDecimal transactionAmountInr;
        private LocalDateTime linkedAt;

        public static LinkedAlertDto from(CaseAlert ca) {
            var alert = ca.getAlert();
            var b = LinkedAlertDto.builder()
                    .alertId(alert != null ? alert.getId() : null)
                    .alertRef(alert != null ? alert.getAlertRef() : null)
                    .ruleCode(alert != null && alert.getRule() != null
                            ? alert.getRule().getRuleCode() : null)
                    .riskScore(alert != null ? alert.getRiskScore() : 0)
                    .status(alert != null && alert.getStatus() != null
                            ? alert.getStatus().name() : null)
                    .linkedAt(ca.getLinkedAt());

            if (alert != null && alert.getTransaction() != null) {
                b.transactionRef(alert.getTransaction().getTransactionRef())
                 .transactionAmountInr(alert.getTransaction().getAmountInr());
            }
            return b.build();
        }
    }
}
