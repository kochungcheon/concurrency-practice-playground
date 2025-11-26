package chung.concurrency.support;

import java.util.Collection;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * 동시성 테스트를 손쉽게 실행하고 결과를 검증할 수 있도록 돕는 유틸리티 클래스입니다.
 */
public final class ConcurrentTestExecutor {

    private static final int DEFAULT_THREAD_COUNT = 16;
    private static final int DEFAULT_TIMEOUT_SECONDS = 5;

    private ConcurrentTestExecutor() {
    }

    /**
     * 기본 스레드 수(16)와 타임아웃(5초)으로 동시성 테스트를 실행합니다.
     *
     * @see #runWithThreads(int, int, Runnable, long, TimeUnit)
     */
    public static Result run(int userCount, Runnable task) {
        // 사용자가 스레드 수를 지정하지 않았다면, userCount만큼은 확보해준다.
        int threadPoolSize = Math.max(userCount, DEFAULT_THREAD_COUNT);
        return runWithThreads(threadPoolSize, userCount, task);
    }

    /**
     * 기본 타임아웃(5초)으로 동시성 테스트를 실행합니다.
     *
     * @see #runWithThreads(int, int, Runnable, long, TimeUnit)
     */
    public static Result runWithThreads(int threadPoolSize, int userCount, Runnable task) {
        return runWithThreads(threadPoolSize, userCount, task, DEFAULT_TIMEOUT_SECONDS, TimeUnit.SECONDS);
    }

    /**
     * 지정된 수의 사용자가 특정 작업을 동시에 실행하는 테스트를 수행합니다.
     * <p>
     * 이 메서드는 모든 스레드가 준비될 때까지 기다린 후, 동시에 시작하여 경합 조건을 시뮬레이션합니다.
     *
     * @param threadPoolSize 동시성 테스트에 사용할 스레드 풀의 크기
     * @param userCount      작업을 실행할 사용자의 수 (스레드 수)
     * @param task           각 사용자가 실행할 작업
     * @param timeout        스레드 준비 및 전체 작업 완료를 기다릴 최대 시간
     * @param unit           타임아웃 시간 단위
     * @return 테스트 실행 결과를 담은 {@link Result} 객체.
     * <b>경고: 반환된 Result 객체는 반드시 {@link Result#assertNoAsyncError()} 등을 통해 검증해야 합니다.
     * 그렇지 않으면 비동기 스레드에서 발생한 예외가 무시될 수 있습니다.</b>
     */
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

    /**
     * 동시성 테스트의 실행 결과를 나타냅니다.
     * 비동기 스레드에서 발생한 예외 목록을 포함합니다.
     *
     * @param asyncErrors 비동기 스레드에서 발생한 예외 목록
     */
    public record Result(Collection<Throwable> asyncErrors) {

        /**
         * 비동기 스레드에서 예외가 발생하지 않았는지 검증합니다.
         * 예외가 하나라도 발생했다면, 모든 예외를 포함하는 {@link AssertionError}를 던집니다.
         */
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
