package com.finscore;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * FinScore AI — Main Application Entry Point
 * ============================================
 * Spring Boot application for alternate credit scoring targeting
 * 190M+ unbanked rural Indians using XGBoost ML + Gemini GenAI + RAG.
 * 
 * Starts embedded Tomcat, initializes JPA/Hibernate for MySQL,
 * configures Spring Security for dashboard protection, and
 * serves Thymeleaf templates for the frontend.
 * 
 * @author FinScore AI Team
 * @version 1.0.0
 */
@SpringBootApplication
public class FinScoreAiApplication {

    public static void main(String[] args) {
        SpringApplication.run(FinScoreAiApplication.class, args);
    }
}
