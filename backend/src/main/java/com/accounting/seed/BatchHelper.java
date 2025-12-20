package com.accounting.seed;

import java.util.List;
import java.util.function.Consumer;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public final class BatchHelper {

    private static final int DEFAULT_CHUNK_SIZE = 1000;

    private BatchHelper() {}

    /**
     * Process a list in chunks, logging progress.
     *
     * @param items List to process
     * @param chunkSize Number of items per chunk
     * @param entityName Name for logging (e.g., "vouchers")
     * @param processor Consumer that processes each chunk
     */
    public static <T> void processInChunks(
            List<T> items, int chunkSize, String entityName, Consumer<List<T>> processor) {
        if (items == null || items.isEmpty()) {
            log.info("No {} to process", entityName);
            return;
        }

        int totalItems = items.size();
        int processedCount = 0;

        for (int i = 0; i < totalItems; i += chunkSize) {
            int endIndex = Math.min(i + chunkSize, totalItems);
            List<T> chunk = items.subList(i, endIndex);

            processor.accept(chunk);

            processedCount += chunk.size();
            log.info("Inserted {} of {} {}...", processedCount, totalItems, entityName);
        }
    }

    public static <T> void processInChunks(
            List<T> items, String entityName, Consumer<List<T>> processor) {
        processInChunks(items, DEFAULT_CHUNK_SIZE, entityName, processor);
    }
}
