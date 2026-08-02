package com.example.payments.export.reader;

import com.example.payments.export.staging.PaymentExportStaging;
import com.example.payments.export.staging.StagingRowMapper;
import javax.sql.DataSource;
import lombok.RequiredArgsConstructor;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.item.database.JdbcCursorItemReader;
import org.springframework.batch.item.database.builder.JdbcCursorItemReaderBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration
@Profile({"manager", "retry-manager"})
@RequiredArgsConstructor
public class StagingReaderConfig {

    private final DataSource dataSource;

    @Bean
    @StepScope
    public JdbcCursorItemReader<PaymentExportStaging> stagingItemReader(
            @Value("#{stepExecutionContext['minId']}") Long minId,
            @Value("#{stepExecutionContext['maxId']}") Long maxId,
            @Value("#{stepExecutionContext['lookbackSince']}") String lookbackSince) {

        return new JdbcCursorItemReaderBuilder<PaymentExportStaging>()
                .name("stagingItemReader")
                .dataSource(dataSource)
                .sql("""
                        SELECT id, payment_id, gross_amount, net_amount, currency, event_timestamp
                        FROM payment_export_staging
                        WHERE status = 'PENDING'
                          AND id BETWEEN ? AND ?
                          AND created_at >= ?::timestamp
                        ORDER BY id
                        FOR UPDATE SKIP LOCKED
                        """)
                .preparedStatementSetter(ps -> {
                    ps.setLong(1, minId);
                    ps.setLong(2, maxId);
                    ps.setString(3, lookbackSince);
                })
                .rowMapper(new StagingRowMapper())
                .build();
    }
}
