package com.markaptogo.optimizationutils.config;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AsyncFileWriterTest {

    private final AsyncFileWriter writer = new AsyncFileWriter("test-writer", Logger.getLogger("test"));

    @TempDir
    Path dir;

    @AfterEach
    void close() {
        writer.close();
    }

    @Test
    void writesInTheOrderTheyWereQueued() throws IOException {
        Path file = dir.resolve("data.yml");
        for (int i = 0; i < 100; i++) {
            writer.write(file, "value: " + i);
        }

        writer.flush();

        assertEquals("value: 99", Files.readString(file));
    }

    @Test
    void closeWaitsForQueuedWrites() throws IOException {
        Path file = dir.resolve("data.yml");
        writer.write(file, "queued");

        writer.close();

        assertEquals("queued", Files.readString(file));
    }

    @Test
    void writesRightAwayOnceClosed() throws IOException {
        Path file = dir.resolve("data.yml");
        writer.close();

        writer.write(file, "after close");

        assertEquals("after close", Files.readString(file));
    }
}
