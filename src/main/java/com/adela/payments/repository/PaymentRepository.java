package com.adela.payments.repository;

import com.adela.payments.entity.Payment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface PaymentRepository extends JpaRepository<Payment, Long>, JpaSpecificationExecutor<Payment> {

    Optional<Payment> findByReference(String reference);

    @Query("""
        SELECT p.method as method, p.status as status, COUNT(p) as cnt, COALESCE(SUM(p.amount), 0) as vol
        FROM Payment p
        WHERE (CAST(:from as timestamp) IS NULL OR p.createdDate >= :from)
        AND (CAST(:to as timestamp) IS NULL OR p.createdDate <= :to)
        GROUP BY p.method, p.status
        """)
    List<Object[]> getAnalyticsRaw(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);
}
