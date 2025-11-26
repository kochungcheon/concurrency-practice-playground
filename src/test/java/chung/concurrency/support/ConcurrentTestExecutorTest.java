package chung.concurrency.support;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ConcurrentTestExecutorTest {

    @Test
    @DisplayName("작업이 주어진 횟수만큼 정확히 실행된다")
    void executesTaskCorrectNumberOfTimes() {
        // given
        int userCount = 10;
        AtomicInteger executionCount = new AtomicInteger(0);
        Runnable task = executionCount::incrementAndGet;

        // when
        ConcurrentTestExecutor.run(userCount, task);

        // then
        assertThat(executionCount.get()).isEqualTo(userCount);
    }

    @Test
    @DisplayName("작업 중 발생한 단일 예외를 수집한다")
    void collectsSingleExceptionFromTask() {
        // given
        int userCount = 1;
        RuntimeException exception = new RuntimeException("Test Exception");
        Runnable task = () -> {
            throw exception;
        };

        // when
        ConcurrentTestExecutor.Result result = ConcurrentTestExecutor.run(userCount, task);

        // then
        assertThat(result.asyncErrors()).containsExactly(exception);
    }

    @Test
    @DisplayName("작업 중 발생한 여러 예외를 모두 수집한다")
    void collectsMultipleExceptionsFromTasks() {
        // given
        int userCount = 8;
        AtomicInteger counter = new AtomicInteger(0);
        Runnable task = () -> {
            int count = counter.incrementAndGet();
            if (count % 2 == 0) { // 짝수 스레드에서만 예외 발생
                throw new RuntimeException("Error from thread " + count);
            }
        };

        // when
        ConcurrentTestExecutor.Result result = ConcurrentTestExecutor.run(userCount, task);

        // then
        assertThat(result.asyncErrors()).hasSize(userCount / 2);
        assertThat(result.asyncErrors()).allMatch(e -> e.getMessage().startsWith("Error from thread"));
    }

    @Test
    @DisplayName("스레드 풀이 사용자 수보다 작으면 IllegalArgumentException을 던진다")
    void throwsIllegalArgumentExceptionWhenThreadPoolIsTooSmall() {
        // given
        int threadPoolSize = 4;
        int userCount = 5;
        Runnable task = () -> {};

        // when & then
        assertThatThrownBy(() -> ConcurrentTestExecutor.runWithThreads(threadPoolSize, userCount, task))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("ThreadPoolSize(4) must be >= UserCount(5)");
    }

    @Test
    @DisplayName("assertNoAsyncError는 에러가 있을 때 AssertionError를 던진다")
    void assertNoAsyncErrorThrowsAssertionErrorWhenErrorsExist() {
        // given
        List<Throwable> errors = List.of(new RuntimeException("Error 1"), new RuntimeException("Error 2"));
        ConcurrentTestExecutor.Result result = new ConcurrentTestExecutor.Result(errors);

        // when & then
        assertThatThrownBy(result::assertNoAsyncError)
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("asynchronous error(s) occurred: 2")
                .hasSuppressedException(errors.get(0))
                .hasSuppressedException(errors.get(1));
    }

    @Test
    @DisplayName("작업이 제시간에 완료되지 않으면 IllegalStateException을 던진다")
    void throwsExceptionWhenTasksDoNotFinishInTime() {
        // given
        int userCount = 1;
        Runnable task = () -> {
            try {
                // Simulate a long-running task that exceeds the timeout
                Thread.sleep(6_000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        };

        // when & then
        assertThatThrownBy(() -> ConcurrentTestExecutor.run(userCount, task))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Timeout waiting for threads to finish");
    }
}
