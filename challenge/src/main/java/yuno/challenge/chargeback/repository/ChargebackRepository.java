package yuno.challenge.chargeback.repository;


import yuno.challenge.chargeback.model.Chargeback;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface ChargebackRepository extends JpaRepository<Chargeback, Long> {

    Optional<Chargeback> findByChargebackId(String chargebackId);

    boolean existsByIdempotencyKey(String idempotencyKey);

    // -----------------------------------------------------------------------
    // Analytics queries
    // -----------------------------------------------------------------------

    /** Count chargebacks per processor in a date range */
    @Query("""
            SELECT c.processorName, COUNT(c) AS total, SUM(c.amount) AS totalAmount
            FROM Chargeback c
            WHERE c.chargebackDate BETWEEN :from AND :to
            GROUP BY c.processorName
            ORDER BY total DESC
            """)
    List<Object[]> countByProcessorInRange(@Param("from") LocalDate from, @Param("to") LocalDate to);

    /** Count and sum chargebacks grouped by month and processor */
    @Query(value = """
            SELECT DATE_TRUNC('month', chargeback_date) AS month,
                   processor_name,
                   COUNT(*) AS total,
                   SUM(amount) AS total_amount
            FROM chargebacks
            WHERE chargeback_date BETWEEN :from AND :to
            GROUP BY month, processor_name
            ORDER BY month, processor_name
            """, nativeQuery = true)
    List<Object[]> monthlyVolumeByProcessor(@Param("from") LocalDate from, @Param("to") LocalDate to);

    /** BIN clustering — top N BINs by chargeback count */
    @Query("""
            SELECT c.cardBin, c.issuerCountry, COUNT(c) AS total, SUM(c.amount) AS totalAmount
            FROM Chargeback c
            WHERE c.chargebackDate BETWEEN :from AND :to
            GROUP BY c.cardBin, c.issuerCountry
            ORDER BY total DESC
            """)
    List<Object[]> binClustering(@Param("from") LocalDate from, @Param("to") LocalDate to);

    /** Country clustering */
    @Query("""
            SELECT c.issuerCountry, COUNT(c) AS total, SUM(c.amount) AS totalAmount
            FROM Chargeback c
            WHERE c.chargebackDate BETWEEN :from AND :to
            GROUP BY c.issuerCountry
            ORDER BY total DESC
            """)
    List<Object[]> countryClustering(@Param("from") LocalDate from, @Param("to") LocalDate to);

    /** Reason code breakdown */
    @Query("""
            SELECT c.reasonCode, c.reasonCategory, COUNT(c) AS total, SUM(c.amount) AS totalAmount
            FROM Chargeback c
            WHERE c.chargebackDate BETWEEN :from AND :to
            GROUP BY c.reasonCode, c.reasonCategory
            ORDER BY total DESC
            """)
    List<Object[]> reasonCodeBreakdown(@Param("from") LocalDate from, @Param("to") LocalDate to);

    /** MCC breakdown */
    @Query("""
            SELECT c.mcc, COUNT(c) AS total, SUM(c.amount) AS totalAmount
            FROM Chargeback c
            WHERE c.mcc IS NOT NULL
              AND c.chargebackDate BETWEEN :from AND :to
            GROUP BY c.mcc
            ORDER BY total DESC
            """)
    List<Object[]> mccBreakdown(@Param("from") LocalDate from, @Param("to") LocalDate to);

    /** Total chargebacks filed in a given calendar month (for rate calculation) */
    @Query(value = """
            SELECT COUNT(*) FROM chargebacks
            WHERE DATE_TRUNC('month', chargeback_date) = DATE_TRUNC('month', CAST(:month AS DATE))
            """, nativeQuery = true)
    long countChargebacksInMonth(@Param("month") LocalDate month);

    /** Total transactions processed in a given calendar month (rate denominator is prior month's txns) */
    @Query(value = """
            SELECT COUNT(*) FROM transactions
            WHERE DATE_TRUNC('month', transaction_date) = DATE_TRUNC('month', CAST(:month AS DATE))
            """, nativeQuery = true)
    long countTransactionsInMonth(@Param("month") LocalDate month);

    /** Total chargeback count within a date range (denominator-agnostic). */
    @Query("SELECT COUNT(c) FROM Chargeback c WHERE c.chargebackDate BETWEEN :from AND :to")
    long countInRange(@Param("from") LocalDate from, @Param("to") LocalDate to);
}
