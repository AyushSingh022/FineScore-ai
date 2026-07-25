package com.finscore.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.finscore.model.BorrowerInput;
import com.finscore.model.CreditResult;
import com.finscore.repository.BorrowerRepository;
import com.finscore.service.CreditScoringService;
import com.finscore.service.GeminiService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * CreditController — REST API Endpoints
 * ========================================
 * Handles all credit scoring API endpoints including:
 * - POST /api/credit/score — Submit assessment
 * - POST /api/credit/explain — Toggle explanation language
 * - GET  /api/credit/history — Paginated assessment history
 * - GET  /api/states — Indian states dropdown data
 * - GET  /api/occupations — Occupation dropdown data
 * - GET  /api/consistency-options — Bill consistency dropdown data
 * 
 * All dropdown data is served via API endpoints (not hardcoded in HTML).
 * 
 * @author FinScore AI Team
 */
@RestController
@RequestMapping("/api")
@Slf4j
@RequiredArgsConstructor
public class CreditController {

    private final CreditScoringService creditScoringService;
    private final GeminiService geminiService;
    private final BorrowerRepository borrowerRepository;
    private final ObjectMapper objectMapper;

    // ═══ CREDIT SCORING ENDPOINTS ═══════════════════════════════════════

    /**
     * POST /api/credit/score — Process a credit assessment
     * 
     * Full pipeline:
     * 1. Validate consent flag
     * 2. Call Python ML service for XGBoost prediction
     * 3. Call Gemini API for explanation
     * 4. Call RAG pipeline for financial tips
     * 5. Save to MySQL
     * 6. Return CreditResult
     */
    @PostMapping("/credit/score")
    public ResponseEntity<?> processScore(
            @Valid @RequestBody BorrowerInput input,
            @RequestHeader(value = "X-Forwarded-For", required = false) String forwardedFor,
            @RequestHeader(value = "X-Real-IP", required = false) String realIp) {
        
        try {
            // Record IP address for audit (prefer forwarded header for proxied requests)
            String ip = forwardedFor != null ? forwardedFor.split(",")[0].trim() 
                       : (realIp != null ? realIp : "unknown");
            input.setIpAddress(ip);

            // Process the assessment
            CreditResult result = creditScoringService.processAssessment(input);
            
            log.info("Assessment completed: ID={}, Score={}, Risk={}", 
                     result.getId(), result.getCreditScore(), result.getRiskCategory());
            
            return ResponseEntity.ok(result);
            
        } catch (IllegalArgumentException e) {
            // Consent not given or validation failure
            log.warn("Validation error: {}", e.getMessage());
            return ResponseEntity.badRequest().body(Map.of(
                "error", e.getMessage(),
                "field", "consentGiven"
            ));
        } catch (Exception e) {
            log.error("Assessment processing failed: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().body(Map.of(
                "error", "An error occurred while processing your assessment. Please try again.",
                "details", e.getMessage()
            ));
        }
    }

    /**
     * POST /api/credit/explain — Re-generate explanation in different language
     * 
     * Allows users to toggle between Hindi and English explanations
     * on the result page without re-running the full assessment.
     */
    @PostMapping("/credit/explain")
    public ResponseEntity<?> toggleExplanation(@RequestBody Map<String, Object> request) {
        try {
            Long scoreId = Long.valueOf(request.get("scoreId").toString());
            String language = (String) request.getOrDefault("language", "English");
            
            // Fetch the saved assessment
            Optional<BorrowerInput> optionalInput = borrowerRepository.findById(scoreId);
            if (optionalInput.isEmpty()) {
                return ResponseEntity.notFound().build();
            }
            
            BorrowerInput saved = optionalInput.get();
            
            // Check if we already have the explanation in this language cached
            String cachedExplanation = "Hindi".equalsIgnoreCase(language) 
                ? saved.getGeminiExplanationHi() 
                : saved.getGeminiExplanationEn();
            
            if (cachedExplanation != null && !cachedExplanation.isEmpty()) {
                return ResponseEntity.ok(Map.of("explanation", cachedExplanation));
            }
            
            // Generate fresh explanation via Gemini
            String explanation = geminiService.generateExplanation(
                saved.getOccupation(),
                saved.getState(),
                saved.getMonthlyIncome().intValue(),
                saved.getCreditScore(),
                saved.getRiskCategory(),
                saved.getLoanRequested().intValue(),
                language
            );
            
            // Cache the new explanation
            if ("Hindi".equalsIgnoreCase(language)) {
                saved.setGeminiExplanationHi(explanation);
            } else {
                saved.setGeminiExplanationEn(explanation);
            }
            borrowerRepository.save(saved);
            
            return ResponseEntity.ok(Map.of("explanation", explanation));
            
        } catch (Exception e) {
            log.error("Explanation toggle failed: {}", e.getMessage());
            return ResponseEntity.internalServerError().body(Map.of(
                "error", "Failed to generate explanation. Please try again."
            ));
        }
    }

    /**
     * GET /api/credit/history — Paginated assessment history
     * 
     * Supports filtering by state, occupation, and risk category.
     * Used by the dashboard table and chart data.
     */
    @GetMapping("/credit/history")
    public ResponseEntity<?> getHistory(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String state,
            @RequestParam(required = false) String occupation,
            @RequestParam(required = false) String riskCategory) {
        
        try {
            PageRequest pageRequest = PageRequest.of(page, size, Sort.by("createdAt").descending());
            Page<BorrowerInput> results;
            
            // Apply filters if provided
            if (state != null && !state.isEmpty() && occupation != null && !occupation.isEmpty()) {
                results = borrowerRepository.findByStateAndOccupation(state, occupation, pageRequest);
            } else if (state != null && !state.isEmpty()) {
                results = borrowerRepository.findByState(state, pageRequest);
            } else if (occupation != null && !occupation.isEmpty()) {
                results = borrowerRepository.findByOccupation(occupation, pageRequest);
            } else if (riskCategory != null && !riskCategory.isEmpty()) {
                results = borrowerRepository.findByRiskCategory(riskCategory, pageRequest);
            } else {
                results = borrowerRepository.findAllByOrderByCreatedAtDesc(pageRequest);
            }
            
            // Build response with pagination metadata
            Map<String, Object> response = new HashMap<>();
            response.put("content", results.getContent());
            response.put("totalElements", results.getTotalElements());
            response.put("totalPages", results.getTotalPages());
            response.put("currentPage", results.getNumber());
            response.put("pageSize", results.getSize());
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("History fetch failed: {}", e.getMessage());
            return ResponseEntity.internalServerError().body(Map.of(
                "error", "Failed to fetch assessment history."
            ));
        }
    }

    // ═══ DROPDOWN DATA ENDPOINTS ════════════════════════════════════════
    // All form dropdown data served via REST API — zero hardcoded values

    /**
     * GET /api/states — All 28 Indian states + 8 Union Territories
     * Alphabetically sorted.
     */
    @GetMapping("/states")
    public ResponseEntity<List<String>> getStates() {
        List<String> states = List.of(
            "Andhra Pradesh", "Arunachal Pradesh", "Assam", "Bihar",
            "Chhattisgarh", "Goa", "Gujarat", "Haryana", "Himachal Pradesh",
            "Jharkhand", "Karnataka", "Kerala", "Madhya Pradesh",
            "Maharashtra", "Manipur", "Meghalaya", "Mizoram", "Nagaland",
            "Odisha", "Punjab", "Rajasthan", "Sikkim", "Tamil Nadu",
            "Telangana", "Tripura", "Uttar Pradesh", "Uttarakhand",
            "West Bengal",
            // Union Territories
            "Andaman and Nicobar Islands", "Chandigarh",
            "Dadra and Nagar Haveli and Daman and Diu", "Delhi",
            "Jammu and Kashmir", "Ladakh", "Lakshadweep", "Puducherry"
        );
        
        // Return sorted list
        List<String> sorted = new ArrayList<>(states);
        Collections.sort(sorted);
        return ResponseEntity.ok(sorted);
    }

    /**
     * GET /api/occupations — Available occupation options
     * Covers key rural informal sector occupations.
     */
    @GetMapping("/occupations")
    public ResponseEntity<List<String>> getOccupations() {
        return ResponseEntity.ok(List.of(
            "Farmer",
            "Daily Wage Laborer",
            "Street Vendor",
            "SHG Member",
            "Migrant Worker",
            "Small Business Owner",
            "Artisan/Craftsperson",
            "Fisher",
            "Other"
        ));
    }

    /**
     * GET /api/consistency-options — Utility bill payment consistency levels
     */
    @GetMapping("/consistency-options")
    public ResponseEntity<List<String>> getConsistencyOptions() {
        return ResponseEntity.ok(List.of(
            "Always on time",
            "Sometimes late",
            "Frequently late"
        ));
    }
}
