package chung.concurrency.support;

import java.util.Collection;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public final class ConcurrentTestExecutor {

    private static final int DEFAULT_THREAD_COUNT = 16;
    private static final int DEFAULT_TIMEOUT_SECONDS = 5;

    private ConcurrentTestExecutor() {
    }

    public static Result run(int userCount, Runnable task) {
        return runWithThreads(DEFAULT_THREAD_COUNT, userCount, task);
    }

    public static Result runWithThreads(int threadPoolSize, int userCount, Runnable task) {
        return runWithThreads(threadPoolSize, userCount, task, DEFAULT_TIMEOUT_SECONDS, TimeUnit.SECONDS);
    }

    public static Result runWithThreads(
        int threadPoolSize,
        int userCount,
        Runnable task,
        long timeout,
        TimeUnit unit
    ) {
        if (threadPoolSize < userCount) {
            throw new IllegalArgumentException(
                String.format("ThreadPoolSize(%d) must be >= UserCount(%d) to prevent deadlock.",
                    threadPoolSize, userCount)
            );
        }

        ExecutorService executorService = Executors.newFixedThreadPool(threadPoolSize);
        Queue<Throwable> asyncErrors = new ConcurrentLinkedQueue<>();

        CountDownLatch ready = new CountDownLatch(userCount);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(userCount);

        try {
            for (int i = 0; i < userCount; i++) {
                executorService.submit(() -> {
                    try {
                        ready.countDown();
                        start.await();
                        task.run();
                    } catch (InterruptedException e) {
                        // 인터럽트 발생 시 스레드 상태 복구하고 에러로 기록
                        Thread.currentThread().interrupt();
                        asyncErrors.add(e);
                    } catch (Throwable throwable) {
                        asyncErrors.add(throwable);
                    } finally {
                        done.countDown();
                    }
                });
            }

            // 1. 모든 스레드 준비 대기
            if (!ready.await(timeout, unit)) {
                throw new IllegalStateException("Timeout waiting for threads to get ready");
            }

            // 2. 동시 시작
            start.countDown();

            // 3. 모든 작업 완료 대기
            if (!done.await(timeout, unit)) {
                throw new IllegalStateException("Timeout waiting for threads to finish");
            }

            return new Result(asyncErrors);

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Test interrupted", e);
        } finally {
            shutdownExecutor(executorService);
        }
    }

    private static void shutdownExecutor(ExecutorService executorService) {
        executorService.shutdown();
        try {
            if (!executorService.awaitTermination(5, TimeUnit.SECONDS)) {
                executorService.shutdownNow();
            }
        } catch (InterruptedException e) {
            executorService.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    public record Result(Collection<Throwable> asyncErrors) {

        public void assertNoAsyncError() {
            if (!asyncErrors.isEmpty()) {
                AssertionError error = new AssertionError(
                    "asynchronous error(s) occurred: " + asyncErrors.size()
                );
                asyncErrors.forEach(error::addSuppressed);
                throw error;
            }
        }
    }
}