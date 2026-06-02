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

@Configuration
public class BatchSerializationConfig {

  @Autowired
  private ObjectMapper objectMapper;

  @Bean
  public ExecutionContextSerializer executionContextSerializer() {
    return new ExecutionContextSerializer() {
      @Override
      public Map<String, Object> deserialize(InputStream inputStream) throws IOException {
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
