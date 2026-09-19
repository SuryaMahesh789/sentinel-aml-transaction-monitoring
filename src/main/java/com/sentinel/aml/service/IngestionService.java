package com.sentinel.aml.service;

import com.sentinel.aml.dto.IngestionResultDto;
import com.sentinel.aml.entity.Account;
import com.sentinel.aml.entity.Customer;
import com.sentinel.aml.repository.AccountRepository;
import com.sentinel.aml.repository.CustomerRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class IngestionService {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private final CustomerRepository customerRepository;
    private final AccountRepository  accountRepository;

    // -------------------------------------------------------------------------
    // Customer CSV ingestion
    // CSV columns (exact order from customers.csv):
    // customer_id, first_name, last_name, gender, date_of_birth, age,
    // email, phone_number, city, state, country, postal_code, occupation,
    // annual_income, marital_status, education_level, employment_status,
    // customer_since, customer_segment, kyc_status, risk_rating,
    // is_politically_exposed, preferred_channel, email_verified,
    // phone_verified, num_complaints_last_year
    // -------------------------------------------------------------------------
    @Transactional
    public IngestionResultDto ingestCustomers(MultipartFile file) {
        List<String> errors     = new ArrayList<>();
        int total = 0, saved = 0, failed = 0, dupes = 0;

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8))) {

            String headerLine = reader.readLine(); // skip header
            if (headerLine == null) {
                errors.add("File is empty");
                return IngestionResultDto.of(0, 0, 0, 0, errors);
            }

            String line;
            int lineNum = 1;
            while ((line = reader.readLine()) != null) {
                lineNum++;
                line = line.trim();
                if (line.isEmpty()) continue;

                total++;
                try {
                    String[] cols = splitCsvLine(line);

                    if (cols.length < 22) {
                        errors.add("Line " + lineNum + ": insufficient columns (" + cols.length + ")");
                        failed++;
                        continue;
                    }

                    String customerId = col(cols, 0);
                    if (customerId.isEmpty()) {
                        errors.add("Line " + lineNum + ": customer_id is blank");
                        failed++;
                        continue;
                    }

                    if (customerRepository.existsByCustomerId(customerId)) {
                        log.debug("Skipping duplicate customer: {}", customerId);
                        dupes++;
                        continue;
                    }

                    Customer customer = Customer.builder()
                            .customerId(customerId)
                            .firstName(requiredCol(cols, 1, "first_name", lineNum, errors))
                            .lastName(requiredCol(cols, 2, "last_name", lineNum, errors))
                            .gender(col(cols, 3))
                            .dateOfBirth(parseDate(col(cols, 4), lineNum, "date_of_birth", errors))
                            // col 5 = age  (derived, not stored)
                            .email(col(cols, 6))
                            .phoneNumber(col(cols, 7))
                            .city(col(cols, 8))
                            .state(col(cols, 9))
                            .country(col(cols, 10))
                            .postalCode(col(cols, 11))
                            .occupation(col(cols, 12))
                            .annualIncome(parseBigDecimal(col(cols, 13)))
                            // col 14 = marital_status  (not stored in entity)
                            // col 15 = education_level (not stored in entity)
                            // col 16 = employment_status (not stored in entity)
                            .customerSince(parseDate(col(cols, 17), lineNum, "customer_since", errors))
                            .customerSegment(col(cols, 18))
                            .kycStatus(parseKycStatus(col(cols, 19), lineNum, errors))
                            .riskRating(parseRiskRating(col(cols, 20), lineNum, errors))
                            .politicallyExposed(parseBooleanInt(col(cols, 21)))
                            // col 22 = preferred_channel  (not stored)
                            // col 23 = email_verified     (not stored)
                            // col 24 = phone_verified     (not stored)
                            // col 25 = num_complaints_last_year (not stored)
                            .build();

                    // If critical fields failed validation they'll be null — allow save anyway
                    // (hackathon pragmatism: save what we can)
                    customerRepository.save(customer);
                    saved++;
                    log.debug("Saved customer: {}", customerId);

                } catch (Exception e) {
                    log.warn("Line {}: unexpected error — {}", lineNum, e.getMessage());
                    errors.add("Line " + lineNum + ": " + e.getMessage());
                    failed++;
                }
            }
        } catch (Exception e) {
            log.error("Failed to read customer CSV: {}", e.getMessage(), e);
            errors.add("File read error: " + e.getMessage());
        }

        log.info("Customer ingestion complete — total={}, saved={}, failed={}, dupes={}", total, saved, failed, dupes);
        return IngestionResultDto.of(total, saved, failed, dupes, errors);
    }

    // -------------------------------------------------------------------------
    // Account CSV ingestion
    // CSV columns (exact order from accounts.csv):
    // account_id, customer_id, account_type, account_status, currency,
    // open_date, close_date, branch_code, branch_city, current_balance,
    // avg_monthly_balance_6m, credit_limit, credit_utilization_pct,
    // overdraft_enabled, card_type, is_joint_account, num_linked_devices,
    // mobile_banking_enrolled, last_login_date, avg_monthly_txn_count,
    // account_tier
    // -------------------------------------------------------------------------
    @Transactional
    public IngestionResultDto ingestAccounts(MultipartFile file) {
        List<String> errors     = new ArrayList<>();
        int total = 0, saved = 0, failed = 0, dupes = 0;

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8))) {

            String headerLine = reader.readLine();
            if (headerLine == null) {
                errors.add("File is empty");
                return IngestionResultDto.of(0, 0, 0, 0, errors);
            }

            String line;
            int lineNum = 1;
            while ((line = reader.readLine()) != null) {
                lineNum++;
                line = line.trim();
                if (line.isEmpty()) continue;

                total++;
                try {
                    String[] cols = splitCsvLine(line);

                    if (cols.length < 10) {
                        errors.add("Line " + lineNum + ": insufficient columns (" + cols.length + ")");
                        failed++;
                        continue;
                    }

                    String accountId   = col(cols, 0);
                    String customerId  = col(cols, 1);

                    if (accountId.isEmpty()) {
                        errors.add("Line " + lineNum + ": account_id is blank");
                        failed++;
                        continue;
                    }

                    if (accountRepository.existsByAccountId(accountId)) {
                        log.debug("Skipping duplicate account: {}", accountId);
                        dupes++;
                        continue;
                    }

                    Customer customer = customerRepository.findByCustomerId(customerId)
                            .orElse(null);
                    if (customer == null) {
                        errors.add("Line " + lineNum + ": customer not found for account " + accountId + " (customer_id=" + customerId + ")");
                        failed++;
                        continue;
                    }

                    Account.AccountStatus status = parseAccountStatus(col(cols, 3), lineNum, errors);

                    Account account = Account.builder()
                            .accountId(accountId)
                            .customer(customer)
                            .accountType(col(cols, 2))
                            .accountStatus(status != null ? status : Account.AccountStatus.ACTIVE)
                            .currency(col(cols, 4).isEmpty() ? "INR" : col(cols, 4))
                            .openDate(parseDate(col(cols, 5), lineNum, "open_date", errors))
                            .closeDate(parseDate(col(cols, 6), lineNum, "close_date", errors))
                            .branchCode(col(cols, 7))
                            .branchCity(col(cols, 8))
                            .currentBalance(parseBigDecimal(col(cols, 9)))
                            .avgMonthlyBalance6m(parseBigDecimal(col(cols, 10)))
                            // col 11 = credit_limit          (not stored)
                            // col 12 = credit_utilization_pct(not stored)
                            // col 13 = overdraft_enabled      (not stored)
                            // col 14 = card_type              (not stored)
                            // col 15 = is_joint_account       (not stored)
                            // col 16 = num_linked_devices     (not stored)
                            // col 17 = mobile_banking_enrolled(not stored)
                            // col 18 = last_login_date        (not stored)
                            .avgMonthlyTxnCount(parseInteger(col(cols, 19)))
                            // col 20 = account_tier           (not stored)
                            .riskRating(customer.getRiskRating()) // inherit customer risk
                            .build();

                    accountRepository.save(account);
                    saved++;
                    log.debug("Saved account: {}", accountId);

                } catch (Exception e) {
                    log.warn("Line {}: unexpected error — {}", lineNum, e.getMessage());
                    errors.add("Line " + lineNum + ": " + e.getMessage());
                    failed++;
                }
            }
        } catch (Exception e) {
            log.error("Failed to read account CSV: {}", e.getMessage(), e);
            errors.add("File read error: " + e.getMessage());
        }

        log.info("Account ingestion complete — total={}, saved={}, failed={}, dupes={}", total, saved, failed, dupes);
        return IngestionResultDto.of(total, saved, failed, dupes, errors);
    }

    // -------------------------------------------------------------------------
    // Parsing helpers
    // -------------------------------------------------------------------------

    /**
     * Minimal CSV splitter — handles comma-separated values.
     * Accounts for quoted fields that contain commas.
     */
    private String[] splitCsvLine(String line) {
        List<String> tokens = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inQuotes = false;

        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '"') {
                inQuotes = !inQuotes;
            } else if (c == ',' && !inQuotes) {
                tokens.add(current.toString().trim());
                current.setLength(0);
            } else {
                current.append(c);
            }
        }
        tokens.add(current.toString().trim()); // last field
        return tokens.toArray(new String[0]);
    }

    /** Safe column accessor — returns empty string if index out of range */
    private String col(String[] cols, int index) {
        if (index >= cols.length) return "";
        String val = cols[index];
        return val == null ? "" : val.trim();
    }

    /** Required column — adds error but does not throw */
    private String requiredCol(String[] cols, int index, String name, int lineNum, List<String> errors) {
        String val = col(cols, index);
        if (val.isEmpty()) {
            errors.add("Line " + lineNum + ": required field '" + name + "' is blank");
        }
        return val;
    }

    private LocalDate parseDate(String val, int lineNum, String fieldName, List<String> errors) {
        if (val == null || val.isEmpty()) return null;
        try {
            return LocalDate.parse(val, DATE_FMT);
        } catch (DateTimeParseException e) {
            errors.add("Line " + lineNum + ": invalid date for '" + fieldName + "': " + val);
            return null;
        }
    }

    private BigDecimal parseBigDecimal(String val) {
        if (val == null || val.isEmpty()) return null;
        try {
            return new BigDecimal(val);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private Integer parseInteger(String val) {
        if (val == null || val.isEmpty()) return null;
        try {
            return Integer.parseInt(val);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** Accepts "1", "0", "Y", "N", "true", "false" */
    private boolean parseBooleanInt(String val) {
        if (val == null || val.isEmpty()) return false;
        return val.equals("1") || val.equalsIgnoreCase("Y") || val.equalsIgnoreCase("true");
    }

    private Customer.KycStatus parseKycStatus(String val, int lineNum, List<String> errors) {
        if (val == null || val.isEmpty()) return Customer.KycStatus.PENDING;
        try {
            return Customer.KycStatus.valueOf(val.toUpperCase());
        } catch (IllegalArgumentException e) {
            errors.add("Line " + lineNum + ": unknown kyc_status '" + val + "', defaulting to PENDING");
            return Customer.KycStatus.PENDING;
        }
    }

    private Customer.RiskRating parseRiskRating(String val, int lineNum, List<String> errors) {
        if (val == null || val.isEmpty()) return Customer.RiskRating.MEDIUM;
        try {
            return Customer.RiskRating.valueOf(val.toUpperCase());
        } catch (IllegalArgumentException e) {
            errors.add("Line " + lineNum + ": unknown risk_rating '" + val + "', defaulting to MEDIUM");
            return Customer.RiskRating.MEDIUM;
        }
    }

    private Account.AccountStatus parseAccountStatus(String val, int lineNum, List<String> errors) {
        if (val == null || val.isEmpty()) return Account.AccountStatus.ACTIVE;
        try {
            return Account.AccountStatus.valueOf(val.toUpperCase());
        } catch (IllegalArgumentException e) {
            errors.add("Line " + lineNum + ": unknown account_status '" + val + "', defaulting to ACTIVE");
            return Account.AccountStatus.ACTIVE;
        }
    }
}
