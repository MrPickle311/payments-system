package com.example.payments.export.job;

import org.springframework.batch.core.partition.Partitioner;
import org.springframework.batch.item.ExecutionContext;

import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class OffsetRangePartitioner implements Partitioner {

  @Override
  public Map<String, ExecutionContext> partition(int gridSize) {
    return IntStream.range(0, gridSize).boxed()
        .collect(Collectors.toMap(i -> "partition" + i, i -> {
          ExecutionContext context = new ExecutionContext();
          context.putInt("partitionId", i);
          return context;
        }));
  }
}
