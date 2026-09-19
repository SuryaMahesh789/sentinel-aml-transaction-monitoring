package com.sentinel.aml.service;

import com.sentinel.aml.dto.IngestionResultDto;
import com.sentinel.aml.entity.Account;
import com.sentinel.aml.entity.Customer;
import com.sentinel.aml.repository.AccountRepository;
import com.sentinel.aml.repository.CustomerRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class IngestionServiceTest {

    @Autowired
    private IngestionService ingestionService;

    @Autowired
    private CustomerRepository customerRepository;

    @Autowired
    private AccountRepository accountRepository;

    private static final String CUSTOMER_CSV =
            "customer_id,first_name,last_name,gender,date_of_birth,age,email,phone_number," +
            "city,state,country,postal_code,occupation,annual_income,marital_status," +
            "education_level,employment_status,customer_since,customer_segment,kyc_status," +
            "risk_rating,is_politically_exposed,preferred_channel,email_verified,phone_verified,num_complaints_last_year\n" +
            "CUST_00001,Krishna,Sharma,M,1985-05-26,41,krishna.sharma31@gmail.com,+91-6939042955," +
            "Gurugram,Haryana,IN,741262,,355047.0,Married,Diploma,,2024-11-26,PREMIUM,VERIFIED," +
            "MEDIUM,0,Phone Banking,Y,Y,1\n" +
            "CUST_00002,Anika,Fernandes,M,2008-08-31,18,anika.fernandes907@outlook.com,+91-8479708607," +
            "Gurugram,Haryana,IN,182933,,101869.0,,,EMPLOYED,2020-06-15,RETAIL,VERIFIED," +
            "LOW,0,Phone Banking,Y,Y,1\n";

    private static final String ACCOUNT_CSV =
            "account_id,customer_id,account_type,account_status,currency,open_date,close_date," +
            "branch_code,branch_city,current_balance,avg_monthly_balance_6m,credit_limit," +
            "credit_utilization_pct,overdraft_enabled,card_type,is_joint_account," +
            "num_linked_devices,mobile_banking_enrolled,last_login_date,avg_monthly_txn_count,account_tier\n" +
            "ACC_000001,CUST_00001,NRE,ACTIVE,INR,2016-08-26,,BR122,Gurugram,33507.22,22831.7," +
            "0.0,0.0,Y,GOLD,0,2,Y,2026-07-20,15,SILVER\n" +
            "ACC_000002,CUST_00001,SAVINGS,ACTIVE,INR,2019-03-31,,BR119,Gurugram,25171.02,32699.45," +
            "0.0,0.0,N,CLASSIC,0,1,Y,2026-06-15,18,SILVER\n";

    @Test
    void shouldIngestCustomersSuccessfully() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "customers.csv", "text/csv",
                CUSTOMER_CSV.getBytes(StandardCharsets.UTF_8));

        IngestionResultDto result = ingestionService.ingestCustomers(file);

        assertThat(result.getTotalProcessed()).isEqualTo(2);
        assertThat(result.getSavedCount()).isEqualTo(2);
        assertThat(result.getFailedCount()).isEqualTo(0);
        assertThat(customerRepository.count()).isEqualTo(2);
    }

    @Test
    void shouldMapCustomerFieldsCorrectly() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "customers.csv", "text/csv",
                CUSTOMER_CSV.getBytes(StandardCharsets.UTF_8));

        ingestionService.ingestCustomers(file);

        Optional<Customer> opt = customerRepository.findByCustomerId("CUST_00001");
        assertThat(opt).isPresent();
        Customer c = opt.get();
        assertThat(c.getFirstName()).isEqualTo("Krishna");
        assertThat(c.getLastName()).isEqualTo("Sharma");
        assertThat(c.getEmail()).isEqualTo("krishna.sharma31@gmail.com");
        assertThat(c.getKycStatus()).isEqualTo(Customer.KycStatus.VERIFIED);
        assertThat(c.getRiskRating()).isEqualTo(Customer.RiskRating.MEDIUM);
        assertThat(c.isPoliticallyExposed()).isFalse();
    }

    @Test
    void shouldIngestAccountsAfterCustomers() {
        MockMultipartFile customerFile = new MockMultipartFile(
                "file", "customers.csv", "text/csv",
                CUSTOMER_CSV.getBytes(StandardCharsets.UTF_8));
        ingestionService.ingestCustomers(customerFile);

        MockMultipartFile accountFile = new MockMultipartFile(
                "file", "accounts.csv", "text/csv",
                ACCOUNT_CSV.getBytes(StandardCharsets.UTF_8));

        IngestionResultDto result = ingestionService.ingestAccounts(accountFile);

        assertThat(result.getTotalProcessed()).isEqualTo(2);
        assertThat(result.getSavedCount()).isEqualTo(2);
        assertThat(result.getFailedCount()).isEqualTo(0);
        assertThat(accountRepository.count()).isEqualTo(2);
    }

    @Test
    @Transactional
    void shouldMapAccountFieldsCorrectly() {
        MockMultipartFile customerFile = new MockMultipartFile(
                "file", "customers.csv", "text/csv",
                CUSTOMER_CSV.getBytes(StandardCharsets.UTF_8));
        ingestionService.ingestCustomers(customerFile);

        MockMultipartFile accountFile = new MockMultipartFile(
                "file", "accounts.csv", "text/csv",
                ACCOUNT_CSV.getBytes(StandardCharsets.UTF_8));
        ingestionService.ingestAccounts(accountFile);

        Optional<Account> opt = accountRepository.findByAccountId("ACC_000001");
        assertThat(opt).isPresent();
        Account a = opt.get();
        assertThat(a.getAccountType()).isEqualTo("NRE");
        assertThat(a.getAccountStatus()).isEqualTo(Account.AccountStatus.ACTIVE);
        assertThat(a.getCurrency()).isEqualTo("INR");
        assertThat(a.getBranchCode()).isEqualTo("BR122");
        // Access lazy-loaded customer within the active transaction
        assertThat(a.getCustomer().getCustomerId()).isEqualTo("CUST_00001");
    }

    @Test
    void shouldSkipDuplicateCustomers() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "customers.csv", "text/csv",
                CUSTOMER_CSV.getBytes(StandardCharsets.UTF_8));

        ingestionService.ingestCustomers(file);
        IngestionResultDto secondResult = ingestionService.ingestCustomers(file);

        assertThat(secondResult.getSkippedDuplicates()).isEqualTo(2);
        assertThat(secondResult.getSavedCount()).isEqualTo(0);
        assertThat(customerRepository.count()).isEqualTo(2); // no extra rows
    }

    @Test
    void shouldFailAccountIngestionWhenCustomerMissing() {
        // No customers loaded first
        MockMultipartFile accountFile = new MockMultipartFile(
                "file", "accounts.csv", "text/csv",
                ACCOUNT_CSV.getBytes(StandardCharsets.UTF_8));

        IngestionResultDto result = ingestionService.ingestAccounts(accountFile);

        assertThat(result.getFailedCount()).isEqualTo(2);
        assertThat(result.getSavedCount()).isEqualTo(0);
        assertThat(result.getErrors()).isNotEmpty();
    }

    @Test
    void shouldHandleEmptyFile() {
        MockMultipartFile emptyFile = new MockMultipartFile(
                "file", "empty.csv", "text/csv", new byte[0]);

        IngestionResultDto result = ingestionService.ingestCustomers(emptyFile);
        // Empty file returns immediately without error
        assertThat(result.getTotalProcessed()).isEqualTo(0);
    }
}
