package com.example.payments.export.writer;

import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.item.Chunk;
import org.springframework.batch.item.ItemWriter;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class LoggingItemWriter implements ItemWriter<String> {

    @Override
    public void write(Chunk<? extends String> chunk) {
        log.info("[LoggingItemWriter] Received chunk of size: {}", chunk.size());
        chunk.forEach(item -> log.debug("[LoggingItemWriter] Item: {}", item));
        log.info("[LoggingItemWriter] Chunk processed successfully");
    }
}
