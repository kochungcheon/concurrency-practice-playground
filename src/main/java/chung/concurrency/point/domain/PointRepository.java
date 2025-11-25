package chung.concurrency.point.domain;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

public interface PointRepository extends JpaRepository<Point, Long> {
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query(
		"""
		select p
		from Point p
		where p.id = :pointId
		"""
	)
	Optional<Point> findByIdForUpdate(@Param("pointId") Long id);
}
