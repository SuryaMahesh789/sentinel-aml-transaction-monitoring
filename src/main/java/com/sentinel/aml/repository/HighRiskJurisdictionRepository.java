package com.sentinel.aml.repository;

import com.sentinel.aml.entity.HighRiskJurisdiction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface HighRiskJurisdictionRepository extends JpaRepository<HighRiskJurisdiction, Long> {

    Optional<HighRiskJurisdiction> findByCountryCodeAndActiveTrue(String countryCode);

    boolean existsByCountryCodeAndActiveTrue(String countryCode);
}
