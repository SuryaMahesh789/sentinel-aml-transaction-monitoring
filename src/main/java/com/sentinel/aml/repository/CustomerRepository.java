package com.sentinel.aml.repository;

import com.sentinel.aml.entity.Customer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface CustomerRepository extends JpaRepository<Customer, Long> {

    Optional<Customer> findByCustomerId(String customerId);

    boolean existsByCustomerId(String customerId);

    boolean existsByEmail(String email);
}
