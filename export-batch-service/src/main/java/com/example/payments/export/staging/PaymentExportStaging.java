package com.example.payments.export.staging;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.*;

@Entity
@Table(name = "payment_export_staging")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentExportStaging {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long paymentId;
    private BigDecimal grossAmount;
    private BigDecimal netAmount;
    private String currency;
    private LocalDateTime eventTimestamp;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ExportStatus status;

    private Integer retryCount;
    private LocalDateTime createdAt;
    private LocalDateTime exportedAt;
    private String lastError;

    public enum ExportStatus {
        PENDING,
        EXPORTED,
        FAILED,
        NONHEALABLE
    }
}
