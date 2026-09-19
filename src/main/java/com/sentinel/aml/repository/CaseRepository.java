package com.sentinel.aml.repository;

import com.sentinel.aml.entity.Case;
import com.sentinel.aml.entity.Case.CaseStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CaseRepository extends JpaRepository<Case, Long> {

    Optional<Case> findByCaseRef(String caseRef);

    Page<Case> findAllByOrderByCreatedAtDesc(Pageable pageable);

    List<Case> findByAssignedToAndStatusNot(String assignedTo, CaseStatus status);

    List<Case> findByCustomer_CustomerIdOrderByCreatedAtDesc(String customerId);
}
