package com.example.payments.export.job;

import org.springframework.batch.core.partition.support.Partitioner;
import org.springframework.batch.item.ExecutionContext;

import java.util.HashMap;
import java.util.Map;

public class OffsetRangePartitioner implements Partitioner {

    @Override
    public Map<String, ExecutionContext> partition(int gridSize) {
        Map<String, ExecutionContext> partitions = new HashMap<>();

        // In a real scenario, we would query Kafka for the actual max offset.
        // For this Demo, we assume we want to process a batch of 10,000 items 
        // across 'gridSize' workers.
        
        int totalItems = 10000;
        int targetSize = (totalItems / gridSize) + 1;

        int start = 0;
        int end = targetSize;

        for (int i = 0; i < gridSize; i++) {
            ExecutionContext context = new ExecutionContext();
            context.putInt("minValue", start);
            context.putInt("maxValue", end);
            context.putInt("partitionId", i % 3); // Assuming 3 Kafka partitions for demo
            
            partitions.put("partition" + i, context);
            
            start += targetSize;
            end += targetSize;
        }

        return partitions;
    }
}
