package com.example.payments.export.staging;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.Optional;
import org.springframework.jdbc.core.RowMapper;

public class StagingRowMapper implements RowMapper<PaymentExportStaging> {

    @Override
    public PaymentExportStaging mapRow(ResultSet rs, int rowNum) throws SQLException {
        return PaymentExportStaging.builder()
                .id(rs.getLong("id"))
                .paymentId(rs.getLong("payment_id"))
                .grossAmount(rs.getBigDecimal("gross_amount"))
                .netAmount(rs.getBigDecimal("net_amount"))
                .currency(rs.getString("currency"))
                .eventTimestamp(toLocalDateTime(rs.getTimestamp("event_timestamp")))
                .status(PaymentExportStaging.ExportStatus.PENDING)
                .build();
    }

    private static LocalDateTime toLocalDateTime(Timestamp ts) {
        return Optional.ofNullable(ts).map(Timestamp::toLocalDateTime).orElse(null);
    }
}
