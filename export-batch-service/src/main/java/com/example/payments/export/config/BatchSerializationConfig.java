package com.example.payments.export.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.batch.core.repository.ExecutionContextSerializer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.Map;

/**
 * Registers a Jackson-based {@link ExecutionContextSerializer} so that Spring Batch 6 uses JSON
 * instead of JDK binary serialization for the {@code SERIALIZED_CONTEXT} column.
 *
 * <p>
 * In Spring Batch 5 this was done by extending {@code DefaultBatchConfiguration} and overriding
 * {@code getExecutionContextSerializer()}. That method was removed in Spring Batch 6 — the
 * serializer is now picked up by Spring Boot auto-configuration when a bean of type
 * {@link ExecutionContextSerializer} is present in the context.
 *
 * <h3>Database migration</h3>
 * <p>
 * Existing rows with binary-serialized context will fail to deserialize. Truncate the Spring Batch
 * metadata tables before first run:
 * 
 * <pre>
 *   TRUNCATE batch_step_execution_context CASCADE;
 *   TRUNCATE batch_job_execution_context  CASCADE;
 *   TRUNCATE batch_step_execution         CASCADE;
 *   TRUNCATE batch_job_execution_params   CASCADE;
 *   TRUNCATE batch_job_execution          CASCADE;
 *   TRUNCATE batch_job_instance           CASCADE;
 * </pre>
 */
@Configuration
public class BatchSerializationConfig {

  @Autowired
  private ObjectMapper objectMapper;

  @Bean
  public ExecutionContextSerializer executionContextSerializer() {
    return new ExecutionContextSerializer() {

      @Override
      public Map<String, Object> deserialize(InputStream inputStream) throws IOException {
        // noinspection unchecked
        return objectMapper.readValue(inputStream, HashMap.class);
      }

      @Override
      public void serialize(Map<String, Object> object, OutputStream outputStream)
          throws IOException {
        objectMapper.writeValue(outputStream, object);
      }
    };
  }
}
