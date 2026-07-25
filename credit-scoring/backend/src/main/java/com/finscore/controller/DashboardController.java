package com.finscore.controller;

import com.finscore.repository.BorrowerRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * DashboardController — Admin Dashboard API
 * =============================================
 * Provides REST endpoints for the admin dashboard:
 * - Summary statistics (total assessments, avg score, etc.)
 * - Chart data (score distribution, state/occupation/gender comparisons)
 * 
 * All endpoints under /api/dashboard require authentication
 * via Spring Security.
 * 
 * @author FinScore AI Team
 */
@RestController
@RequestMapping("/api/dashboard")
@Slf4j
@RequiredArgsConstructor
public class DashboardController {

    private final BorrowerRepository borrowerRepository;

    /**
     * GET /api/dashboard/stats — Summary statistics for dashboard cards
     * 
     * Returns:
     * - totalAssessments: Count of completed assessments
     * - avgScore: Average credit score (rounded)
     * - eligibilityRate: Percentage of eligible borrowers
     * - topOccupation: Most common occupation assessed
     */
    @GetMapping("/stats")
    public ResponseEntity<?> getDashboardStats() {
        try {
            Map<String, Object> stats = new HashMap<>();
            
            Long total = borrowerRepository.countCompletedAssessments();
            stats.put("totalAssessments", total != null ? total : 0);
            
            Double avgScore = borrowerRepository.getAverageCreditScore();
            stats.put("avgScore", avgScore != null ? Math.round(avgScore) : 0);
            
            Double eligibilityRate = borrowerRepository.getLoanEligibilityRate();
            stats.put("eligibilityRate", eligibilityRate != null ? Math.round(eligibilityRate) : 0);
            
            String topOccupation = borrowerRepository.getMostCommonOccupation();
            stats.put("topOccupation", topOccupation != null ? topOccupation : "N/A");
            
            return ResponseEntity.ok(stats);
            
        } catch (Exception e) {
            log.error("Failed to fetch dashboard stats: {}", e.getMessage());
            return ResponseEntity.internalServerError().body(Map.of(
                "error", "Failed to load dashboard statistics."
            ));
        }
    }

    /**
     * GET /api/dashboard/charts — All chart data in a single response
     * 
     * Aggregates all chart data to minimize API calls from the dashboard.
     * Returns score distribution, state averages, occupation comparison,
     * gender bias data, risk category counts, and daily volume.
     */
    @GetMapping("/charts")
    public ResponseEntity<?> getChartData() {
        try {
            Map<String, Object> chartData = new HashMap<>();
            
            // Score distribution histogram
            List<Object[]> scoreDist = borrowerRepository.getScoreDistribution();
            chartData.put("scoreDistribution", formatPairData(scoreDist));
            
            // State-wise average score
            List<Object[]> stateAvg = borrowerRepository.getAverageScoreByState();
            chartData.put("stateWiseScore", formatPairData(stateAvg));
            
            // Occupation-wise average score
            List<Object[]> occAvg = borrowerRepository.getAverageScoreByOccupation();
            chartData.put("occupationWiseScore", formatPairData(occAvg));
            
            // Gender-wise average score (bias monitoring)
            List<Object[]> genderAvg = borrowerRepository.getAverageScoreByGender();
            chartData.put("genderWiseScore", formatTripleData(genderAvg));
            
            // Risk category counts
            List<Object[]> riskCounts = borrowerRepository.getCountByRiskCategory();
            chartData.put("riskDistribution", formatPairData(riskCounts));
            
            // Daily assessment volume (last 30 days)
            try {
                List<Object[]> dailyVol = borrowerRepository.getDailyVolume();
                chartData.put("dailyVolume", formatPairData(dailyVol));
            } catch (Exception e) {
                // dailyVolume uses native query which may fail on H2
                chartData.put("dailyVolume", Map.of("labels", List.of(), "values", List.of()));
                log.warn("Daily volume query failed (expected on H2): {}", e.getMessage());
            }
            
            return ResponseEntity.ok(chartData);
            
        } catch (Exception e) {
            log.error("Failed to fetch chart data: {}", e.getMessage());
            return ResponseEntity.internalServerError().body(Map.of(
                "error", "Failed to load chart data."
            ));
        }
    }

    /**
     * Format [label, value] pairs into { labels: [...], values: [...] }
     * structure expected by Chart.js.
     */
    private Map<String, Object> formatPairData(List<Object[]> data) {
        List<String> labels = new ArrayList<>();
        List<Number> values = new ArrayList<>();
        
        if (data != null) {
            for (Object[] row : data) {
                labels.add(row[0] != null ? row[0].toString() : "Unknown");
                values.add(row[1] != null ? ((Number) row[1]) : 0);
            }
        }
        
        Map<String, Object> result = new HashMap<>();
        result.put("labels", labels);
        result.put("values", values);
        return result;
    }

    /**
     * Format [label, value, count] triples for gender data
     * (includes count for context in bias monitoring).
     */
    private Map<String, Object> formatTripleData(List<Object[]> data) {
        List<String> labels = new ArrayList<>();
        List<Number> values = new ArrayList<>();
        List<Number> counts = new ArrayList<>();
        
        if (data != null) {
            for (Object[] row : data) {
                labels.add(row[0] != null ? row[0].toString() : "Unknown");
                values.add(row[1] != null ? ((Number) row[1]) : 0);
                counts.add(row.length > 2 && row[2] != null ? ((Number) row[2]) : 0);
            }
        }
        
        Map<String, Object> result = new HashMap<>();
        result.put("labels", labels);
        result.put("values", values);
        result.put("counts", counts);
        return result;
    }
}
