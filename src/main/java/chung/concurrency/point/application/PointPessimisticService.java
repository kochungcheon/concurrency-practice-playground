package chung.concurrency.point.application;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import chung.concurrency.point.domain.Point;
import chung.concurrency.point.domain.PointExceptionSupplier;
import chung.concurrency.point.domain.PointRepository;

@Service
public class PointPessimisticService {
	private final PointRepository pointRepository;

	public PointPessimisticService(PointRepository pointRepository) {
		this.pointRepository = pointRepository;
	}

	@Transactional
	public Point charge(Long pointId, long amount) {
		Point point = pointRepository.findByIdForUpdate(pointId)
			.orElseThrow(PointExceptionSupplier.notFound(pointId));

		long currentBalance = point.getBalance();

		try {
			Thread.sleep(200);
		} catch (InterruptedException ignored) {
		}

		point.setBalance(currentBalance + amount);
		return pointRepository.save(point);
	}
}
