package com.sentinel.aml.service;

import com.sentinel.aml.dto.TransactionRequestDto;
import com.sentinel.aml.dto.TransactionResponseDto;
import com.sentinel.aml.entity.Account;
import com.sentinel.aml.entity.Customer;
import com.sentinel.aml.entity.ExchangeRate;
import com.sentinel.aml.entity.Transaction;
import com.sentinel.aml.entity.Transaction.Channel;
import com.sentinel.aml.exception.DuplicateResourceException;
import com.sentinel.aml.exception.ResourceNotFoundException;
import com.sentinel.aml.exception.ValidationException;
import com.sentinel.aml.repository.AccountRepository;
import com.sentinel.aml.repository.ExchangeRateRepository;
import com.sentinel.aml.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class TransactionService {

    private static final String INR = "INR";
    private static final DateTimeFormatter REF_FMT =
            DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private final TransactionRepository transactionRepository;
    private final AccountRepository     accountRepository;
    private final ExchangeRateRepository exchangeRateRepository;
    private final DetectionEngine        detectionEngine;

    // -------------------------------------------------------------------------
    // Create transaction
    // -------------------------------------------------------------------------
    @Transactional
    public TransactionResponseDto createTransaction(TransactionRequestDto req) {

        // 1. Resolve source account
        Account sourceAccount = accountRepository.findByAccountId(req.getSourceAccountId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Source account not found: " + req.getSourceAccountId()));

        if (sourceAccount.getAccountStatus() != Account.AccountStatus.ACTIVE) {
            throw new ValidationException(
                    "Source account " + req.getSourceAccountId() +
                    " is not active (status=" + sourceAccount.getAccountStatus() + ")");
        }

        // 2. Validate destination account when it looks like an internal account ID
        final String destinationRef;
        if (req.getDestinationAccountRef() != null && !req.getDestinationAccountRef().isBlank()) {
            String trimmed = req.getDestinationAccountRef().trim();
            // If it matches internal account pattern, confirm it exists
            if (trimmed.startsWith("ACC_")) {
                accountRepository.findByAccountId(trimmed)
                        .orElseThrow(() -> new ResourceNotFoundException(
                                "Destination account not found: " + trimmed));
            }
            destinationRef = trimmed;
        } else {
            destinationRef = null;
        }

        // 3. Resolve customer from the source account
        Customer customer = sourceAccount.getCustomer();

        // 4. Resolve exchange rate and normalise to INR
        String currency = req.getCurrency().toUpperCase();
        BigDecimal rate = resolveExchangeRate(currency);
        BigDecimal amountInr = req.getAmount()
                .multiply(rate)
                .setScale(2, RoundingMode.HALF_UP);

        // 5. Generate unique transaction reference
        String txnRef = generateTransactionRef(req);
        if (transactionRepository.existsByTransactionRef(txnRef)) {
            throw new DuplicateResourceException(
                    "Transaction reference already exists: " + txnRef);
        }

        // 6. Determine timestamp
        LocalDateTime timestamp = (req.getTransactionTimestamp() != null)
                ? req.getTransactionTimestamp()
                : LocalDateTime.now();

        // 7. Build and persist transaction
        Transaction transaction = Transaction.builder()
                .transactionRef(txnRef)
                .sourceAccount(sourceAccount)
                .destinationAccountRef(destinationRef)
                .customer(customer)
                .amount(req.getAmount())
                .currency(currency)
                .amountInr(amountInr)
                .transactionType(req.getTransactionType())
                .channel(req.getChannel() != null ? req.getChannel() : Channel.ONLINE)
                .counterpartyName(req.getCounterpartyName())
                .counterpartyAccount(req.getCounterpartyAccount())
                .counterpartyBank(req.getCounterpartyBank())
                .jurisdiction(req.getJurisdiction() != null
                        ? req.getJurisdiction().toUpperCase()
                        : null)
                .transactionTimestamp(timestamp)
                .description(req.getDescription())
                .build();

        Transaction saved = transactionRepository.save(transaction);

        log.info("Transaction saved: ref={}, amount={} {} = {} INR, account={}",
                saved.getTransactionRef(), saved.getAmount(), currency,
                saved.getAmountInr(), req.getSourceAccountId());

        // 8. Run detection engine — evaluate all AML rules against this transaction
        detectionEngine.evaluate(saved);

        return TransactionResponseDto.from(saved, rate);
    }

    // -------------------------------------------------------------------------
    // Get transaction by ID
    // -------------------------------------------------------------------------
    @Transactional(readOnly = true)
    public TransactionResponseDto getTransaction(Long id) {
        Transaction t = transactionRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Transaction not found with id: " + id));

        // Resolve rate for display — best-effort, may differ from original rate used
        BigDecimal rate = resolveExchangeRate(t.getCurrency());
        return TransactionResponseDto.from(t, rate);
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /**
     * Looks up the active exchange rate for the given currency.
     * Falls back to 1.0 for INR (no conversion needed).
     * Throws ValidationException if rate is missing for a non-INR currency.
     */
    private BigDecimal resolveExchangeRate(String currency) {
        if (INR.equalsIgnoreCase(currency)) {
            return BigDecimal.ONE;
        }
        ExchangeRate rate = exchangeRateRepository.findActiveRateForCurrency(currency)
                .orElseThrow(() -> new ValidationException(
                        "No active exchange rate found for currency: " + currency +
                        ". Please add an exchange rate entry first."));
        return rate.getRate();
    }

    /**
     * Generates a unique transaction reference combining timestamp + UUID suffix.
     * Format: TXN-20240915143022-a1b2
     */
    private String generateTransactionRef(TransactionRequestDto req) {
        String ts = LocalDateTime.now().format(REF_FMT);
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        return "TXN-" + ts + "-" + suffix;
    }
}
