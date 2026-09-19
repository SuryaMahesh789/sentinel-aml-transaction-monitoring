package com.sentinel.aml.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "customers")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Customer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "customer_id", nullable = false, unique = true, length = 50)
    private String customerId;

    @Column(name = "first_name", nullable = false, length = 100)
    private String firstName;

    @Column(name = "last_name", nullable = false, length = 100)
    private String lastName;

    @Column(name = "gender", length = 10)
    private String gender;

    @Column(name = "date_of_birth")
    private LocalDate dateOfBirth;

    @Column(name = "email", length = 150)
    private String email;

    @Column(name = "phone_number", length = 30)
    private String phoneNumber;

    @Column(name = "city", length = 100)
    private String city;

    @Column(name = "state", length = 100)
    private String state;

    @Column(name = "country", length = 10)
    private String country;

    @Column(name = "postal_code", length = 20)
    private String postalCode;

    @Column(name = "occupation", length = 150)
    private String occupation;

    @Column(name = "annual_income", precision = 18, scale = 2)
    private BigDecimal annualIncome;

    @Column(name = "customer_segment", length = 50)
    private String customerSegment;

    @Enumerated(EnumType.STRING)
    @Column(name = "kyc_status", length = 20)
    private KycStatus kycStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "risk_rating", length = 20)
    private RiskRating riskRating;

    @Column(name = "is_politically_exposed", nullable = false)
    private boolean politicallyExposed;

    @Column(name = "customer_since")
    private LocalDate customerSince;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @OneToMany(mappedBy = "customer", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @Builder.Default
    private List<Account> accounts = new ArrayList<>();

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public enum KycStatus {
        VERIFIED, PENDING, FAILED, EXPIRED
    }

    public enum RiskRating {
        LOW, MEDIUM, HIGH, CRITICAL
    }
}
