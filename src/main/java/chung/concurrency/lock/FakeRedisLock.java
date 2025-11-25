package chung.concurrency.lock;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

@Component
public class FakeRedisLock {

    private final Map<String, String> lockStore = new ConcurrentHashMap<>();

    public boolean tryLock(String key) {
        return lockStore.putIfAbsent(key, "LOCKED") == null;
    }

    public void unlock(String key) {
        lockStore.remove(key);
    }
}
