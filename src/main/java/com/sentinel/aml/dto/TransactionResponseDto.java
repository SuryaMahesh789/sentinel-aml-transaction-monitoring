package com.sentinel.aml.dto;

import com.sentinel.aml.entity.Transaction;
import com.sentinel.aml.entity.Transaction.Channel;
import com.sentinel.aml.entity.Transaction.TransactionStatus;
import com.sentinel.aml.entity.Transaction.TransactionType;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Builder
public class TransactionResponseDto {

    private Long id;
    private String transactionRef;
    private String sourceAccountId;
    private String destinationAccountRef;
    private String customerId;
    private BigDecimal amount;
    private String currency;
    private BigDecimal amountInr;
    private String exchangeRateUsed;
    private TransactionType transactionType;
    private Channel channel;
    private String counterpartyName;
    private String counterpartyAccount;
    private String counterpartyBank;
    private String jurisdiction;
    private LocalDateTime transactionTimestamp;
    private String description;
    private TransactionStatus status;
    private LocalDateTime createdAt;

    public static TransactionResponseDto from(Transaction t, BigDecimal rateUsed) {
        return TransactionResponseDto.builder()
                .id(t.getId())
                .transactionRef(t.getTransactionRef())
                .sourceAccountId(t.getSourceAccount().getAccountId())
                .destinationAccountRef(t.getDestinationAccountRef())
                .customerId(t.getCustomer().getCustomerId())
                .amount(t.getAmount())
                .currency(t.getCurrency())
                .amountInr(t.getAmountInr())
                .exchangeRateUsed(rateUsed != null ? "1 " + t.getCurrency() + " = " + rateUsed + " INR" : "N/A")
                .transactionType(t.getTransactionType())
                .channel(t.getChannel())
                .counterpartyName(t.getCounterpartyName())
                .counterpartyAccount(t.getCounterpartyAccount())
                .counterpartyBank(t.getCounterpartyBank())
                .jurisdiction(t.getJurisdiction())
                .transactionTimestamp(t.getTransactionTimestamp())
                .description(t.getDescription())
                .status(t.getStatus())
                .createdAt(t.getCreatedAt())
                .build();
    }
}
