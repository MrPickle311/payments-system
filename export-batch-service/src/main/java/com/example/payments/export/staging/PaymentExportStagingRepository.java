package com.example.payments.export.staging;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import com.example.payments.export.staging.PaymentExportStaging.ExportStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PaymentExportStagingRepository extends JpaRepository<PaymentExportStaging, Long> {

    boolean existsByStatusAndCreatedAtGreaterThanEqual(ExportStatus status, LocalDateTime since);

    @Query("SELECT MIN(s.id) FROM PaymentExportStaging s WHERE s.status = 'PENDING' AND s.createdAt >= :since")
    Optional<Long> findMinPendingId(@Param("since") LocalDateTime since);

    @Query("SELECT MAX(s.id) FROM PaymentExportStaging s WHERE s.status = 'PENDING' AND s.createdAt >= :since")
    Optional<Long> findMaxPendingId(@Param("since") LocalDateTime since);

    @Modifying
    @Query("UPDATE PaymentExportStaging s SET s.status = 'EXPORTED', s.exportedAt = :now WHERE s.id IN :ids")
    int markAsExported(@Param("ids") List<Long> ids, @Param("now") LocalDateTime now);

    @Modifying
    @Query("""
            UPDATE PaymentExportStaging s
            SET s.status = CASE WHEN s.retryCount + 1 >= :maxRetries THEN 'NONHEALABLE' ELSE 'FAILED' END,
                s.retryCount = s.retryCount + 1,
                s.lastError = :error
            WHERE s.id IN :ids
            """)
    int markAsFailedOrNonhealable(
            @Param("ids") List<Long> ids, @Param("error") String error, @Param("maxRetries") int maxRetries);

    @Modifying
    @Query("""
            UPDATE PaymentExportStaging s
            SET s.status = 'PENDING'
            WHERE s.status = 'FAILED'
              AND s.retryCount < :maxRetries
              AND s.createdAt >= :since
            """)
    int resetFailedToPending(@Param("maxRetries") int maxRetries, @Param("since") LocalDateTime since);
}
