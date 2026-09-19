package com.sentinel.aml.service;

import com.sentinel.aml.dto.TransactionRequestDto;
import com.sentinel.aml.dto.TransactionResponseDto;
import com.sentinel.aml.entity.*;
import com.sentinel.aml.entity.Transaction.TransactionType;
import com.sentinel.aml.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class TransactionServiceTest {

    @Autowired TransactionService     transactionService;
    @Autowired CustomerRepository     customerRepository;
    @Autowired AccountRepository      accountRepository;
    @Autowired TransactionRepository  transactionRepository;
    @Autowired ExchangeRateRepository exchangeRateRepository;

    @BeforeEach
    void setUp() {
        // Seed a customer
        Customer customer = Customer.builder()
                .customerId("CUST_TEST_01")
                .firstName("Test")
                .lastName("User")
                .kycStatus(Customer.KycStatus.VERIFIED)
                .riskRating(Customer.RiskRating.LOW)
                .build();
        customerRepository.save(customer);

        // Seed an active INR account
        Account account = Account.builder()
                .accountId("ACC_TEST_001")
                .customer(customer)
                .accountType("SAVINGS")
                .accountStatus(Account.AccountStatus.ACTIVE)
                .currency("INR")
                .currentBalance(new BigDecimal("100000.00"))
                .build();
        accountRepository.save(account);

        // Seed a frozen account for negative testing
        Account frozenAccount = Account.builder()
                .accountId("ACC_TEST_FROZEN")
                .customer(customer)
                .accountType("SAVINGS")
                .accountStatus(Account.AccountStatus.FROZEN)
                .currency("INR")
                .currentBalance(BigDecimal.ZERO)
                .build();
        accountRepository.save(frozenAccount);

        // Seed USD exchange rate
        ExchangeRate usdRate = ExchangeRate.builder()
                .fromCurrency("USD")
                .toCurrency("INR")
                .rate(new BigDecimal("83.500000"))
                .active(true)
                .effectiveFrom(LocalDateTime.now().minusDays(1))
                .build();
        exchangeRateRepository.save(usdRate);
    }

    @Test
    void shouldCreateInrTransactionSuccessfully() {
        TransactionRequestDto req = new TransactionRequestDto();
        req.setSourceAccountId("ACC_TEST_001");
        req.setAmount(new BigDecimal("50000.00"));
        req.setCurrency("INR");
        req.setTransactionType(TransactionType.DEPOSIT);

        TransactionResponseDto resp = transactionService.createTransaction(req);

        assertThat(resp.getId()).isNotNull();
        assertThat(resp.getTransactionRef()).startsWith("TXN-");
        assertThat(resp.getAmountInr()).isEqualByComparingTo("50000.00");
        assertThat(resp.getCurrency()).isEqualTo("INR");
        assertThat(resp.getSourceAccountId()).isEqualTo("ACC_TEST_001");
        assertThat(resp.getStatus()).isEqualTo(Transaction.TransactionStatus.COMPLETED);
    }

    @Test
    void shouldNormaliseUsdAmountToInr() {
        TransactionRequestDto req = new TransactionRequestDto();
        req.setSourceAccountId("ACC_TEST_001");
        req.setAmount(new BigDecimal("1000.00"));
        req.setCurrency("USD");
        req.setTransactionType(TransactionType.TRANSFER);
        req.setDestinationAccountRef("EXT_ACCT_US_123");
        req.setJurisdiction("US");

        TransactionResponseDto resp = transactionService.createTransaction(req);

        // 1000 USD × 83.5 = 83500 INR
        assertThat(resp.getAmountInr()).isEqualByComparingTo("83500.00");
        assertThat(resp.getCurrency()).isEqualTo("USD");
        assertThat(resp.getJurisdiction()).isEqualTo("US");
        assertThat(resp.getExchangeRateUsed()).contains("83.5");
    }

    @Test
    void shouldPersistTransactionToDatabase() {
        TransactionRequestDto req = new TransactionRequestDto();
        req.setSourceAccountId("ACC_TEST_001");
        req.setAmount(new BigDecimal("900000.00")); // above CTR threshold
        req.setCurrency("INR");
        req.setTransactionType(TransactionType.CASH_DEPOSIT);
        req.setJurisdiction("IN");
        req.setDescription("Test large cash deposit");

        TransactionResponseDto resp = transactionService.createTransaction(req);

        assertThat(transactionRepository.count()).isEqualTo(1);
        assertThat(transactionRepository.findById(resp.getId())).isPresent();
    }

    @Test
    void shouldThrowWhenSourceAccountNotFound() {
        TransactionRequestDto req = new TransactionRequestDto();
        req.setSourceAccountId("ACC_DOES_NOT_EXIST");
        req.setAmount(new BigDecimal("1000.00"));
        req.setCurrency("INR");
        req.setTransactionType(TransactionType.DEPOSIT);

        assertThatThrownBy(() -> transactionService.createTransaction(req))
                .isInstanceOf(com.sentinel.aml.exception.ResourceNotFoundException.class)
                .hasMessageContaining("ACC_DOES_NOT_EXIST");
    }

    @Test
    void shouldThrowWhenAccountIsFrozen() {
        TransactionRequestDto req = new TransactionRequestDto();
        req.setSourceAccountId("ACC_TEST_FROZEN");
        req.setAmount(new BigDecimal("1000.00"));
        req.setCurrency("INR");
        req.setTransactionType(TransactionType.WITHDRAWAL);

        assertThatThrownBy(() -> transactionService.createTransaction(req))
                .isInstanceOf(com.sentinel.aml.exception.ValidationException.class)
                .hasMessageContaining("not active");
    }

    @Test
    void shouldThrowWhenExchangeRateMissing() {
        TransactionRequestDto req = new TransactionRequestDto();
        req.setSourceAccountId("ACC_TEST_001");
        req.setAmount(new BigDecimal("100.00"));
        req.setCurrency("JPY"); // no rate seeded for JPY
        req.setTransactionType(TransactionType.REMITTANCE);

        assertThatThrownBy(() -> transactionService.createTransaction(req))
                .isInstanceOf(com.sentinel.aml.exception.ValidationException.class)
                .hasMessageContaining("No active exchange rate found for currency: JPY");
    }

    @Test
    void shouldUseProvidedTimestamp() {
        LocalDateTime backdated = LocalDateTime.of(2024, 1, 15, 10, 30, 0);

        TransactionRequestDto req = new TransactionRequestDto();
        req.setSourceAccountId("ACC_TEST_001");
        req.setAmount(new BigDecimal("5000.00"));
        req.setCurrency("INR");
        req.setTransactionType(TransactionType.PAYMENT);
        req.setTransactionTimestamp(backdated);

        TransactionResponseDto resp = transactionService.createTransaction(req);

        assertThat(resp.getTransactionTimestamp()).isEqualTo(backdated);
    }

    @Test
    void shouldDefaultChannelToOnlineWhenNotProvided() {
        TransactionRequestDto req = new TransactionRequestDto();
        req.setSourceAccountId("ACC_TEST_001");
        req.setAmount(new BigDecimal("2000.00"));
        req.setCurrency("INR");
        req.setTransactionType(TransactionType.WITHDRAWAL);
        // channel intentionally not set

        TransactionResponseDto resp = transactionService.createTransaction(req);

        assertThat(resp.getChannel()).isEqualTo(Transaction.Channel.ONLINE);
    }

    @Test
    void shouldReturnTransactionById() {
        TransactionRequestDto req = new TransactionRequestDto();
        req.setSourceAccountId("ACC_TEST_001");
        req.setAmount(new BigDecimal("1500.00"));
        req.setCurrency("INR");
        req.setTransactionType(TransactionType.DEPOSIT);

        TransactionResponseDto created = transactionService.createTransaction(req);
        TransactionResponseDto fetched  = transactionService.getTransaction(created.getId());

        assertThat(fetched.getId()).isEqualTo(created.getId());
        assertThat(fetched.getTransactionRef()).isEqualTo(created.getTransactionRef());
    }

    @Test
    void shouldThrowWhenTransactionIdNotFound() {
        assertThatThrownBy(() -> transactionService.getTransaction(99999L))
                .isInstanceOf(com.sentinel.aml.exception.ResourceNotFoundException.class)
                .hasMessageContaining("99999");
    }
}
