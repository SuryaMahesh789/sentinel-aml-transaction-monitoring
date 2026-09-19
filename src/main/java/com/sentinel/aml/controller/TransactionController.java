package com.sentinel.aml.controller;

import com.sentinel.aml.dto.TransactionRequestDto;
import com.sentinel.aml.dto.TransactionResponseDto;
import com.sentinel.aml.service.TransactionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/api/v1/transactions")
@RequiredArgsConstructor
@Tag(name = "Transactions", description = "Transaction ingestion and retrieval endpoints")
public class TransactionController {

    private final TransactionService transactionService;

    @Operation(
        summary = "Submit a new transaction",
        description = "Validates, normalises to INR, persists the transaction, " +
                      "and returns the saved record with its generated reference ID."
    )
    @PostMapping
    public ResponseEntity<TransactionResponseDto> createTransaction(
            @Valid @RequestBody TransactionRequestDto request) {

        log.info("Received transaction request: sourceAccount={}, amount={} {}",
                request.getSourceAccountId(), request.getAmount(), request.getCurrency());

        TransactionResponseDto response = transactionService.createTransaction(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @Operation(
        summary = "Get a transaction by ID",
        description = "Returns the full transaction record for a given internal database ID."
    )
    @GetMapping("/{id}")
    public ResponseEntity<TransactionResponseDto> getTransaction(
            @Parameter(description = "Internal transaction database ID", required = true)
            @PathVariable Long id) {

        TransactionResponseDto response = transactionService.getTransaction(id);
        return ResponseEntity.ok(response);
    }
}
