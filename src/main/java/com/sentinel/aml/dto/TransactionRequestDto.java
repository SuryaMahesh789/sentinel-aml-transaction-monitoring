package com.sentinel.aml.dto;

import com.sentinel.aml.entity.Transaction.Channel;
import com.sentinel.aml.entity.Transaction.TransactionType;
import jakarta.validation.constraints.*;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class TransactionRequestDto {

    /** Must match an existing account_id in the accounts table */
    @NotBlank(message = "sourceAccountId is required")
    private String sourceAccountId;

    /**
     * Optional: for internal transfers, pass the internal account_id.
     * For external transfers/remittances, pass any external reference string.
     */
    private String destinationAccountRef;

    @NotNull(message = "amount is required")
    @DecimalMin(value = "0.01", message = "amount must be greater than zero")
    @Digits(integer = 16, fraction = 2, message = "amount must have at most 16 integer digits and 2 decimal places")
    private BigDecimal amount;

    /** ISO 4217 currency code, e.g. INR, USD, EUR */
    @NotBlank(message = "currency is required")
    @Size(min = 3, max = 10, message = "currency must be a valid ISO 4217 code")
    private String currency;

    @NotNull(message = "transactionType is required")
    private TransactionType transactionType;

    /** Optional — defaults to ONLINE if not provided */
    private Channel channel;

    /** ISO 3166-1 alpha-2 country code of the counterparty's jurisdiction, e.g. "IN", "US", "IR" */
    @Size(max = 5, message = "jurisdiction must be a valid ISO 3166-1 alpha-2 code")
    private String jurisdiction;

    private String counterpartyName;

    @Size(max = 80)
    private String counterpartyAccount;

    @Size(max = 100)
    private String counterpartyBank;

    /**
     * Optional: when null, defaults to now().
     * Allows back-dated transactions to be ingested during bulk load.
     */
    private LocalDateTime transactionTimestamp;

    @Size(max = 500)
    private String description;
}
