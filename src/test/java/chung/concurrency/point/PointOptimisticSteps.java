package chung.concurrency.point;

import static org.assertj.core.api.Assertions.assertThat;

import io.cucumber.java.After;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.beans.factory.annotation.Autowired;

import chung.concurrency.point.application.PointOptimisticService;
import chung.concurrency.point.application.PointConcurrencyBusyException;
import chung.concurrency.point.domain.Point;
import chung.concurrency.point.domain.PointRepository;
import chung.concurrency.support.ConcurrentTestExecutor;

/**
 * Optimistic Lock 전략을 BDD 형식으로 검증한다.
 * 재시도 + 지수 백오프 + 지터 전략이 실제로 동시성 환경에서도 최종 잔액을 보존하는지 확인한다.
 */
public class PointOptimisticSteps {

    private static final long POINT_ID = 4L;

    @Autowired
    private PointRepository pointRepository;

    @Autowired
    private PointOptimisticService pointOptimisticService;

    private ConcurrentTestExecutor.Result executionResult;
    private Integer overrideMaxRetry;
    private final AtomicInteger maxObservedRetryAttempt = new AtomicInteger();
    private AutoCloseable retryListenerHandle;

    @Given("Optimistic 계좌가 초기 잔액 {long}원으로 존재한다")
    public void setupPoint(long balance) {
        pointRepository.deleteAll();
        pointRepository.save(new Point(POINT_ID, balance));
        executionResult = null;
        overrideMaxRetry = null;
        maxObservedRetryAttempt.set(0);
    }

    @When("{int}명이 동시에 {long}원을 Optimistic 서비스로 충전한다")
    public void chargeConcurrently(int userCount, long amountPerUser) throws InterruptedException {
        executionResult = ConcurrentTestExecutor.run(userCount,
            () -> {
                if (overrideMaxRetry != null) {
                    pointOptimisticService.chargeWithRetryLimit(POINT_ID, amountPerUser, overrideMaxRetry);
                } else {
                    pointOptimisticService.charge(POINT_ID, amountPerUser);
                }
            });
    }

    @When("Optimistic 재시도 한도를 {int}회로 제한한다")
    public void overrideRetryLimit(int maxRetry) {
        overrideMaxRetry = maxRetry;
    }

    @Given("Optimistic 재시도 모니터링을 초기화한다")
    public void initRetryMonitoring() {
        maxObservedRetryAttempt.set(0);
        retryListenerHandle = pointOptimisticService.registerRetryListener((attempt, maxRetry) ->
            maxObservedRetryAttempt.accumulateAndGet(attempt, Math::max));
    }

    @Then("Optimistic 최종 잔액은 {long}원이 된다")
    public void verifyBalance(long expectedBalance) {
        executionResult.assertNoAsyncError();
        Point point = pointRepository.findById(POINT_ID).orElseThrow();
        assertThat(point.getBalance()).isEqualTo(expectedBalance);
    }

    @Then("Optimistic 서비스는 최대 {int}회까지만 재시도한다")
    public void verifyMaxRetry(int expectedMaxRetry) {
        assertThat(maxObservedRetryAttempt.get()).isLessThanOrEqualTo(expectedMaxRetry);
    }

    @Then("Optimistic 전략은 혼잡 상태를 사용자에게 알린다")
    public void verifyBusyFailure() {
        assertThat(executionResult)
            .isNotNull();
        assertThat(executionResult.asyncError())
            .isNotNull()
            .isInstanceOf(PointConcurrencyBusyException.class);
    }
}
