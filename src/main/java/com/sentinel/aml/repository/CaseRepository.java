package com.sentinel.aml.repository;

import com.sentinel.aml.entity.Case;
import com.sentinel.aml.entity.Case.CaseStatus;
import com.sentinel.aml.entity.Case.Priority;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CaseRepository extends JpaRepository<Case, Long> {

    Optional<Case> findByCaseRef(String caseRef);

    Page<Case> findAllByOrderByCreatedAtDesc(Pageable pageable);

    List<Case> findByAssignedToAndStatusNot(String assignedTo, CaseStatus status);

    List<Case> findByCustomer_CustomerIdOrderByCreatedAtDesc(String customerId);

    /** Filtered paginated query — all params optional (null = no filter) */
    @Query("SELECT c FROM Case c WHERE " +
           "(:status IS NULL OR c.status = :status) AND " +
           "(:assignedTo IS NULL OR c.assignedTo = :assignedTo) AND " +
           "(:priority IS NULL OR c.priority = :priority)")
    Page<Case> findByFilters(
            @Param("status")     CaseStatus status,
            @Param("assignedTo") String assignedTo,
            @Param("priority")   Priority priority,
            Pageable pageable);

    /** Find active (non-closed) cases already linked to a given alert */
    @Query("SELECT c FROM Case c JOIN c.caseAlerts ca " +
           "WHERE ca.alert.id = :alertId " +
           "AND c.status NOT IN ('CLOSED_SAR', 'CLOSED_NO_ACTION')")
    List<Case> findActiveCasesByAlertId(@Param("alertId") Long alertId);
}
