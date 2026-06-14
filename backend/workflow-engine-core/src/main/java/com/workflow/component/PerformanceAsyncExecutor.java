package com.workflow.component;

import com.workflow.dto.response.AsyncOperationResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * 性能管理组件 - 异步处理与批量操作协作类
 *
 * <p>承载异步操作的提交/查询/取消/清理，以及批量与并行批量执行，
 * 持有共享线程池与异步操作结果存储。逻辑由 {@link PerformanceManagerComponent}
 * 拆分而来，行为零变化。
 *
 * @author Workflow Engine
 * @version 1.0
 */
@Slf4j
@Component
public class PerformanceAsyncExecutor {

    // 异步操作执行器
    private final ExecutorService asyncExecutor = Executors.newFixedThreadPool(
            Runtime.getRuntime().availableProcessors() * 2,
            r -> {
                Thread t = new Thread(r, "async-workflow-");
                t.setDaemon(true);
                return t;
            }
    );

    // 异步操作结果存储
    private final ConcurrentHashMap<String, CompletableFuture<?>> asyncOperations = new ConcurrentHashMap<>();

    // ==================== 异步处理方法 ====================

    public <T> CompletableFuture<AsyncOperationResult<T>> executeAsync(String operationId, Supplier<T> operation) {
        log.info("开始异步操作: operationId={}", operationId);

        CompletableFuture<AsyncOperationResult<T>> future = CompletableFuture.supplyAsync(() -> {
            long startTime = System.currentTimeMillis();

            try {
                T result = operation.get();
                long executionTime = System.currentTimeMillis() - startTime;

                log.info("异步操作完成: operationId={}, executionTime={}ms", operationId, executionTime);
                return AsyncOperationResult.success(operationId, result, executionTime);

            } catch (Exception e) {
                log.error("异步操作失败: operationId={}, error={}", operationId, e.getMessage(), e);
                return AsyncOperationResult.failure(operationId, e.getMessage());
            }
        }, asyncExecutor);

        asyncOperations.put(operationId, future);
        return future;
    }

    public <T> CompletableFuture<AsyncOperationResult<T>> executeAsyncWithTimeout(
            String operationId, Supplier<T> operation, long timeoutMs) {

        log.info("开始带超时的异步操作: operationId={}, timeout={}ms", operationId, timeoutMs);

        CompletableFuture<AsyncOperationResult<T>> future = executeAsync(operationId, operation)
                .orTimeout(timeoutMs, TimeUnit.MILLISECONDS)
                .exceptionally(ex -> {
                    if (ex instanceof TimeoutException) {
                        log.warn("异步操作超时: operationId={}", operationId);
                        return AsyncOperationResult.failure(operationId, "操作超时");
                    }
                    return AsyncOperationResult.failure(operationId, ex.getMessage());
                });

        return future;
    }

    public AsyncOperationResult.OperationStatus getAsyncOperationStatus(String operationId) {
        CompletableFuture<?> future = asyncOperations.get(operationId);

        if (future == null) {
            return null;
        }

        if (future.isDone()) {
            if (future.isCompletedExceptionally()) {
                return AsyncOperationResult.OperationStatus.FAILED;
            }
            if (future.isCancelled()) {
                return AsyncOperationResult.OperationStatus.CANCELLED;
            }
            return AsyncOperationResult.OperationStatus.COMPLETED;
        }

        return AsyncOperationResult.OperationStatus.RUNNING;
    }

    public boolean cancelAsyncOperation(String operationId) {
        CompletableFuture<?> future = asyncOperations.get(operationId);

        if (future != null && !future.isDone()) {
            boolean cancelled = future.cancel(true);
            log.info("取消异步操作: operationId={}, result={}", operationId, cancelled);
            return cancelled;
        }

        return false;
    }

    public void cleanupCompletedAsyncOperations() {
        asyncOperations.entrySet().removeIf(entry -> entry.getValue().isDone());
        log.debug("清理已完成的异步操作");
    }

    // ==================== 批量操作优化方法 ====================

    public <T, R> List<R> executeBatch(List<T> items, int batchSize, Function<List<T>, List<R>> processor) {
        log.info("开始批量操作: totalItems={}, batchSize={}", items.size(), batchSize);

        List<R> results = new ArrayList<>();

        for (int i = 0; i < items.size(); i += batchSize) {
            int endIndex = Math.min(i + batchSize, items.size());
            List<T> batch = items.subList(i, endIndex);

            try {
                List<R> batchResults = processor.apply(batch);
                results.addAll(batchResults);
                log.debug("批次处理完成: batch={}/{}, processed={}",
                        (i / batchSize) + 1, (items.size() + batchSize - 1) / batchSize, batch.size());
            } catch (Exception e) {
                log.error("批次处理失败: batch={}, error={}", (i / batchSize) + 1, e.getMessage());
                throw e;
            }
        }

        log.info("批量操作完成: totalProcessed={}", results.size());
        return results;
    }

    public <T, R> List<R> executeParallelBatch(List<T> items, int batchSize,
                                                Function<List<T>, List<R>> processor) {
        log.info("开始并行批量操作: totalItems={}, batchSize={}", items.size(), batchSize);

        List<List<T>> batches = new ArrayList<>();
        for (int i = 0; i < items.size(); i += batchSize) {
            int endIndex = Math.min(i + batchSize, items.size());
            batches.add(items.subList(i, endIndex));
        }

        List<CompletableFuture<List<R>>> futures = batches.stream()
                .map(batch -> CompletableFuture.supplyAsync(() -> processor.apply(batch), asyncExecutor))
                .toList();

        List<R> results = futures.stream()
                .map(CompletableFuture::join)
                .flatMap(List::stream)
                .toList();

        log.info("并行批量操作完成: totalProcessed={}", results.size());
        return results;
    }

    // ==================== 资源关闭 ====================

    public void shutdown() {
        log.info("关闭性能管理组件");
        asyncExecutor.shutdown();
        try {
            if (!asyncExecutor.awaitTermination(30, TimeUnit.SECONDS)) {
                asyncExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            asyncExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}
