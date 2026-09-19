package com.sentinel.aml.controller;

import com.sentinel.aml.dto.IngestionResultDto;
import com.sentinel.aml.service.IngestionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@Slf4j
@RestController
@RequestMapping("/api/v1/ingest")
@RequiredArgsConstructor
@Tag(name = "Ingestion", description = "CSV data ingestion endpoints for customers and accounts")
public class IngestionController {

    private final IngestionService ingestionService;

    @Operation(
        summary = "Ingest customers from CSV",
        description = "Upload a CSV file matching the customers.csv schema to bulk-load customer records."
    )
    @PostMapping(value = "/customers", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<IngestionResultDto> ingestCustomers(
            @Parameter(description = "customers.csv file", required = true)
            @RequestParam("file") MultipartFile file) {

        if (file.isEmpty()) {
            return ResponseEntity.badRequest().body(
                    IngestionResultDto.of(0, 0, 0, 0, java.util.List.of("Uploaded file is empty")));
        }

        log.info("Received customer CSV upload: filename={}, size={} bytes",
                file.getOriginalFilename(), file.getSize());

        IngestionResultDto result = ingestionService.ingestCustomers(file);
        return ResponseEntity.ok(result);
    }

    @Operation(
        summary = "Ingest accounts from CSV",
        description = "Upload a CSV file matching the accounts.csv schema. Customers must be loaded first."
    )
    @PostMapping(value = "/accounts", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<IngestionResultDto> ingestAccounts(
            @Parameter(description = "accounts.csv file", required = true)
            @RequestParam("file") MultipartFile file) {

        if (file.isEmpty()) {
            return ResponseEntity.badRequest().body(
                    IngestionResultDto.of(0, 0, 0, 0, java.util.List.of("Uploaded file is empty")));
        }

        log.info("Received account CSV upload: filename={}, size={} bytes",
                file.getOriginalFilename(), file.getSize());

        IngestionResultDto result = ingestionService.ingestAccounts(file);
        return ResponseEntity.ok(result);
    }
}
