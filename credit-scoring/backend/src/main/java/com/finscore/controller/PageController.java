package com.finscore.controller;

import com.finscore.model.CreditResult;
import com.finscore.service.CreditScoringService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.Map;

/**
 * PageController — Thymeleaf Page Navigation
 * ==============================================
 * Serves HTML pages via Thymeleaf templates.
 *
 * Pages:
 * - GET /            → index.html   (Home page)
 * - GET /form        → form.html    (Assessment form)
 * - GET /result/{id} → result.html  (Score result)
 * - GET /dashboard   → dashboard.html (Admin dashboard)
 * - GET /login       → login.html   (Custom login page)
 * - GET /register    → register.html (Sign-up page)
 * - POST /register   → handles registration form submission
 *
 * @author FinScore AI Team
 */
@Controller
@Slf4j
@RequiredArgsConstructor
public class PageController {

    private final CreditScoringService creditScoringService;

    // ═══════════════════════════════════════════════════════════════
    // Public Pages
    // ═══════════════════════════════════════════════════════════════

    /** GET / — Home Page */
    @GetMapping("/")
    public String homePage(Model model) {
        model.addAttribute("heroTitle", "Fair Credit Scoring for Every Indian");
        model.addAttribute("heroSubtitle",
            "AI-powered alternate credit assessment for 190M+ unbanked Indians who deserve access to fair institutional loans");
        model.addAttribute("heroStat1", "190M+");
        model.addAttribute("heroStat1Label", "Unbanked Indians (World Bank, 2022)");
        model.addAttribute("heroStat2", "24-48%");
        model.addAttribute("heroStat2Label", "Moneylender Interest Rates (RBI, 2023)");
        model.addAttribute("heroStat3", "96%");
        model.addAttribute("heroStat3Label", "SHG Women Repayment Rate");
        model.addAttribute("heroCta", "Check Your Credit Score");

        model.addAttribute("howItWorksTitle", "How It Works");
        model.addAttribute("step1Title", "Share Your Details");
        model.addAttribute("step1Desc",
            "Fill a simple form with your financial behavior — UPI usage, bill payments, income details. No Aadhaar or PAN required.");
        model.addAttribute("step2Title", "AI Analyzes Your Profile");
        model.addAttribute("step2Desc",
            "Our XGBoost ML model evaluates your alternate financial data to generate a fair credit score from 0 to 100.");
        model.addAttribute("step3Title", "Get Your Score & Tips");
        model.addAttribute("step3Desc",
            "Receive your credit score with a plain language explanation and personalized tips to improve it.");

        model.addAttribute("sdgTitle", "Aligned with UN Sustainable Development Goals");
        model.addAttribute("sdg1Title", "SDG 1: No Poverty");
        model.addAttribute("sdg1Desc",
            "Direct credit access enables rural Indians to escape predatory moneylenders and build sustainable livelihoods.");
        model.addAttribute("sdg10Title", "SDG 10: Reduced Inequalities");
        model.addAttribute("sdg10Desc",
            "Fair scoring based on actual financial behavior — not formal employment — reduces systemic financial exclusion.");

        model.addAttribute("responsibleAiTitle", "Built on Responsible AI Principles");
        model.addAttribute("rai1Title", "Equity & Inclusion");
        model.addAttribute("rai1Desc",
            "Designed specifically for 190M+ excluded Indians — farmers, daily wage workers, SHG women, and migrant workers.");
        model.addAttribute("rai2Title", "Transparency & Explainability");
        model.addAttribute("rai2Desc",
            "Every score comes with a plain language explanation powered by Google Gemini AI — in Hindi or English.");
        model.addAttribute("rai3Title", "Privacy by Design");
        model.addAttribute("rai3Desc",
            "Consent-first approach. We never store Aadhaar, PAN, or biometric data. Your information is encrypted.");
        model.addAttribute("rai4Title", "Bias Mitigation");
        model.addAttribute("rai4Desc",
            "Gender does not influence your credit score. Our model is continuously monitored for regional and demographic bias.");

        model.addAttribute("footerDisclaimer",
            "FinScore AI is an educational project demonstrating responsible AI for financial inclusion. " +
            "It is not a licensed financial product and should not be used as a substitute for professional financial advice.");
        model.addAttribute("footerPrivacy",
            "Your data is processed with your explicit consent and is never shared with third parties.");

        return "index";
    }

