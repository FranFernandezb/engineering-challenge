package yuno.challenge.transaction.repository;

import yuno.challenge.transaction.model.Transaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface TransactionRepository extends JpaRepository<Transaction, Long> {

    Optional<Transaction> findByTransactionId(String transactionId);

    // -----------------------------------------------------------------------
    // Counts used as denominators for chargeback-rate-by-dimension calculation.
    // -----------------------------------------------------------------------

    @Query("SELECT COUNT(t) FROM Transaction t WHERE t.transactionDate BETWEEN :from AND :to")
    long countInRange(@Param("from") LocalDate from, @Param("to") LocalDate to);

    @Query("""
            SELECT t.processorName, COUNT(t)
            FROM Transaction t
            WHERE t.transactionDate BETWEEN :from AND :to
            GROUP BY t.processorName
            """)
    List<Object[]> countByProcessor(@Param("from") LocalDate from, @Param("to") LocalDate to);

    @Query("""
            SELECT t.issuerCountry, COUNT(t)
            FROM Transaction t
            WHERE t.transactionDate BETWEEN :from AND :to
            GROUP BY t.issuerCountry
            """)
    List<Object[]> countByCountry(@Param("from") LocalDate from, @Param("to") LocalDate to);

    @Query("""
            SELECT t.cardBin, COUNT(t)
            FROM Transaction t
            WHERE t.transactionDate BETWEEN :from AND :to
            GROUP BY t.cardBin
            """)
    List<Object[]> countByBin(@Param("from") LocalDate from, @Param("to") LocalDate to);

    @Query("""
            SELECT t.mcc, COUNT(t)
            FROM Transaction t
            WHERE t.transactionDate BETWEEN :from AND :to
            GROUP BY t.mcc
            """)
    List<Object[]> countByMcc(@Param("from") LocalDate from, @Param("to") LocalDate to);
}

