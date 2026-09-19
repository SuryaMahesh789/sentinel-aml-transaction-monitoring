package com.sentinel.aml.repository;

import com.sentinel.aml.entity.Alert;
import com.sentinel.aml.entity.Alert.AlertStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AlertRepository extends JpaRepository<Alert, Long> {

    Optional<Alert> findByAlertRef(String alertRef);

    Page<Alert> findAllByOrderByRiskScoreDescCreatedAtDesc(Pageable pageable);

    List<Alert> findByCustomer_CustomerIdAndStatus(String customerId, AlertStatus status);

    /** Filtered paginated query — status and ruleCode are optional (null = no filter) */
    @Query("SELECT a FROM Alert a WHERE " +
           "(:status IS NULL OR a.status = :status) AND " +
           "(:ruleCode IS NULL OR a.rule.ruleCode = :ruleCode)")
    Page<Alert> findByFilters(
            @Param("status") AlertStatus status,
            @Param("ruleCode") String ruleCode,
            Pageable pageable);

    /** Check for existing open alert for same customer + rule — used for de-duplication */
    @Query("SELECT a FROM Alert a WHERE a.customer.customerId = :customerId " +
           "AND a.rule.ruleCode = :ruleCode " +
           "AND a.status IN ('OPEN', 'UNDER_REVIEW')")
    List<Alert> findOpenAlertsByCustomerAndRule(
            @Param("customerId") String customerId,
            @Param("ruleCode") String ruleCode);

    /** Check for existing open alert for same transaction — used for de-duplication */
    @Query("SELECT a FROM Alert a WHERE a.transaction.transactionRef = :txnRef " +
           "AND a.rule.ruleCode = :ruleCode " +
           "AND a.status IN ('OPEN', 'UNDER_REVIEW')")
    List<Alert> findOpenAlertsByTransactionAndRule(
            @Param("txnRef") String txnRef,
            @Param("ruleCode") String ruleCode);

    long countByStatus(AlertStatus status);
}
