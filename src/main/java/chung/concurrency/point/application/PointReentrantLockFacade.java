package chung.concurrency.point.application;

import java.util.concurrent.locks.ReentrantLock;
import org.springframework.stereotype.Service;

import chung.concurrency.point.domain.Point;

/**
 * Java Explicit 전략: ReentrantLock 사용.
 * Facade는 트랜잭션 외부에서 락을 획득/해제하고, 실제 DB 작업은 PointService가 담당한다.
 */
@Service
public class PointReentrantLockFacade {

    private final PointService pointService;
    private final ReentrantLock lock = new ReentrantLock();

    public PointReentrantLockFacade(PointService pointService) {
        this.pointService = pointService;
    }

    public Point charge(Long pointId, long amount) {
        lock.lock();
        try {
            return pointService.charge(pointId, amount);
        } finally {
            lock.unlock();
        }
    }
}
