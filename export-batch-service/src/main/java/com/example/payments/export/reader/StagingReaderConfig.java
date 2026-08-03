package com.example.payments.export.reader;

import static org.springframework.batch.item.database.Order.ASCENDING;

import com.example.payments.export.staging.PaymentExportStaging;
import com.example.payments.export.staging.StagingRowMapper;
import java.util.HashMap;
import java.util.Map;
import javax.sql.DataSource;
import lombok.RequiredArgsConstructor;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.item.database.JdbcPagingItemReader;
import org.springframework.batch.item.database.builder.JdbcPagingItemReaderBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration
@Profile("manager")
@RequiredArgsConstructor
public class StagingReaderConfig {

    private final DataSource dataSource;

    @Bean
    @StepScope
    public JdbcPagingItemReader<PaymentExportStaging> stagingItemReader(
            @Value("#{stepExecutionContext['minId']}") Long minId,
            @Value("#{stepExecutionContext['maxId']}") Long maxId,
            @Value("#{stepExecutionContext['lookbackSince']}") String lookbackSince,
            @Value("${export.batch-size:500}") int pageSize) {

        var queryProvider = new LockingPostgresPagingQueryProvider();
        queryProvider.setSelectClause("SELECT id, payment_id, gross_amount, net_amount, currency, event_timestamp");
        queryProvider.setFromClause("FROM payment_export_staging");
        queryProvider.setWhereClause(
                "WHERE status = 'PENDING' AND id BETWEEN :minId AND :maxId AND created_at >= :since::timestamp");
        queryProvider.setSortKeys(Map.of("id", ASCENDING));

        var parameterValues = new HashMap<String, Object>();
        parameterValues.put("minId", minId);
        parameterValues.put("maxId", maxId);
        parameterValues.put("since", lookbackSince);

        return new JdbcPagingItemReaderBuilder<PaymentExportStaging>()
                .name("stagingItemReader")
                .dataSource(dataSource)
                .queryProvider(queryProvider)
                .parameterValues(parameterValues)
                .pageSize(pageSize)
                .rowMapper(new StagingRowMapper())
                .build();
    }
}
