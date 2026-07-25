package com.finscore.repository;

import com.finscore.model.BorrowerInput;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * BorrowerRepository — Data Access Layer for Assessments
 * ========================================================
 * JPA repository providing CRUD operations and custom queries
 * for the borrower_assessments table.
 * 
 * Custom queries support the dashboard's statistical charts
 * and the paginated assessment history endpoint.
 * 
 * @author FinScore AI Team
 */
@Repository
public interface BorrowerRepository extends JpaRepository<BorrowerInput, Long> {

    // ═══ FILTERED QUERIES ════════════════════════════════════════════════

    /** Find assessments by state with pagination */
    Page<BorrowerInput> findByState(String state, Pageable pageable);

    /** Find assessments by occupation with pagination */
    Page<BorrowerInput> findByOccupation(String occupation, Pageable pageable);

    /** Find assessments by risk category with pagination */
    Page<BorrowerInput> findByRiskCategory(String riskCategory, Pageable pageable);

    /** Find assessments by state and occupation with pagination */
    Page<BorrowerInput> findByStateAndOccupation(String state, String occupation, Pageable pageable);

    // ═══ DASHBOARD STATISTICS ════════════════════════════════════════════

    /** Total number of completed assessments */
    @Query("SELECT COUNT(b) FROM BorrowerInput b WHERE b.creditScore IS NOT NULL")
    Long countCompletedAssessments();

    /** Average credit score across all assessments */
    @Query("SELECT COALESCE(AVG(b.creditScore), 0) FROM BorrowerInput b WHERE b.creditScore IS NOT NULL")
    Double getAverageCreditScore();

    /** Loan eligibility rate as percentage */
    @Query("SELECT COALESCE(COUNT(CASE WHEN b.loanEligible = true THEN 1 END) * 100.0 / NULLIF(COUNT(b), 0), 0) " +
           "FROM BorrowerInput b WHERE b.creditScore IS NOT NULL")
    Double getLoanEligibilityRate();

    /** Most common occupation among assessed borrowers */
    @Query("SELECT b.occupation FROM BorrowerInput b WHERE b.creditScore IS NOT NULL " +
           "GROUP BY b.occupation ORDER BY COUNT(b) DESC LIMIT 1")
    String getMostCommonOccupation();

    // ═══ CHART DATA QUERIES ═════════════════════════════════════════════

    /** Score distribution — count of assessments in each score bucket */
    @Query("SELECT " +
           "CASE " +
           "  WHEN b.creditScore BETWEEN 0 AND 10 THEN '0-10' " +
           "  WHEN b.creditScore BETWEEN 11 AND 20 THEN '11-20' " +
           "  WHEN b.creditScore BETWEEN 21 AND 30 THEN '21-30' " +
           "  WHEN b.creditScore BETWEEN 31 AND 40 THEN '31-40' " +
           "  WHEN b.creditScore BETWEEN 41 AND 50 THEN '41-50' " +
           "  WHEN b.creditScore BETWEEN 51 AND 60 THEN '51-60' " +
           "  WHEN b.creditScore BETWEEN 61 AND 70 THEN '61-70' " +
           "  WHEN b.creditScore BETWEEN 71 AND 80 THEN '71-80' " +
           "  WHEN b.creditScore BETWEEN 81 AND 90 THEN '81-90' " +
           "  ELSE '91-100' " +
           "END AS bucket, COUNT(b) " +
           "FROM BorrowerInput b WHERE b.creditScore IS NOT NULL " +
           "GROUP BY bucket ORDER BY bucket")
    List<Object[]> getScoreDistribution();

    /** Average score by state */
    @Query("SELECT b.state, AVG(b.creditScore) " +
           "FROM BorrowerInput b WHERE b.creditScore IS NOT NULL " +
           "GROUP BY b.state ORDER BY AVG(b.creditScore) DESC")
    List<Object[]> getAverageScoreByState();

    /** Average score by occupation */
    @Query("SELECT b.occupation, AVG(b.creditScore) " +
           "FROM BorrowerInput b WHERE b.creditScore IS NOT NULL " +
           "GROUP BY b.occupation ORDER BY AVG(b.creditScore) DESC")
    List<Object[]> getAverageScoreByOccupation();

    /** Average score by gender — for bias monitoring dashboard */
    @Query("SELECT b.gender, AVG(b.creditScore), COUNT(b) " +
           "FROM BorrowerInput b WHERE b.creditScore IS NOT NULL AND b.gender IS NOT NULL " +
           "GROUP BY b.gender")
    List<Object[]> getAverageScoreByGender();

    /** Daily assessment volume for the last 30 days */
    @Query(value = "SELECT DATE(created_at) as day, COUNT(*) " +
           "FROM borrower_assessments " +
           "WHERE credit_score IS NOT NULL AND created_at >= DATE_SUB(CURDATE(), INTERVAL 30 DAY) " +
           "GROUP BY day ORDER BY day", nativeQuery = true)
    List<Object[]> getDailyVolume();

    /** Count by risk category */
    @Query("SELECT b.riskCategory, COUNT(b) FROM BorrowerInput b " +
           "WHERE b.creditScore IS NOT NULL GROUP BY b.riskCategory")
    List<Object[]> getCountByRiskCategory();

    /** Recent assessments ordered by creation date */
    Page<BorrowerInput> findAllByOrderByCreatedAtDesc(Pageable pageable);
}
