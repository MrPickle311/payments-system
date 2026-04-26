package com.example.payments.export.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.batch.core.configuration.support.DefaultBatchConfiguration;
import org.springframework.batch.core.repository.ExecutionContextSerializer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.Map;

/**
 * Overrides Spring Batch 5's infrastructure to use Jackson JSON for serializing
 * {@code ExecutionContext} into the database (BATCH_STEP_EXECUTION_CONTEXT,
 * BATCH_JOB_EXECUTION_CONTEXT tables) instead of the default Java binary serialization.
 *
 * <h3>Why this class is necessary</h3>
 * <p>Spring Batch stores partition context (partitionId, minValue, maxValue, etc.)
 * in the {@code SERIALIZED_CONTEXT} column as a serialized blob. The default
 * {@link org.springframework.batch.core.repository.dao.DefaultExecutionContextSerializer}
 * uses JDK object serialization, which produces unreadable Base64 binary in the database.
 *
 * <p>By extending {@link DefaultBatchConfiguration} and overriding
 * {@code getExecutionContextSerializer()}, we replace the serializer for both
 * {@code JobRepository} and {@code JobExplorer} in one place. Spring Boot 3's
 * {@code BatchAutoConfiguration} detects this class and backs off completely,
 * so there is no double-configuration.
 *
 * <h3>Database migration</h3>
 * <p>Existing rows with binary-serialized context will fail to deserialize with the new
 * Jackson serializer. Truncate the Spring Batch metadata tables before first run:
 * <pre>
 *   TRUNCATE batch_step_execution_context CASCADE;
 *   TRUNCATE batch_job_execution_context  CASCADE;
 *   TRUNCATE batch_step_execution         CASCADE;
 *   TRUNCATE batch_job_execution_params   CASCADE;
 *   TRUNCATE batch_job_execution          CASCADE;
 *   TRUNCATE batch_job_instance           CASCADE;
 * </pre>
 *
 * <p>After migration, the SERIALIZED_CONTEXT column will contain readable JSON:
 * <pre>
 *   {"partitionId":0,"minValue":0,"maxValue":1000}
 * </pre>
 */
@Configuration
public class BatchSerializationConfig extends DefaultBatchConfiguration {

    @Autowired
    private ObjectMapper objectMapper;

    /**
     * Supplies the Jackson-based serializer to both {@code JobRepository} and
     * {@code JobExplorer}. Called internally by {@link DefaultBatchConfiguration}
     * when building those beans.
     */
    @Override
    protected ExecutionContextSerializer getExecutionContextSerializer() {
        return new ExecutionContextSerializer() {

            @Override
            public Map<String, Object> deserialize(InputStream inputStream) throws IOException {
                //noinspection unchecked
                return objectMapper.readValue(inputStream, HashMap.class);
            }

            @Override
            public void serialize(Map<String, Object> object, OutputStream outputStream) throws IOException {
                objectMapper.writeValue(outputStream, object);
            }
        };
    }
}
