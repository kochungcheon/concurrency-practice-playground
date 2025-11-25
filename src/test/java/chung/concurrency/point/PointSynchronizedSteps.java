package chung.concurrency.point;

import static org.assertj.core.api.Assertions.assertThat;

import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import org.springframework.beans.factory.annotation.Autowired;
import chung.concurrency.point.application.PointSynchronizedFacade;
import chung.concurrency.point.domain.Point;
import chung.concurrency.point.domain.PointRepository;
import chung.concurrency.support.ConcurrentTestExecutor;

public class PointSynchronizedSteps {

    private static final long POINT_ID = 1L;

    @Autowired
    private PointRepository pointRepository;

    @Autowired
    private PointSynchronizedFacade pointSynchronizedFacade;

    private ConcurrentTestExecutor.Result executionResult;

    @Given("Synchronized 계좌가 초기 잔액 {long}원으로 존재한다")
    public void setupPoint(long balance) {
        pointRepository.deleteAll();
        pointRepository.save(new Point(POINT_ID, balance));
    }

    @When("{int}명이 동시에 {long}원을 synchronized 파사드로 충전한다")
    public void chargeConcurrently(int userCount, long amountPerUser) throws InterruptedException {
        executionResult = ConcurrentTestExecutor.run(userCount, () -> pointSynchronizedFacade.charge(POINT_ID, amountPerUser));
    }

    @Then("Synchronized 최종 잔액은 {long}원이 된다")
    public void verifyBalance(long expectedBalance) {
        executionResult.assertNoAsyncError();
        Point point = pointRepository.findById(POINT_ID).orElseThrow();
        assertThat(point.getBalance()).isEqualTo(expectedBalance);
    }
}
