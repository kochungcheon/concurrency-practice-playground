package chung.concurrency.point.application;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import chung.concurrency.point.domain.Point;
import chung.concurrency.point.domain.PointRepository;

@Service
public class PointService {

    private final PointRepository pointRepository;

    public PointService(PointRepository pointRepository) {
        this.pointRepository = pointRepository;
    }

    @Transactional
    public Point charge(Long pointId, long amount) {
        Point point = pointRepository.findById(pointId).orElseThrow();

        long currentBalance = point.getBalance();

        try {
            Thread.sleep(200);
        } catch (InterruptedException ignored) {
        }

        point.setBalance(currentBalance + amount);
        return pointRepository.save(point);
    }
}
