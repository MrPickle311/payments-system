package com.example.payments.export.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Lightweight DTO sent from the Manager → Worker over the export-batch-requests Kafka topic.
 * Replaces the Java-serialized {@link org.springframework.batch.integration.partition.StepExecutionRequest}
 * with a simple JSON-serializable contract.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class StepPartitionRequestDto {
    private String stepName;
    private Long jobExecutionId;
    private Long stepExecutionId;
}
