package chung.concurrency.point.domain;

public class PointNotFoundException extends RuntimeException {

    public PointNotFoundException(Long pointId) {
        super("Point not found. id=" + pointId);
    }
}
