package com.finscore.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * BorrowerInput — JPA Entity for Borrower Assessments
 * =====================================================
 * Maps to the 'borrower_assessments' table in MySQL.
 * 
 * Contains both INPUT fields (borrower data from the form)
 * and OUTPUT fields (ML prediction results, Gemini explanations,
 * RAG tips). This dual-purpose design allows a single entity
 * to represent the complete assessment lifecycle.
 * 
 * PRIVACY NOTE:
 * This entity deliberately does NOT store Aadhaar, PAN, or
 * any biometric data. Only alternate financial behavior data
 * and the borrower's name are stored.
 * 
 * @author FinScore AI Team
 */
@Entity
@Table(name = "borrower_assessments")
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class BorrowerInput {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // ═══ BORROWER INPUT FIELDS ═══════════════════════════════════════════

    /** Full name of the borrower (no Aadhaar/PAN stored) */
    @NotBlank(message = "Full name is required")
    @Size(max = 100, message = "Name must be under 100 characters")
    @Column(name = "full_name", nullable = false, length = 100)
    private String fullName;

    /** Gender — included for demographic tracking, NOT for scoring bias */
    @Column(name = "gender", length = 20)
    private String gender;

    /** Indian state or UT where borrower resides */
    @NotBlank(message = "State is required")
    @Column(name = "state", nullable = false, length = 50)
    private String state;

    /** Borrower's primary occupation */
    @NotBlank(message = "Occupation is required")
    @Column(name = "occupation", nullable = false, length = 50)
    private String occupation;

    /** Monthly income from all informal sources (INR) */
    @NotNull(message = "Monthly income is required")
    @DecimalMin(value = "0", message = "Income must be positive")
    @Column(name = "monthly_income", nullable = false, precision = 10, scale = 2)
    private BigDecimal monthlyIncome;

    /** Number of UPI transactions per month */
    @NotNull(message = "UPI frequency is required")
    @Min(value = 0, message = "UPI frequency must be non-negative")
    @Column(name = "upi_frequency", nullable = false)
    private Integer upiFrequency;

    /** Average amount per UPI transaction (INR) */
    @NotNull(message = "Average UPI amount is required")
    @DecimalMin(value = "0", message = "UPI amount must be positive")
    @Column(name = "avg_upi_amount", nullable = false, precision = 10, scale = 2)
    private BigDecimal avgUpiAmount;

    /** Utility bill payment consistency level */
    @NotBlank(message = "Utility consistency is required")
    @Column(name = "utility_consistency", nullable = false, length = 30)
    private String utilityConsistency;

    /** Monthly mobile recharge amount (INR) */
    @NotNull(message = "Recharge amount is required")
    @DecimalMin(value = "0", message = "Recharge amount must be positive")
    @Column(name = "recharge_amount", nullable = false, precision = 10, scale = 2)
    private BigDecimal rechargeAmount;

    /** Agricultural yield in kg/season (optional, mainly for farmers) */
    @Column(name = "agricultural_yield", precision = 10, scale = 2)
    @Builder.Default
    private BigDecimal agriculturalYield = BigDecimal.ZERO;

    /** Loan amount requested by the borrower (INR) */
    @NotNull(message = "Loan amount is required")
    @DecimalMin(value = "1", message = "Loan amount must be positive")
    @Column(name = "loan_requested", nullable = false, precision = 12, scale = 2)
    private BigDecimal loanRequested;

    /** Preferred language for AI explanation (Hindi or English) */
    @Column(name = "preferred_language", length = 10)
    @Builder.Default
    private String preferredLanguage = "English";

    /** Whether borrower gave consent for data processing (MANDATORY) */
    @Column(name = "consent_given", nullable = false)
    @Builder.Default
    private Boolean consentGiven = false;

    // ═══ ASSESSMENT RESULT FIELDS ════════════════════════════════════════
    // These are populated after ML prediction and Gemini API calls

    /** Credit score from XGBoost model (0-100) */
    @Column(name = "credit_score")
    private Integer creditScore;

    /** Risk category: Low Risk, Medium Risk, High Risk */
    @Column(name = "risk_category", length = 20)
    private String riskCategory;

    /** Whether the borrower is eligible for a loan */
    @Column(name = "loan_eligible")
    private Boolean loanEligible;

    /** Maximum recommended loan amount (INR) */
    @Column(name = "max_loan_amount", precision = 12, scale = 2)
    private BigDecimal maxLoanAmount;

    /** Suggested interest rate range string */
    @Column(name = "suggested_interest_rate", length = 20)
    private String suggestedInterestRate;

    /** Gemini AI explanation in English */
    @Column(name = "gemini_explanation_en", columnDefinition = "TEXT")
    private String geminiExplanationEn;

    /** Gemini AI explanation in Hindi */
    @Column(name = "gemini_explanation_hi", columnDefinition = "TEXT")
    private String geminiExplanationHi;

    /** RAG-generated financial tips stored as JSON string */
    @Column(name = "rag_tips", columnDefinition = "TEXT")
    private String ragTips;

    /** ML model prediction confidence (0-1) */
    @Column(name = "ml_confidence", precision = 5, scale = 4)
    private BigDecimal mlConfidence;

    // ═══ METADATA ════════════════════════════════════════════════════════

    /** Timestamp when assessment was created */
    @Column(name = "created_at")
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    /** IP address of the requester (for audit trail) */
    @Column(name = "ip_address", length = 45)
    private String ipAddress;

    /**
     * Set creation timestamp before persisting.
     * Ensures created_at is always populated.
     */
    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }
}
