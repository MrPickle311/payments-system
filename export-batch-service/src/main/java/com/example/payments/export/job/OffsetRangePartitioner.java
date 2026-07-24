package com.example.payments.export.job;

import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.springframework.batch.core.partition.Partitioner;
import org.springframework.batch.item.ExecutionContext;

public class OffsetRangePartitioner implements Partitioner {

    public static final String PARTITION_ID = "partitionId";
    public static final String PARTITION_PREFIX = "partition";

    @Override
    public Map<String, ExecutionContext> partition(int gridSize) {
        return IntStream.range(0, gridSize).boxed().collect(Collectors.toMap(i -> PARTITION_PREFIX + i, i -> {
            ExecutionContext context = new ExecutionContext();
            context.putInt(PARTITION_ID, i);
            return context;
        }));
    }
}
