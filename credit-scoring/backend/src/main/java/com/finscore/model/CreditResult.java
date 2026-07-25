package com.finscore.model;

import lombok.*;
import java.math.BigDecimal;
import java.util.List;

/**
 * CreditResult — Data Transfer Object for API Responses
 * =======================================================
 * Carries the complete credit assessment result from the service
 * layer to the controller and then to the Thymeleaf templates
 * or JSON API consumers.
 * 
 * This DTO is separate from the JPA entity to decouple the
 * database schema from the API contract.
 * 
 * @author FinScore AI Team
 */
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class CreditResult {

    /** Database ID of the saved assessment */
    private Long id;

    /** Borrower's full name */
    private String fullName;

    /** Borrower's occupation */
    private String occupation;

    /** Borrower's state */
    private String state;

    /** Borrower's gender */
    private String gender;

    /** Monthly income in INR */
    private BigDecimal monthlyIncome;

    /** Loan amount requested in INR */
    private BigDecimal loanRequested;

    // ═══ ML PREDICTION RESULTS ═══════════════════════════════════════════

    /** Credit score from XGBoost model (0-100) */
    private Integer creditScore;

    /** Risk category: "Low Risk", "Medium Risk", "High Risk" */
    private String riskCategory;

    /** Whether the borrower qualifies for a loan */
    private Boolean loanEligible;

    /** Maximum recommended loan amount in INR */
    private BigDecimal maxLoanAmount;

    /** Suggested interest rate range (e.g., "10-12% p.a.") */
    private String suggestedInterestRate;

    /** Model prediction confidence (0.0 - 1.0) */
    private BigDecimal mlConfidence;

    // ═══ GENAI EXPLANATION ═══════════════════════════════════════════════

    /** Gemini explanation in the borrower's preferred language */
    private String explanation;

    /** Current language of the explanation */
    private String explanationLanguage;

    // ═══ RAG TIPS ════════════════════════════════════════════════════════

    /** Personalized financial tips from RAG pipeline */
    private List<FinancialTip> tips;

    /**
     * Inner class representing a single financial tip.
     * Each tip has a heading, detail text, and icon class.
     */
    @Getter @Setter
    @NoArgsConstructor @AllArgsConstructor
    @Builder
    public static class FinancialTip {
        /** Short descriptive heading for the tip */
        private String heading;
        
        /** Detailed actionable advice */
        private String detail;
        
        /** Bootstrap icon class name (e.g., "bi-piggy-bank") */
        private String icon;
    }
}
