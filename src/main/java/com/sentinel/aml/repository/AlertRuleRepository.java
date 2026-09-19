package com.sentinel.aml.repository;

import com.sentinel.aml.entity.AlertRule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AlertRuleRepository extends JpaRepository<AlertRule, Long> {

    Optional<AlertRule> findByRuleCode(String ruleCode);

    List<AlertRule> findByEnabledTrue();
}
