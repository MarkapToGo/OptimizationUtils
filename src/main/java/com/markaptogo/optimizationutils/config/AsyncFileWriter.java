package com.markaptogo.optimizationutils.config;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Writes files on a single background thread, in the order they were queued, so the main thread never waits for
 * the disk. The content has to be complete when queued (e.g. a config saved to a string on the main thread).
 */
public final class AsyncFileWriter {

    private final ExecutorService executor;
    private final Logger logger;

    public AsyncFileWriter(String threadName, Logger logger) {
        this.logger = logger;
        this.executor = Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, threadName);
            thread.setDaemon(true);
            return thread;
        });
    }

    /**
     * Queues writing the content to the file. Writes it right away once this writer is closed.
     */
    public void write(Path file, String content) {
        if (executor.isShutdown()) {
            writeNow(file, content);
            return;
        }

        executor.execute(() -> writeNow(file, content));
    }

    /**
     * Waits until every queued write is done.
     */
    public void flush() {
        if (executor.isShutdown()) return;

        try {
            executor.submit(() -> {
            }).get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (ExecutionException e) {
            throw new IllegalStateException(e);
        }
    }

    /**
     * Waits for the queued writes and stops the background thread. Later writes happen right away.
     */
    public void close() {
        executor.shutdown();

        try {
            if (!executor.awaitTermination(10, TimeUnit.SECONDS)) {
                logger.warning("Timed out waiting for files to be written");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private void writeNow(Path file, String content) {
        try {
            Files.writeString(file, content, StandardCharsets.UTF_8);
        } catch (IOException e) {
            logger.log(Level.SEVERE, "Failed to write " + file, e);
        }
    }
}
