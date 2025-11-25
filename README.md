# Concurrency Practice Playground

포인트 충전과 같은 핵심 비즈니스 로직이 N명 이상의 동시 요청에서도 안전하게 동작하도록, 다섯 가지 락 전략을 한 프로젝트에서 비교·실험할 수 있게 만든 학습용 애플리케이션입니다. 단일 인스턴스 락부터 DB 비관/낙관 락, Fake Redis 기반 분산 락까지 모두 동일한 도메인(Point)을 대상으로 검증하며, 각 전략은 Cucumber BDD 시나리오로 재현됩니다.

## 주요 기능
- **다중 락 전략**: `synchronized`, `ReentrantLock`, `SELECT ... FOR UPDATE`, `@Version + Retry`, FakeRedisLock 기반 분산 락을 모두 제공.
- **Retry & Backoff**: 낙관적 락 실패 시 지수 백오프(50ms → 800ms)와 완전 지터 + 최대 5회 재시도를 적용, 혼잡 시 사용자에게 Busy 알림.
- **분산 락 실행기**: `RedisLockExecutor`가 UUID 토큰 + Lease TTL(기본 1초) + backoff(20→200ms)를 통해 Zombie Lock을 회수하고 대기 부하를 제어.
- **Cucumber 동시성 테스트**: `ExecutorService` + `CountDownLatch` 조합으로 10명 동시 충전 상황을 매 시나리오마다 재현.
- **문서화된 설계 근거**: `docs/tech-spec.md`에 Step 0~5 전체의 원인 분석, 대안 비교, 선택 근거를 정리.

## 기술 스택
- Java 17, Spring Boot 3.1.3
- Gradle 8.7 (Wrapper 포함)
- DB: MySQL 8.x (로컬 실행), H2(MODE=MySQL) for tests
- 테스트/BDD: JUnit 5, Cucumber, AssertJ

## 빠른 시작
```bash
# 도커로 Spring Boot + MySQL 실행
docker compose up --build

# 의존성 다운로드 후 빌드 & 테스트
./gradlew clean test

# 애플리케이션 실행 (기본 DB: mysql://localhost:3306/appdb)
./gradlew bootRun
```
> DB 접속 정보는 `src/main/resources/application.yml` 환경 변수(`SPRING_DATASOURCE_*`)로 재정의할 수 있습니다.

## 테스트 전략
- `./gradlew test`는 H2 메모리 DB와 `CucumberTest`를 사용해 모든 feature 파일을 실행합니다.
- `point_optimistic.feature`는 기본 재시도가 5회를 넘지 않음을 검증하고, 재시도 한도를 강제로 낮추면 `PointConcurrencyBusyException`이 발생한다는 것도 보여줍니다.
- `point_redis_lock.feature`는 FakeRedisLock + Lease TTL 조합이 10명 경쟁 상황에서도 타임아웃 없이 직렬화를 보장함을 확인합니다.

## 폴더 구조
```
src
├── main
│   ├── java/chung/concurrency
│   │   ├── ConcurrencyApplication.java
│   │   ├── lock/
│   │   │   ├── FakeRedisLock.java               # ConcurrentHashMap 기반 모의 Redis 분산락
│   │   │   └── RedisLockExecutor.java           # Lease + Backoff + TTL 회수
│   │   └── point/
│   │       ├── application/                     # 전략별 Facade/Service
│   │       └── domain/                          # Point 엔티티(@Version) 및 Repository
│   └── resources/application.yml
└── test
    ├── java/chung/concurrency                   # Cucumber 설정/Step 정의
    └── resources/features/point/*.feature       # 전략별 시나리오
```

## 전략별 요약
| 전략 | 주요 클래스 | 대기 방식 | 특징 |
| --- | --- | --- | --- |
| JVM `synchronized` | `PointSynchronizedFacade` | JVM monitor 큐 | 단일 인스턴스에서만 유효, 코드 간단 |
| `ReentrantLock` | `PointReentrantLockFacade` | 공정성 없는 lock queue | 명시적 lock/unlock, try/fair 옵션 확장 용이 |
| 비관적 락 | `PointPessimisticService` + `PointRepository.findByIdForUpdate` | DB 세션 대기 | DB 레벨에서 충돌 차단, Deadlock 주의 |
| 낙관적 락 + Retry | `PointOptimisticService` | backoff + jitter 재시도 | 충돌 빈도가 낮을 때 고성능, 실패 시 Busy 알림 |
| Fake Redis 분산 락 | `PointRedisLockFacade` + `RedisLockExecutor` | 20→200ms 백오프 + TTL | Zombie Lock 자동 회수, 다중 인스턴스 대응 |
