package com.example.payments.export.writer;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.batch.item.Chunk;

class LoggingItemWriterTest {

    @Test
    void testWrite() {
        LoggingItemWriter writer = new LoggingItemWriter();
        writer.write(new Chunk<>(List.of("item1", "item2")));
    }
}
