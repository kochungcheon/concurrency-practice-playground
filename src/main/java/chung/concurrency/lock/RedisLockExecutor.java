package chung.concurrency.lock;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import org.springframework.stereotype.Component;

/**
 * FakeRedisLock 기반 분산 락 실행기.
 * acquire → wait(backoff) → action → release 흐름을 캡슐화하고,
 * lease time(TTL) 만료 시 좀비 락을 강제로 제거한다.
 */
@Component
public class RedisLockExecutor {

    private static final long DEFAULT_LEASE_MILLIS = 1_000L;
    private static final long DEFAULT_WAIT_TIMEOUT_MILLIS = 10_000L;
    private static final long INITIAL_BACKOFF_MILLIS = 20L;
    private static final long MAX_BACKOFF_MILLIS = 200L;

    private final FakeRedisLock fakeRedisLock;
    private final Map<String, Lease> leases = new ConcurrentHashMap<>();

    public RedisLockExecutor(FakeRedisLock fakeRedisLock) {
        this.fakeRedisLock = fakeRedisLock;
    }

    public <T> T executeWithLock(String key, Supplier<T> criticalSection) {
        return executeWithLock(key, criticalSection, Duration.ofMillis(DEFAULT_LEASE_MILLIS),
            Duration.ofMillis(DEFAULT_WAIT_TIMEOUT_MILLIS));
    }

    public void executeWithLock(String key, Runnable criticalSection) {
        executeWithLock(key, () -> {
            criticalSection.run();
            return null;
        });
    }

    public <T> T executeWithLock(String key, Supplier<T> criticalSection,
        Duration leaseDuration, Duration waitTimeout) {
        String ownerToken = UUID.randomUUID().toString();
        acquireWithBackoff(key, ownerToken, leaseDuration, waitTimeout);
        try {
            return criticalSection.get();
        } finally {
            releaseSafely(key, ownerToken);
        }
    }

    private void acquireWithBackoff(String key, String ownerToken,
        Duration leaseDuration, Duration waitTimeout) {
        long deadlineNanos = System.nanoTime() + waitTimeout.toNanos();
        long backoffMillis = INITIAL_BACKOFF_MILLIS;
        while (System.nanoTime() < deadlineNanos) {
            if (tryAcquire(key, ownerToken, leaseDuration)) {
                return;
            }
            reclaimExpiredLease(key);
            sleep(backoffMillis);
            backoffMillis = Math.min(backoffMillis * 2, MAX_BACKOFF_MILLIS);
        }
        throw new IllegalStateException("lock acquisition timeout for key=" + key);
    }

    private boolean tryAcquire(String key, String ownerToken, Duration leaseDuration) {
        if (fakeRedisLock.tryLock(key)) {
            long expiry = System.nanoTime() + leaseDuration.toNanos();
            leases.put(key, new Lease(ownerToken, expiry));
            return true;
        }
        return false;
    }

    private void reclaimExpiredLease(String key) {
        Lease lease = leases.get(key);
        if (lease != null && lease.isExpired()) {
            if (leases.remove(key, lease)) {
                fakeRedisLock.unlock(key);
            }
        }
    }

    private void releaseSafely(String key, String ownerToken) {
        Lease lease = leases.get(key);
        if (lease != null && lease.isOwnedBy(ownerToken)) {
            leases.remove(key);
            fakeRedisLock.unlock(key);
        }
    }

    private void sleep(long millis) {
        try {
            TimeUnit.MILLISECONDS.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("lock wait interrupted", e);
        }
    }

    private record Lease(String ownerToken, long expiryNanoTime) {

        boolean isOwnedBy(String token) {
            return ownerToken.equals(token);
        }

        boolean isExpired() {
            return System.nanoTime() >= expiryNanoTime;
        }
    }
}
