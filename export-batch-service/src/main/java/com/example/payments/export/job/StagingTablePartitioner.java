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
    public static final String SINGLE_NO_OP_PARTITION = "partition0";
    public static final String PARTITION_NAME_PREFIX = "partition";

    private final PaymentExportStagingRepository stagingRepository;
    private final ExportProperties exportProperties;
    private final Clock clock;

    @Override
    public Map<String, ExecutionContext> partition(int gridSize) {
        LocalDateTime since = calculateDateSinceNMonths();

        long minId = stagingRepository.findMinPendingId(since).orElse(0L);
        long maxId = stagingRepository.findMaxPendingId(since).orElse(0L);
        if (noRowsDetected(maxId, minId)) {
            var ctx = new ExecutionContext();
            ctx.putLong(MIN_ID, 0L);
            ctx.putLong(MAX_ID, -1L);
            ctx.put(LOOKBACK_SINCE, since.toString());
            return Map.of(SINGLE_NO_OP_PARTITION, ctx);
        }

        long partitionSize = Math.max(1, (maxId - minId + 1) / gridSize);
        Map<String, ExecutionContext> partitions = new HashMap<>();
        for (int i = 0; i < gridSize; i++) {
            long firstPartitionId = minId + (i * partitionSize);
            long partitionLastId = getPartitionLastIndex(gridSize, i, maxId, firstPartitionId, partitionSize);
            var ctx = new ExecutionContext();
            ctx.putLong(MIN_ID, firstPartitionId);
            ctx.putLong(MAX_ID, partitionLastId);
            ctx.put(LOOKBACK_SINCE, since.toString());
            partitions.put(PARTITION_NAME_PREFIX + i, ctx);
        }
        return partitions;
    }

    private static long getPartitionLastIndex(
            int gridSize, int i, long maxId, long firstPartitionId, long partitionSize) {
        if (i == gridSize - 1) {
            return maxId;
        }

        return firstPartitionId + partitionSize - 1;
    }

    private LocalDateTime calculateDateSinceNMonths() {
        return LocalDate.now(clock)
                .minusMonths(exportProperties.getLookbackMonths())
                .withDayOfMonth(1)
                .atStartOfDay();
    }

    private static boolean noRowsDetected(long maxId, long minId) {
        return maxId < minId;
    }
}
