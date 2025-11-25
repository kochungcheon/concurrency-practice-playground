package chung.concurrency.point.domain;

import java.util.function.Supplier;

@FunctionalInterface
public interface PointExceptionSupplier extends Supplier<RuntimeException> {

    static PointExceptionSupplier notFound(Long pointId) {
        return () -> new PointNotFoundException(pointId);
    }
}
