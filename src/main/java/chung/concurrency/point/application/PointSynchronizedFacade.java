package chung.concurrency.point.application;

import org.springframework.stereotype.Service;

import chung.concurrency.point.domain.Point;

/**
 * Java Native 전략: synchronized 키워드를 사용한 직렬화.
 * Facade 메서드 자체는 @Transactional이 아니며, 내부에서 PointService(Transactional)를 호출합니다.
 * 따라서 synchronized 블록이 끝날 때 이미 DB 커밋까지 완료되어 정합성을 보장할 수 있습니다.
 */
@Service
public class PointSynchronizedFacade {

    private final PointService pointService;

    public PointSynchronizedFacade(PointService pointService) {
        this.pointService = pointService;
    }

    public synchronized Point charge(Long pointId, long amount) {
        return pointService.charge(pointId, amount);
    }
}
