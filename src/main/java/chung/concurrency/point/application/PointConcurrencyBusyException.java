package chung.concurrency.point.application;

public class PointConcurrencyBusyException extends RuntimeException {

    public PointConcurrencyBusyException(Long pointId) {
        super("동시 요청이 많아 포인트를 처리할 수 없습니다. id=" + pointId);
    }
}
