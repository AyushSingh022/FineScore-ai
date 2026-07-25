package com.finscore.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;

/**
 * SecurityConfig — Spring Security Configuration
 * ==================================================
 * Configures authentication and authorization rules:
 * 
 * PUBLIC (no login required):
 * - /          (home page)
 * - /form      (assessment form)
 * - /result/** (result pages)
 * - /login     (custom login page)
 * - /register  (sign-up page)
 * - /api/**    (REST API endpoints except dashboard)
 * - /css/**    (static CSS)
 * - /js/**     (static JS)
 * - /images/** (static images incl. gov emblem)
 * - /h2-console/** (H2 console for dev)
 * 
 * PROTECTED (login required):
 * - /dashboard         (admin dashboard page)
 * - /api/dashboard/**  (dashboard API endpoints)
 * 
 * Authentication uses form login with a custom login.html page.
 * Credentials come from environment variables (ADMIN_USERNAME, ADMIN_PASSWORD).
 * 
 * @author FinScore AI Team
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            // ─── Authorization Rules ────────────────────────────────
            .authorizeHttpRequests(auth -> auth
                // Public pages — accessible to everyone
                .requestMatchers("/", "/form", "/result/**").permitAll()

                // Auth pages — login & register are always public
                .requestMatchers("/login", "/login/**", "/register", "/register/**").permitAll()

                // Public API endpoints — form dropdowns, scoring, history
                .requestMatchers("/api/states", "/api/occupations",
                                 "/api/consistency-options").permitAll()
                .requestMatchers("/api/credit/**").permitAll()
                .requestMatchers("/api/auth/**").permitAll()

                // Static resources (including government emblem image)
                .requestMatchers("/css/**", "/js/**", "/images/**",
                                 "/favicon.ico").permitAll()

                // H2 console (development only)
                .requestMatchers("/h2-console/**").permitAll()

                // Dashboard — requires authentication
                .requestMatchers("/dashboard", "/api/dashboard/**").authenticated()

                // Everything else requires authentication
                .anyRequest().authenticated()
            )

            // ─── Authentication ─────────────────────────────────────
            // Custom login page at /login
            .httpBasic(basic -> {})
            .formLogin(form -> form
                .loginPage("/login")
                .loginProcessingUrl("/login")
                .defaultSuccessUrl("/dashboard", true)
                .failureUrl("/login?error")
                .permitAll()
            )
            .logout(logout -> logout
                .logoutUrl("/logout")
                .logoutSuccessUrl("/login?logout")
                .invalidateHttpSession(true)
                .deleteCookies("JSESSIONID")
                .permitAll()
            )

            // ─── CSRF ───────────────────────────────────────────────
            // Disable CSRF for API endpoints (they use JSON, not forms)
            // Keep CSRF for form submissions via Thymeleaf
            .csrf(csrf -> csrf
                .ignoringRequestMatchers("/api/**", "/h2-console/**")
            )

            // ─── Frame Options ──────────────────────────────────────
            // Allow H2 console to use frames (dev only)
            .headers(headers -> headers
                .frameOptions(frame -> frame.sameOrigin())
            );

        return http.build();
    }
}
