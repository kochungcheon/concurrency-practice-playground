package chung.concurrency.support;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

public final class ConcurrentTestExecutor {

    private static final int DEFAULT_THREAD_COUNT = 16;
    private static final int DEFAULT_TIMEOUT_SECONDS = 5;

    private ConcurrentTestExecutor() {
    }

    public static Result run(int userCount, Runnable task) {
        return runWithThreads(DEFAULT_THREAD_COUNT, userCount, task);
    }

    public static Result runWithThreads(int threadPoolSize, int userCount, Runnable task) {
        ExecutorService executorService = Executors.newFixedThreadPool(threadPoolSize);
        AtomicReference<Throwable> asyncError = new AtomicReference<>();
        try {
            CountDownLatch ready = new CountDownLatch(userCount);
            CountDownLatch start = new CountDownLatch(1);
            CountDownLatch done = new CountDownLatch(userCount);

            for (int i = 0; i < userCount; i++) {
                executorService.submit(() -> {
                    try {
                        ready.countDown();
                        start.await();
                        task.run();
                    } catch (Throwable throwable) {
                        asyncError.compareAndSet(null, throwable);
                    } finally {
                        done.countDown();
                    }
                });
            }

            if (!ready.await(DEFAULT_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                throw new IllegalStateException("threads did not get ready in time");
            }
            start.countDown();
            if (!done.await(DEFAULT_TIMEOUT_SECONDS * 2, TimeUnit.SECONDS)) {
                throw new IllegalStateException("threads did not finish in time");
            }
            return new Result(asyncError.get());
        } catch (InterruptedException e) {
			throw new RuntimeException(e);
		} finally {
            executorService.shutdownNow();
        }
    }

    public record Result(Throwable asyncError) {

        public void assertNoAsyncError() {
            if (asyncError != null) {
                throw new AssertionError("asynchronous error occurred", asyncError);
            }
        }
    }
}
