package com.sentinel.aml.repository;

import com.sentinel.aml.entity.Account;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AccountRepository extends JpaRepository<Account, Long> {

    Optional<Account> findByAccountId(String accountId);

    boolean existsByAccountId(String accountId);

    List<Account> findByCustomer_CustomerId(String customerId);
}