    /** GET /form — Credit Assessment Form Page */
    @GetMapping("/form")
    public String formPage(Model model) {
        model.addAttribute("pageTitle", "Credit Assessment Form");
        model.addAttribute("pageSubtitle",
            "Fill in your financial details below. All information is encrypted and used solely for credit assessment.");
        model.addAttribute("consentText",
            "I consent to my financial behavior data being used solely for credit assessment purposes. " +
            "I understand that no Aadhaar, PAN, or biometric data will be collected.");
        model.addAttribute("submitButtonText", "Calculate My Credit Score");
        model.addAttribute("privacyNote",
            "Your data is encrypted end-to-end and never shared with third parties. " +
            "We comply with RBI data protection guidelines.");
        return "form";
    }

    /** GET /result/{id} — Credit Assessment Result Page */
    @GetMapping("/result/{id}")
    public String resultPage(@PathVariable Long id, Model model) {
        CreditResult result = creditScoringService.getAssessmentById(id);

        if (result == null) {
            model.addAttribute("error", "Assessment not found. Please try again.");
            return "form";
        }

        model.addAttribute("result", result);
        model.addAttribute("pageTitle", "Your FinScore AI Credit Assessment");
        model.addAttribute("scoreLabel", "Your Credit Score");
        model.addAttribute("riskLabel", "Risk Category");
        model.addAttribute("loanEligibilityTitle", "Loan Eligibility");
        model.addAttribute("explanationTitle", "Why You Got This Score");
        model.addAttribute("tipsTitle", "How To Improve Your Score");
        model.addAttribute("transparencyNote",
            "This score was generated by an AI model trained on anonymized alternate financial data. " +
            "It is not a substitute for professional financial advice. The model does not use Aadhaar, " +
            "PAN, caste, religion, or any protected characteristic in scoring.");
        model.addAttribute("downloadButtonText", "Download as PDF");
        model.addAttribute("retryButtonText", "Assess Another Borrower");

        return "result";
    }

    // ═══════════════════════════════════════════════════════════════
    // Admin Dashboard (Protected)
    // ═══════════════════════════════════════════════════════════════

    /**
     * GET /dashboard — Admin Dashboard Page
     * Protected by Spring Security — requires login.
     */
    @GetMapping("/dashboard")
    public String dashboardPage(Model model) {
        model.addAttribute("pageTitle", "FinScore AI Dashboard");
        model.addAttribute("pageSubtitle", "Assessment Analytics & Bias Monitoring");
        return "dashboard";
    }

    // ═══════════════════════════════════════════════════════════════
    // Auth Pages — Login & Registration
    // ═══════════════════════════════════════════════════════════════

    /**
     * GET /login — Custom Login Page
     *
     * Spring Security handles the POST /login submission automatically.
     * This controller only renders the Thymeleaf login.html template.
     * Query params ?error and ?logout are detected via Thymeleaf th:if.
     */
    @GetMapping("/login")
    public String loginPage() {
        return "login";
    }

    /** GET /register — Sign-Up / Registration Page */
    @GetMapping("/register")
    public String registerPage(Model model) {
        model.addAttribute("pageTitle", "Create Account — FinScore AI");
        return "register";
    }

    /**
     * POST /register — Handle Registration Form Submission
     *
     * Validates the submitted fields and redirects with flash messages.
     * In a production build, wire this to a UserDetailsService and
     * UserRepository to persist the new admin user.
     */
    @PostMapping("/register")
    public String handleRegister(
            @RequestParam Map<String, String> params,
            RedirectAttributes redirectAttributes) {

        String username  = params.getOrDefault("username", "").trim();
        String password  = params.getOrDefault("password", "").trim();
        String confirm   = params.getOrDefault("confirmPassword", "").trim();
        String firstName = params.getOrDefault("firstName", "").trim();
        String email     = params.getOrDefault("email", "").trim();

        log.info("Registration attempt for username: {}", username);

        if (username.isEmpty() || password.isEmpty() || firstName.isEmpty() || email.isEmpty()) {
            redirectAttributes.addFlashAttribute("error", "All required fields must be filled in.");
            return "redirect:/register";
        }
        if (!password.equals(confirm)) {
            redirectAttributes.addFlashAttribute("error", "Passwords do not match. Please try again.");
            return "redirect:/register";
        }
        if (password.length() < 8) {
            redirectAttributes.addFlashAttribute("error", "Password must be at least 8 characters long.");
            return "redirect:/register";
        }

        // TODO: Persist user — userRepository.save(new AdminUser(...))
        log.info("New registration accepted: username={}, email={}", username, email);

        redirectAttributes.addFlashAttribute("success",
            "Account created! Your request is pending admin approval. " +
            "You will be notified at " + email + ".");
        return "redirect:/login";
    }
}
