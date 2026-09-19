package com.sentinel.aml.repository;

import com.sentinel.aml.entity.CaseAlert;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CaseAlertRepository extends JpaRepository<CaseAlert, Long> {

    List<CaseAlert> findByLinkedCase_Id(Long caseId);

    List<CaseAlert> findByAlert_Id(Long alertId);
}
