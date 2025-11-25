package chung.concurrency.point.application;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ThreadLocalRandom;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import chung.concurrency.point.domain.Point;
import chung.concurrency.point.domain.PointExceptionSupplier;
import chung.concurrency.point.domain.PointRepository;

@Service
public class PointOptimisticService {

    private static final int DEFAULT_MAX_RETRY = 5;
    private static final long DEFAULT_INITIAL_BACKOFF_MILLIS = 50L;
    private static final long DEFAULT_MAX_BACKOFF_MILLIS = 800L;

    private final PointRepository pointRepository;
    private final List<RetryListener> retryListeners = new CopyOnWriteArrayList<>();

    public PointOptimisticService(PointRepository pointRepository) {
        this.pointRepository = pointRepository;
    }

    public Point charge(Long pointId, long amount) {
        return chargeWithRetryLimit(pointId, amount, DEFAULT_MAX_RETRY);
    }

    public Point chargeWithRetryLimit(Long pointId, long amount, int maxRetry) {
        long backoff = DEFAULT_INITIAL_BACKOFF_MILLIS;
        for (int attempt = 1; attempt <= maxRetry; attempt++) {
            notifyRetryListeners(attempt, maxRetry);
            try {
                return doCharge(pointId, amount);
            } catch (OptimisticLockingFailureException ex) {
                if (attempt == maxRetry) {
                    throw new PointConcurrencyBusyException(pointId);
                }
                sleepWithJitter(backoff);
                backoff = Math.min(backoff * 2, DEFAULT_MAX_BACKOFF_MILLIS);
            }
        }
        throw new IllegalStateException("unreachable");
    }

    @Transactional
    protected Point doCharge(Long pointId, long amount) {
        Point point = pointRepository.findById(pointId)
            .orElseThrow(PointExceptionSupplier.notFound(pointId));
        long currentBalance = point.getBalance();
        point.setBalance(currentBalance + amount);
        return pointRepository.save(point);
    }

    private void sleepWithJitter(long backoffMillis) {
        long jitter = ThreadLocalRandom.current().nextLong(backoffMillis + 1);
        try {
            Thread.sleep(jitter);
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }
    }

    public AutoCloseable registerRetryListener(RetryListener listener) {
        retryListeners.add(listener);
        return () -> retryListeners.remove(listener);
    }

    private void notifyRetryListeners(int attempt, int maxRetry) {
        for (RetryListener listener : retryListeners) {
            listener.onAttempt(attempt, maxRetry);
        }
    }

    @FunctionalInterface
    public interface RetryListener {
        void onAttempt(int attempt, int maxRetry);
    }
}
