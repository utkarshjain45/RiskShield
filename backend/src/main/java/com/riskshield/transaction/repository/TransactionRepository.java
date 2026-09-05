package com.riskshield.transaction.repository;

import com.riskshield.transaction.entity.Customer;
import com.riskshield.transaction.entity.Device;
import com.riskshield.transaction.entity.Transaction;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public interface TransactionRepository extends JpaRepository<Transaction, String> {

    @Query("SELECT t FROM Transaction t " +
           "LEFT JOIN FETCH t.merchant " +
           "LEFT JOIN FETCH t.customer " +
           "LEFT JOIN FETCH t.device " +
           "WHERE t.id = :id")
    Optional<Transaction> findByIdWithDetails(@Param("id") String id);

    Page<Transaction> findByMerchantIdOrderByCreatedAtDesc(String merchantId, Pageable pageable);

    long countByCustomerAndCreatedAtAfter(Customer customer, Instant timestamp);

    long countByDeviceAndCreatedAtAfter(Device device, Instant timestamp);

    long countByIpAddressAndCreatedAtAfter(String ipAddress, Instant timestamp);

    @Query("SELECT COUNT(DISTINCT t.customer.id) FROM Transaction t WHERE t.device = :device")
    long countDistinctCustomersByDevice(@Param("device") Device device);

    @Query("SELECT COUNT(DISTINCT t.customer.id) FROM Transaction t WHERE t.ipAddress = :ipAddress")
    long countDistinctCustomersByIpAddress(@Param("ipAddress") String ipAddress);

    @Query("SELECT AVG(t.amountInPaise) FROM Transaction t WHERE t.customer = :customer")
    Double calculateAvgAmountInPaiseByCustomer(@Param("customer") Customer customer);

    @Query("SELECT " +
           "COUNT(t), " +
           "COALESCE(SUM(CASE WHEN d.decision IN (com.riskshield.common.enums.RiskDecisionType.REVIEW, com.riskshield.common.enums.RiskDecisionType.BLOCK) THEN 1L ELSE 0L END), 0L), " +
           "COALESCE(SUM(CASE WHEN d.decision = com.riskshield.common.enums.RiskDecisionType.BLOCK THEN 1L ELSE 0L END), 0L), " +
           "COALESCE(SUM(CASE WHEN d.decision = com.riskshield.common.enums.RiskDecisionType.REVIEW THEN 1L ELSE 0L END), 0L), " +
           "COALESCE(AVG(t.amountInPaise), 0.0), " +
           "COALESCE(SUM(CASE WHEN d.decision IN (com.riskshield.common.enums.RiskDecisionType.REVIEW, com.riskshield.common.enums.RiskDecisionType.BLOCK) THEN t.amountInPaise ELSE 0L END), 0L) " +
           "FROM Transaction t " +
           "LEFT JOIN RiskDecision d ON d.transaction = t " +
           "WHERE t.merchant.id = :merchantId AND t.createdAt >= :windowStart AND t.createdAt <= :windowEnd")
    Object calculateMerchantWindowMetricsRaw(
            @Param("merchantId") String merchantId,
            @Param("windowStart") Instant windowStart,
            @Param("windowEnd") Instant windowEnd
    );

    @Query("SELECT t FROM Transaction t " +
           "LEFT JOIN FETCH t.merchant m " +
           "LEFT JOIN FETCH t.customer c " +
           "LEFT JOIN FETCH t.device d " +
           "WHERE (:search IS NULL OR :search = '' OR " +
           "       LOWER(t.id) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
           "       LOWER(c.id) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
           "       LOWER(m.id) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
           "       LOWER(t.ipAddress) LIKE LOWER(CONCAT('%', :search, '%'))) AND " +
           "(:paymentStatus IS NULL OR :paymentStatus = '' OR t.paymentStatus = :paymentStatus) AND " +
           "(:merchantId IS NULL OR :merchantId = '' OR m.id = :merchantId)")
    Page<Transaction> searchTransactions(
            @Param("search") String search,
            @Param("paymentStatus") String paymentStatus,
            @Param("merchantId") String merchantId,
            Pageable pageable
    );

    List<Transaction> findByCustomerOrderByCreatedAtDesc(Customer customer, Pageable pageable);
    List<Transaction> findByDeviceOrderByCreatedAtDesc(Device device, Pageable pageable);
    List<Transaction> findByIpAddressOrderByCreatedAtDesc(String ipAddress, Pageable pageable);

    long countByCustomer(Customer customer);
    long countByDevice(Device device);
    long countByIpAddress(String ipAddress);
}

