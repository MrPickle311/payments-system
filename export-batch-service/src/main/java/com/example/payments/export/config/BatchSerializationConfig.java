package com.example.payments.export.config;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.Map;
import org.springframework.batch.core.repository.ExecutionContextSerializer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class BatchSerializationConfig {

    @Bean
    public ExecutionContextSerializer executionContextSerializer(final ObjectMapper objectMapper) {
        ObjectMapper batchMapper = objectMapper.copy()
                .enable(DeserializationFeature.USE_LONG_FOR_INTS);

        return new ExecutionContextSerializer() {
            @Override
            public Map<String, Object> deserialize(InputStream inputStream) throws IOException {
                return batchMapper.readValue(inputStream, HashMap.class);
            }

            @Override
            public void serialize(Map<String, Object> object, OutputStream outputStream) throws IOException {
                batchMapper.writeValue(outputStream, object);
            }
        };
    }
}
