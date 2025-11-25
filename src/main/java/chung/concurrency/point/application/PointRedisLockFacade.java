package chung.concurrency.point.application;

import org.springframework.stereotype.Service;

import chung.concurrency.lock.RedisLockExecutor;
import chung.concurrency.point.domain.Point;

/**
 * FakeRedisLock 기반 분산 락 파사드.
 * 트랜잭션은 PointService가 담당하고, 분산 락은 Facade에서 선행 취득한다.
 */
@Service
public class PointRedisLockFacade {

    private final PointService pointService;
    private final RedisLockExecutor redisLockExecutor;

    public PointRedisLockFacade(PointService pointService, RedisLockExecutor redisLockExecutor) {
        this.pointService = pointService;
        this.redisLockExecutor = redisLockExecutor;
    }

    public Point charge(Long pointId, long amount) {
        String lockKey = buildLockKey(pointId);
        return redisLockExecutor.executeWithLock(lockKey, () -> pointService.charge(pointId, amount));
    }

    private String buildLockKey(Long pointId) {
        return "point:" + pointId;
    }
}
