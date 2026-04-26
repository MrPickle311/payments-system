package com.example.payments.export.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Lightweight DTO sent from the Worker → Manager over the export-batch-replies Kafka topic.
 * Replaces the Java-serialized {@link org.springframework.batch.core.StepExecution} (which has
 * circular references that break Jackson) with a flat JSON contract.
 * <p>
 * The Manager uses the {@code stepExecutionId} to look up the full {@link org.springframework.batch.core.StepExecution}
 * from the database via {@link org.springframework.batch.core.explore.JobExplorer}, then applies
 * the status and counts from this DTO to produce the final, merged execution state.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class StepPartitionReplyDto {
    private Long stepExecutionId;
    private Long jobExecutionId;

    // Status fields
    private String batchStatus;       // e.g. "COMPLETED", "FAILED"
    private String exitCode;          // e.g. "COMPLETED", "FAILED"
    private String exitDescription;   // error message if any

    // Item counts
    private long readCount;
    private long writeCount;
    private long filterCount;
    private long readSkipCount;
    private long writeSkipCount;
    private long processSkipCount;
    private long rollbackCount;
    private long commitCount;
}
