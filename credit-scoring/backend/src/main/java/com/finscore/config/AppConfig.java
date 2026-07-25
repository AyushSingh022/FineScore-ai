package com.finscore.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * AppConfig — Application Bean Configuration
 * ==============================================
 * Configures shared beans used across the application:
 * - RestTemplate for external API calls (ML service, Gemini)
 * - ObjectMapper with Java 8 time support
 * - CORS configuration for API access
 * 
 * @author FinScore AI Team
 */
@Configuration
public class AppConfig {

    /**
     * RestTemplate bean for HTTP calls to:
     * - Python ML service (localhost:8000)
     * - Google Gemini API
     */
    @Bean
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }

    /**
     * ObjectMapper configured with Java 8 date/time support.
     * Handles LocalDateTime serialization for API responses.
     */
    @Bean
    public ObjectMapper objectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        return mapper;
    }

    /**
     * CORS configuration allowing frontend JavaScript to call
     * API endpoints from the same origin.
     */
    @Bean
    public WebMvcConfigurer corsConfigurer() {
        return new WebMvcConfigurer() {
            @Override
            public void addCorsMappings(CorsRegistry registry) {
                registry.addMapping("/api/**")
                        .allowedOrigins("*")
                        .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                        .allowedHeaders("*");
            }
        };
    }
}
