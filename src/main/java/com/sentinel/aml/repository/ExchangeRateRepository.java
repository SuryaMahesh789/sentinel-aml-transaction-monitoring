package com.sentinel.aml.repository;

import com.sentinel.aml.entity.ExchangeRate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ExchangeRateRepository extends JpaRepository<ExchangeRate, Long> {

    @Query("SELECT e FROM ExchangeRate e WHERE e.fromCurrency = :currency " +
           "AND e.active = true ORDER BY e.effectiveFrom DESC LIMIT 1")
    Optional<ExchangeRate> findActiveRateForCurrency(@Param("currency") String currency);
}
