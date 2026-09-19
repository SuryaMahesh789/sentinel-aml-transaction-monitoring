package com.sentinel.aml.repository;

import com.sentinel.aml.entity.Transaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface TransactionRepository extends JpaRepository<Transaction, Long> {

    Optional<Transaction> findByTransactionRef(String transactionRef);

    boolean existsByTransactionRef(String transactionRef);

    /** All transactions for an account within a time window — used by Structuring + Rapid Movement rules */
    @Query("SELECT t FROM Transaction t WHERE t.sourceAccount.accountId = :accountId " +
           "AND t.transactionTimestamp BETWEEN :from AND :to " +
           "ORDER BY t.transactionTimestamp ASC")
    List<Transaction> findByAccountAndTimeWindow(
            @Param("accountId") String accountId,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to);

    /** Sum of deposits into an account within a time window — used by Rapid Movement rule */
    @Query("SELECT COALESCE(SUM(t.amountInr), 0) FROM Transaction t " +
           "WHERE t.sourceAccount.accountId = :accountId " +
           "AND t.transactionType = com.sentinel.aml.entity.Transaction$TransactionType.DEPOSIT " +
           "AND t.transactionTimestamp BETWEEN :from AND :to")
    BigDecimal sumDepositsInr(
            @Param("accountId") String accountId,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to);

    /** Sum of outflows (WITHDRAWAL + TRANSFER) from an account within a time window */
    @Query("SELECT COALESCE(SUM(t.amountInr), 0) FROM Transaction t " +
           "WHERE t.sourceAccount.accountId = :accountId " +
           "AND t.transactionType IN " +
           "(com.sentinel.aml.entity.Transaction$TransactionType.WITHDRAWAL, " +
           " com.sentinel.aml.entity.Transaction$TransactionType.TRANSFER) " +
           "AND t.transactionTimestamp BETWEEN :from AND :to")
    BigDecimal sumOutflowsInr(
            @Param("accountId") String accountId,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to);

    /** Sum of all transaction amounts (INR) for a customer on a specific day — used by Behavioral Deviation rule */
    @Query("SELECT COALESCE(SUM(t.amountInr), 0) FROM Transaction t " +
           "WHERE t.customer.customerId = :customerId " +
           "AND t.transactionTimestamp BETWEEN :from AND :to")
    BigDecimal sumDailyVolumeInr(
            @Param("customerId") String customerId,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to);

    /** Average daily transaction volume in INR over the past N days — used as rolling baseline */
    @Query("SELECT COALESCE(AVG(daily.daySum), 0) FROM " +
           "(SELECT SUM(t.amountInr) AS daySum FROM Transaction t " +
           " WHERE t.customer.customerId = :customerId " +
           " AND t.transactionTimestamp >= :since " +
           " GROUP BY CAST(t.transactionTimestamp AS date)) daily")
    BigDecimal avgDailyVolumeInr(
            @Param("customerId") String customerId,
            @Param("since") LocalDateTime since);
}
