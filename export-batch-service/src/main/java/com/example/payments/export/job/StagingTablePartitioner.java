package com.example.payments.export.job;

import com.example.payments.export.config.ExportProperties;
import com.example.payments.export.staging.PaymentExportStagingRepository;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.batch.core.partition.Partitioner;
import org.springframework.batch.item.ExecutionContext;

@RequiredArgsConstructor
public class StagingTablePartitioner implements Partitioner {

    public static final String MIN_ID = "minId";
    public static final String MAX_ID = "maxId";
    public static final String LOOKBACK_SINCE = "lookbackSince";

    private final PaymentExportStagingRepository stagingRepository;
    private final ExportProperties exportProperties;
    private final Clock clock;

    @Override
    public Map<String, ExecutionContext> partition(int gridSize) {
        // Cover current month + N past months to catch reset FAILED→PENDING rows from old partitions
        LocalDateTime since = LocalDate.now(clock)
                .minusMonths(exportProperties.getLookbackMonths())
                .withDayOfMonth(1)
                .atStartOfDay();

        long minId = stagingRepository.findMinPendingId(since).orElse(0L);
        long maxId = stagingRepository.findMaxPendingId(since).orElse(0L);

        if (maxId < minId) {
            // No pending rows — return a single no-op partition
            ExecutionContext ctx = new ExecutionContext();
            ctx.putLong(MIN_ID, 0L);
            ctx.putLong(MAX_ID, -1L);
            ctx.put(LOOKBACK_SINCE, since.toString());
            return Map.of("partition0", ctx);
        }

        long rangeSize = Math.max(1, (maxId - minId + 1) / gridSize);
        Map<String, ExecutionContext> partitions = new HashMap<>();
        for (int i = 0; i < gridSize; i++) {
            long partMin = minId + (i * rangeSize);
            long partMax = (i == gridSize - 1) ? maxId : partMin + rangeSize - 1;
            ExecutionContext ctx = new ExecutionContext();
            ctx.putLong(MIN_ID, partMin);
            ctx.putLong(MAX_ID, partMax);
            ctx.put(LOOKBACK_SINCE, since.toString());
            partitions.put("partition" + i, ctx);
        }
        return partitions;
    }
}
