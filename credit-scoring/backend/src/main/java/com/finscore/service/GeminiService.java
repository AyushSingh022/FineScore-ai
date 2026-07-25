package com.finscore.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.*;

/**
 * GeminiService — Google Gemini AI Integration
 * ===============================================
 * Calls the Gemini 1.5 Flash model to generate empathetic,
 * plain-language explanations of credit scores.
 * 
 * The prompt is designed to be:
 * - Compassionate and non-judgmental
 * - Written in simple everyday language
 * - Actionable with specific improvement steps
 * - Available in both Hindi and English
 * - Mentioning relevant government schemes
 * 
 * API key is sourced from environment variable GEMINI_API_KEY.
 * If the key is missing or the API fails, a graceful fallback
 * explanation is returned.
 * 
 * @author FinScore AI Team
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class GeminiService {

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    @Value("${gemini.api.key:}")
    private String apiKey;

    @Value("${gemini.api.model:gemini-1.5-flash}")
    private String model;

    /** Gemini API base URL */
    private static final String GEMINI_API_URL = 
        "https://generativelanguage.googleapis.com/v1beta/models/%s:generateContent?key=%s";

    /**
     * Generate a credit score explanation using Gemini AI.
     * 
     * @param occupation  Borrower's occupation
     * @param state       Borrower's state
     * @param income      Monthly income in INR
     * @param score       Credit score (0-100)
     * @param riskCategory Risk category string
     * @param loanRequested Loan amount requested
     * @param language    Target language: "Hindi" or "English"
     * @return Plain text explanation in the requested language
     */
    public String generateExplanation(
            String occupation, String state, int income,
            int score, String riskCategory, int loanRequested,
            String language) {
        
        // Check if API key is available
        if (apiKey == null || apiKey.isBlank()) {
            log.warn("Gemini API key not configured — using fallback explanation");
            return generateFallbackExplanation(
                occupation, score, riskCategory, loanRequested, language
            );
        }

        try {
            // Build the empathetic prompt
            String prompt = buildPrompt(
                occupation, state, income, score, 
                riskCategory, loanRequested, language
            );

            // Build Gemini API request body
            Map<String, Object> requestBody = buildRequestBody(prompt);
            
            String url = String.format(GEMINI_API_URL, model, apiKey);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);

            log.info("Calling Gemini API for {} explanation (score: {})", language, score);
            
            ResponseEntity<String> response = restTemplate.postForEntity(
                url, entity, String.class
            );

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                return extractTextFromResponse(response.getBody());
            }

            log.warn("Gemini API returned status: {}", response.getStatusCode());

        } catch (Exception e) {
            log.error("Gemini API call failed: {}", e.getMessage());
        }

        // Fallback if API call fails
        return generateFallbackExplanation(
            occupation, score, riskCategory, loanRequested, language
        );
    }

    /**
     * Build the empathetic prompt template for Gemini.
     * 
     * The prompt is carefully crafted to produce explanations that
     * a person with basic literacy can understand. It avoids financial
     * jargon and uses an encouraging, supportive tone.
     */
    private String buildPrompt(
            String occupation, String state, int income,
            int score, String riskCategory, int loanRequested,
            String language) {
        
        return String.format("""
            You are a compassionate financial advisor helping rural Indians
            understand their credit assessment result.
            
            Borrower Profile:
            - Occupation: %s
            - State: %s
            - Monthly Income: ₹%,d
            - Credit Score: %d out of 100
            - Risk Category: %s
            - Loan Requested: ₹%,d
            
            Task: Explain this credit score result in simple %s language
            that a person with basic literacy can understand.
            
            Instructions:
            - Start with an encouraging, empathetic opening
            - Explain what the score means in practical terms
            - Give 3 specific actionable steps to improve their score
            - Mention one relevant government scheme they may qualify for
            - End with an encouraging closing message
            - Keep total response under 200 words
            - Use simple everyday words, avoid financial jargon
            - If language is Hindi, respond entirely in Hindi (Devanagari script)
            - Do NOT use markdown formatting, bullet points with *, or headers
            - Write in plain paragraphs with numbered steps
            """,
            occupation, state, income, score, riskCategory, loanRequested, language
        );
    }

    /**
     * Build the Gemini API request body in the required format.
     */
    private Map<String, Object> buildRequestBody(String prompt) {
        Map<String, Object> textPart = new HashMap<>();
        textPart.put("text", prompt);

        Map<String, Object> contentParts = new HashMap<>();
        contentParts.put("parts", List.of(textPart));

        Map<String, Object> generationConfig = new HashMap<>();
        generationConfig.put("temperature", 0.7);
        generationConfig.put("maxOutputTokens", 500);
        generationConfig.put("topP", 0.9);

        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("contents", List.of(contentParts));
        requestBody.put("generationConfig", generationConfig);

        return requestBody;
    }

    /**
     * Extract the generated text from Gemini's JSON response.
     */
    private String extractTextFromResponse(String responseBody) {
        try {
            JsonNode root = objectMapper.readTree(responseBody);
            JsonNode candidates = root.path("candidates");
            
            if (candidates.isArray() && candidates.size() > 0) {
                JsonNode content = candidates.get(0).path("content");
                JsonNode parts = content.path("parts");
                
                if (parts.isArray() && parts.size() > 0) {
                    return parts.get(0).path("text").asText();
                }
            }
            
            log.warn("Unexpected Gemini response structure");
        } catch (Exception e) {
            log.error("Failed to parse Gemini response: {}", e.getMessage());
        }
        
        return null;
    }

    /**
     * Generate a fallback explanation when Gemini API is unavailable.
     * 
     * Provides a helpful, empathetic explanation based on the score
     * and risk category. Available in both Hindi and English.
     */
    private String generateFallbackExplanation(
            String occupation, int score, String riskCategory,
            int loanRequested, String language) {
        
        if ("Hindi".equalsIgnoreCase(language)) {
            return generateHindiFallback(occupation, score, riskCategory, loanRequested);
        }
        return generateEnglishFallback(occupation, score, riskCategory, loanRequested);
    }

    private String generateEnglishFallback(
            String occupation, int score, String riskCategory, int loanRequested) {
        
        StringBuilder sb = new StringBuilder();
        
        if (score >= 70) {
            sb.append("Great news! Your credit score of ").append(score)
              .append(" out of 100 shows that you have strong financial habits. ")
              .append("As a ").append(occupation).append(", your consistent financial behavior ")
              .append("makes you eligible for a loan. ");
        } else if (score >= 40) {
            sb.append("Your credit score is ").append(score)
              .append(" out of 100, which shows moderate financial activity. ")
              .append("As a ").append(occupation).append(", you have a good foundation ")
              .append("but there are ways to improve your score. ");
        } else {
            sb.append("Your credit score is ").append(score)
              .append(" out of 100. While this score is currently low, ")
              .append("please don't worry — there are simple steps you can take ")
              .append("to improve it. As a ").append(occupation)
              .append(", you have opportunities to build your financial profile. ");
        }
        
        sb.append("\n\nHere are 3 steps to improve your score:\n")
          .append("1. Use UPI for daily payments — even small amounts help build your financial trail.\n")
          .append("2. Pay electricity and phone bills on time every month.\n")
          .append("3. Save a small amount regularly in your bank account.\n\n");
        
        sb.append("You may also benefit from the Pradhan Mantri MUDRA Yojana, ")
          .append("which provides loans up to ₹50,000 without any guarantee. ")
          .append("Keep up your good work and your score will improve!");
        
        return sb.toString();
    }

    private String generateHindiFallback(
            String occupation, int score, String riskCategory, int loanRequested) {
        
        StringBuilder sb = new StringBuilder();
        
        if (score >= 70) {
            sb.append("बहुत अच्छी खबर! आपका क्रेडिट स्कोर ").append(score)
              .append(" है जो 100 में से बहुत अच्छा है। ")
              .append("आपकी वित्तीय आदतें मजबूत हैं और आप ऋण के लिए पात्र हैं। ");
        } else if (score >= 40) {
            sb.append("आपका क्रेडिट स्कोर ").append(score)
              .append(" है जो 100 में से ठीक है। ")
              .append("आपके पास अच्छी नींव है लेकिन स्कोर बेहतर करने के तरीके हैं। ");
        } else {
            sb.append("आपका क्रेडिट स्कोर ").append(score)
              .append(" है। चिंता न करें — कुछ आसान कदम उठाकर ")
              .append("आप इसे बेहतर बना सकते हैं। ");
        }
        
        sb.append("\n\nस्कोर सुधारने के 3 उपाय:\n")
          .append("1. रोजमर्रा के भुगतान के लिए UPI का उपयोग करें।\n")
          .append("2. बिजली और फोन का बिल हर महीने समय पर भरें।\n")
          .append("3. बैंक खाते में नियमित रूप से छोटी बचत करें।\n\n");
        
        sb.append("प्रधानमंत्री मुद्रा योजना के तहत आप बिना गारंटी ")
          .append("₹50,000 तक का ऋण प्राप्त कर सकते हैं। ")
          .append("हिम्मत रखें, आपका स्कोर जरूर सुधरेगा!");
        
        return sb.toString();
    }
}
