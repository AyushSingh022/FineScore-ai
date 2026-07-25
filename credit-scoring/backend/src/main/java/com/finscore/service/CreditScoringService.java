package com.finscore.service;

import com.finscore.model.BorrowerInput;
import com.finscore.model.CreditResult;
import com.finscore.repository.BorrowerRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;

/**
 * CreditScoringService — Core Business Logic Orchestrator
 * =========================================================
 * Orchestrates the complete credit assessment pipeline:
 * 
 * 1. Validate consent flag
 * 2. Call Python ML service (XGBoost) for score prediction
 * 3. Call Gemini API for explanation in preferred language
 * 4. Call RAG service for personalized financial tips
 * 5. Save complete result to MySQL database
 * 6. Return structured CreditResult
 * 
 * Each external service call is wrapped in try-catch with
 * graceful fallback behavior — the assessment still succeeds
 * even if Gemini or RAG services are temporarily unavailable.
 * 
 * @author FinScore AI Team
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class CreditScoringService {

    private final BorrowerRepository borrowerRepository;
    private final GeminiService geminiService;
    private final RAGService ragService;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    @Value("${ml.service.url}")
    private String mlServiceUrl;

    /**
     * Process a complete credit assessment.
     * 
     * This is the main entry point for the scoring pipeline.
     * Steps are executed sequentially with fallbacks at each stage.
     * 
     * @param input Validated borrower input from the form
     * @return CreditResult with score, explanation, and tips
     * @throws IllegalArgumentException if consent is not given
     */
    public CreditResult processAssessment(BorrowerInput input) {
        // ── Step 1: Validate consent ────────────────────────────────────
        if (input.getConsentGiven() == null || !input.getConsentGiven()) {
            throw new IllegalArgumentException(
                "Consent is required before processing. " +
                "Please check the consent checkbox."
            );
        }

        log.info("Processing assessment for: {} ({})", 
                 input.getFullName(), input.getOccupation());

        // ── Step 2: Call ML Service for prediction ──────────────────────
        Map<String, Object> mlResult = callMLService(input);
        
        int creditScore = ((Number) mlResult.getOrDefault("score", 50)).intValue();
        String riskCategory = (String) mlResult.getOrDefault("risk_category", "Medium Risk");
        boolean loanEligible = (Boolean) mlResult.getOrDefault("loan_eligible", false);
        double maxLoanAmount = ((Number) mlResult.getOrDefault("max_loan_amount", 0)).doubleValue();
        String interestRate = (String) mlResult.getOrDefault("suggested_interest_rate", "N/A");
        double confidence = ((Number) mlResult.getOrDefault("confidence", 0.75)).doubleValue();

        // Update input entity with results
        input.setCreditScore(creditScore);
        input.setRiskCategory(riskCategory);
        input.setLoanEligible(loanEligible);
        input.setMaxLoanAmount(BigDecimal.valueOf(maxLoanAmount));
        input.setSuggestedInterestRate(interestRate);
        input.setMlConfidence(BigDecimal.valueOf(confidence).setScale(4, RoundingMode.HALF_UP));

        // ── Step 3: Call Gemini for explanation ─────────────────────────
        String preferredLang = input.getPreferredLanguage() != null 
            ? input.getPreferredLanguage() : "English";
        
        String explanation = geminiService.generateExplanation(
            input.getOccupation(),
            input.getState(),
            input.getMonthlyIncome().intValue(),
            creditScore,
            riskCategory,
            input.getLoanRequested().intValue(),
            preferredLang
        );

        // Store explanations in both languages if possible
        if ("Hindi".equalsIgnoreCase(preferredLang)) {
            input.setGeminiExplanationHi(explanation);
        } else {
            input.setGeminiExplanationEn(explanation);
        }

        // ── Step 4: Call RAG service for tips ───────────────────────────
        List<CreditResult.FinancialTip> tips = ragService.getFinancialTips(
            creditScore, input.getOccupation(), input.getState()
        );

        // Serialize tips to JSON string for database storage
        try {
            input.setRagTips(objectMapper.writeValueAsString(tips));
        } catch (Exception e) {
            log.warn("Failed to serialize RAG tips: {}", e.getMessage());
            input.setRagTips("[]");
        }

        // ── Step 5: Save to database ────────────────────────────────────
        BorrowerInput saved = borrowerRepository.save(input);
        log.info("Assessment saved with ID: {} | Score: {} | Risk: {}", 
                 saved.getId(), creditScore, riskCategory);

        // ── Step 6: Build and return CreditResult DTO ──────────────────
        return CreditResult.builder()
                .id(saved.getId())
                .fullName(saved.getFullName())
                .occupation(saved.getOccupation())
                .state(saved.getState())
                .gender(saved.getGender())
                .monthlyIncome(saved.getMonthlyIncome())
                .loanRequested(saved.getLoanRequested())
                .creditScore(creditScore)
                .riskCategory(riskCategory)
                .loanEligible(loanEligible)
                .maxLoanAmount(BigDecimal.valueOf(maxLoanAmount))
                .suggestedInterestRate(interestRate)
                .mlConfidence(BigDecimal.valueOf(confidence))
                .explanation(explanation)
                .explanationLanguage(preferredLang)
                .tips(tips)
                .build();
    }

    /**
     * Call the Python ML service for credit score prediction.
     * 
     * Sends borrower's alternate financial data to the XGBoost model
     * via the FastAPI /predict endpoint.
     * 
     * @param input Borrower input data
     * @return Map with prediction results (score, risk, eligibility, etc.)
     */
    private Map<String, Object> callMLService(BorrowerInput input) {
        String predictUrl = mlServiceUrl + "/predict";
        
        try {
            // Build request body matching the Python PredictRequest schema
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("monthly_income", input.getMonthlyIncome().doubleValue());
            requestBody.put("upi_frequency", input.getUpiFrequency());
            requestBody.put("avg_upi_amount", input.getAvgUpiAmount().doubleValue());
            requestBody.put("utility_consistency", input.getUtilityConsistency());
            requestBody.put("recharge_amount", input.getRechargeAmount().doubleValue());
            requestBody.put("agricultural_yield", 
                input.getAgriculturalYield() != null 
                    ? input.getAgriculturalYield().doubleValue() : 0.0);
            requestBody.put("loan_requested", input.getLoanRequested().doubleValue());
            requestBody.put("occupation", input.getOccupation());
            requestBody.put("state", input.getState());
            requestBody.put("gender", input.getGender() != null ? input.getGender() : "Other");

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);

            log.info("Calling ML service at: {}", predictUrl);
            ResponseEntity<String> response = restTemplate.postForEntity(
                predictUrl, entity, String.class
            );

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                return objectMapper.readValue(
                    response.getBody(), 
                    new TypeReference<Map<String, Object>>() {}
                );
            }
            
            log.warn("ML service returned non-success status: {}", response.getStatusCode());
            
        } catch (Exception e) {
            log.error("ML service call failed: {} — using fallback scoring", e.getMessage());
        }
        
        // ── Fallback: Simple rule-based scoring if ML service is down ──
        return fallbackScoring(input);
    }

    /**
     * Fallback scoring when ML service is unavailable.
     * 
     * Uses a simple weighted formula based on the same alternate
     * financial indicators. This ensures the application remains
     * functional even if the Python service is temporarily down.
     */
    private Map<String, Object> fallbackScoring(BorrowerInput input) {
        log.warn("Using fallback scoring — ML service unavailable");
        
        double incomeNorm = Math.min(input.getMonthlyIncome().doubleValue() / 50000.0, 1.0);
        double upiNorm = Math.min(input.getUpiFrequency() / 100.0, 1.0);
        double upiAmtNorm = Math.min(input.getAvgUpiAmount().doubleValue() / 5000.0, 1.0);
        
        double utilityScore = switch (input.getUtilityConsistency()) {
            case "Always on time" -> 1.0;
            case "Sometimes late" -> 0.5;
            default -> 0.15;
        };
        
        double rechargeNorm = Math.min(input.getRechargeAmount().doubleValue() / 500.0, 1.0);
        
        double rawScore = 0.25 * incomeNorm + 0.20 * upiNorm + 0.15 * upiAmtNorm 
                        + 0.20 * utilityScore + 0.10 * rechargeNorm + 0.10 * 0.5;
        
        int score = (int) Math.min(Math.max(rawScore * 100, 0), 100);
        String risk = score >= 70 ? "Low Risk" : (score >= 40 ? "Medium Risk" : "High Risk");
        boolean eligible = score >= 30;
        double maxLoan = eligible 
            ? Math.min(input.getMonthlyIncome().doubleValue() * (6 + score * 0.18), 
                       input.getLoanRequested().doubleValue()) 
            : 0;
        
        String interestRate = score >= 70 ? "10-12% p.a." 
            : (score >= 50 ? "14-16% p.a." : (score >= 30 ? "18-22% p.a." : "Not recommended"));

        Map<String, Object> result = new HashMap<>();
        result.put("score", score);
        result.put("risk_category", risk);
        result.put("loan_eligible", eligible);
        result.put("max_loan_amount", maxLoan);
        result.put("suggested_interest_rate", interestRate);
        result.put("confidence", 0.60); // Lower confidence for fallback
        return result;
    }

    /**
     * Retrieve a saved assessment by ID.
     * 
     * @param id Assessment database ID
     * @return CreditResult DTO or null if not found
     */
    public CreditResult getAssessmentById(Long id) {
        return borrowerRepository.findById(id)
            .map(this::entityToResult)
            .orElse(null);
    }

    /**
     * Convert a BorrowerInput entity to a CreditResult DTO.
     * Deserializes the JSON-stored RAG tips back into objects.
     */
    private CreditResult entityToResult(BorrowerInput entity) {
        List<CreditResult.FinancialTip> tips = new ArrayList<>();
        
        // Deserialize RAG tips from JSON string
        if (entity.getRagTips() != null && !entity.getRagTips().isEmpty()) {
            try {
                tips = objectMapper.readValue(
                    entity.getRagTips(),
                    new TypeReference<List<CreditResult.FinancialTip>>() {}
                );
            } catch (Exception e) {
                log.warn("Failed to deserialize RAG tips for assessment {}: {}", 
                         entity.getId(), e.getMessage());
            }
        }

        // Determine which explanation to use
        String explanation = entity.getGeminiExplanationEn();
        String lang = "English";
        if ("Hindi".equalsIgnoreCase(entity.getPreferredLanguage()) 
            && entity.getGeminiExplanationHi() != null) {
            explanation = entity.getGeminiExplanationHi();
            lang = "Hindi";
        }

        return CreditResult.builder()
                .id(entity.getId())
                .fullName(entity.getFullName())
                .occupation(entity.getOccupation())
                .state(entity.getState())
                .gender(entity.getGender())
                .monthlyIncome(entity.getMonthlyIncome())
                .loanRequested(entity.getLoanRequested())
                .creditScore(entity.getCreditScore())
                .riskCategory(entity.getRiskCategory())
                .loanEligible(entity.getLoanEligible())
                .maxLoanAmount(entity.getMaxLoanAmount())
                .suggestedInterestRate(entity.getSuggestedInterestRate())
                .mlConfidence(entity.getMlConfidence())
                .explanation(explanation)
                .explanationLanguage(lang)
                .tips(tips)
                .build();
    }
}
