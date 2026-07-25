package com.finscore.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.finscore.model.CreditResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.*;

/**
 * RAGService — Retrieval-Augmented Generation Tips Client
 * =========================================================
 * Calls the Python FastAPI /rag/tips endpoint to retrieve
 * personalized financial tips from the FAISS vector store.
 * 
 * Tips are based on RBI financial inclusion guidelines and
 * are personalized to the borrower's occupation and score range.
 * 
 * Falls back to hardcoded tips if the Python service is unavailable.
 * 
 * @author FinScore AI Team
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class RAGService {

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    @Value("${ml.service.url}")
    private String mlServiceUrl;

    /**
     * Get personalized financial tips from the RAG pipeline.
     * 
     * @param score      Credit score (0-100)
     * @param occupation Borrower's occupation
     * @param state      Borrower's state
     * @return List of 3 FinancialTip objects
     */
    public List<CreditResult.FinancialTip> getFinancialTips(
            int score, String occupation, String state) {
        
        String tipsUrl = mlServiceUrl + "/rag/tips";
        
        try {
            // Build request body
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("score", score);
            requestBody.put("occupation", occupation);
            requestBody.put("state", state != null ? state : "");

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);

            log.info("Calling RAG service for tips: occupation={}, score={}", occupation, score);
            
            ResponseEntity<String> response = restTemplate.postForEntity(
                tipsUrl, entity, String.class
            );

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                return parseTipsResponse(response.getBody());
            }

            log.warn("RAG service returned status: {}", response.getStatusCode());

        } catch (Exception e) {
            log.error("RAG service call failed: {} — using fallback tips", e.getMessage());
        }

        // Return fallback tips if service is unavailable
        return getFallbackTips(score, occupation);
    }

    /**
     * Parse the JSON response from the RAG tips endpoint.
     * Expected format: { "tips": [{ "heading", "detail", "icon" }, ...] }
     */
    private List<CreditResult.FinancialTip> parseTipsResponse(String responseBody) {
        try {
            JsonNode root = objectMapper.readTree(responseBody);
            JsonNode tipsNode = root.path("tips");
            
            if (tipsNode.isArray()) {
                List<CreditResult.FinancialTip> tips = new ArrayList<>();
                for (JsonNode tipNode : tipsNode) {
                    tips.add(CreditResult.FinancialTip.builder()
                        .heading(tipNode.path("heading").asText("Financial Tip"))
                        .detail(tipNode.path("detail").asText(""))
                        .icon(tipNode.path("icon").asText("bi-lightbulb"))
                        .build()
                    );
                }
                return tips;
            }
        } catch (Exception e) {
            log.error("Failed to parse RAG tips response: {}", e.getMessage());
        }
        
        return getFallbackTips(50, "Unknown");
    }

    /**
     * Provide fallback tips when RAG service is unavailable.
     * 
     * Tips are categorized by score range to remain relevant
     * even without RAG retrieval.
     */
    private List<CreditResult.FinancialTip> getFallbackTips(int score, String occupation) {
        log.warn("Using fallback tips for {} (score: {})", occupation, score);
        
        List<CreditResult.FinancialTip> tips = new ArrayList<>();

        if (score >= 70) {
            tips.add(CreditResult.FinancialTip.builder()
                .heading("Maintain Your Strong Financial Habits")
                .detail("Your consistent UPI usage and bill payments show excellent financial discipline. "
                    + "Continue these habits to maintain your high credit score and access better loan terms.")
                .icon("bi-trophy")
                .build());
            
            tips.add(CreditResult.FinancialTip.builder()
                .heading("Explore MUDRA Yojana Benefits")
                .detail("With your strong credit profile, you may qualify for MUDRA loans up to ₹10 lakh "
                    + "for business expansion. Visit your nearest bank to learn about Shishu, Kishore, "
                    + "and Tarun loan categories.")
                .icon("bi-award")
                .build());
            
            tips.add(CreditResult.FinancialTip.builder()
                .heading("Build Emergency Savings")
                .detail("Set aside ₹500-1000 every month in your PMJDY account. This safety net "
                    + "protects you during emergencies and demonstrates savings discipline to lenders.")
                .icon("bi-piggy-bank")
                .build());
                
        } else if (score >= 40) {
            tips.add(CreditResult.FinancialTip.builder()
                .heading("Increase Your UPI Usage")
                .detail("Try to make at least 15-20 UPI transactions per month. Use UPI for buying "
                    + "groceries, paying for transport, and receiving payments for your work. "
                    + "Each transaction builds your digital financial trail.")
                .icon("bi-qr-code")
                .build());
            
            tips.add(CreditResult.FinancialTip.builder()
                .heading("Pay Bills Before Due Date")
                .detail("Set phone reminders for electricity and phone bill due dates. "
                    + "Paying on time for 6 months straight can significantly boost your credit score. "
                    + "Even partial payments before the due date are better than late payments.")
                .icon("bi-calendar-check")
                .build());
            
            tips.add(CreditResult.FinancialTip.builder()
                .heading("Join a Self Help Group")
                .detail("SHG membership helps you build savings habits and access small loans "
                    + "at low interest rates. Women SHGs have a 96% repayment rate — the highest "
                    + "in Indian microfinance. Contact your nearest NRLM block office to join.")
                .icon("bi-people")
                .build());
                
        } else {
            tips.add(CreditResult.FinancialTip.builder()
                .heading("Open a Jan Dhan Account")
                .detail("If you don't have a bank account, open a free PMJDY account at any bank. "
                    + "You'll get a free debit card and ₹2 lakh accident insurance. "
                    + "Make small regular deposits to start building your banking history.")
                .icon("bi-bank")
                .build());
            
            tips.add(CreditResult.FinancialTip.builder()
                .heading("Start Using Digital Payments")
                .detail("Download a UPI app (Google Pay, PhonePe, or Paytm) and start "
                    + "making small payments. Even ₹50-100 transactions help build your "
                    + "digital financial trail. Ask shop owners if they accept UPI.")
                .icon("bi-phone")
                .build());
            
            tips.add(CreditResult.FinancialTip.builder()
                .heading("Register on e-SHRAM Portal")
                .detail("Register at eshram.gov.in to get a unique worker ID and access "
                    + "government schemes including PM-KISAN (₹6,000/year), PMSBY insurance, "
                    + "and skill development programs that improve your financial profile.")
                .icon("bi-shield-check")
                .build());
        }

        return tips;
    }
}
