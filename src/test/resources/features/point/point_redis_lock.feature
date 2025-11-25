Feature: FakeRedisLock 기반 분산 락 파사드를 이용한 포인트 충전 동시성 제어
  운영자로서
  나는 Redis 분산 락이 여러 서버 인스턴스의 동시 충전 요청을 직렬화하길 원한다
  그래야 단일 서버를 넘는 병렬 환경에서도 포인트 잔액이 일관되게 유지된다

  Scenario: 10개의 동시 충전 요청이 Redis 분산 락을 통해 직렬화된다
    Given RedisLock 계좌가 초기 잔액 0원으로 존재한다
    When 10명이 동시에 100원을 Redis 락 파사드로 충전한다
    Then RedisLock 최종 잔액은 1000원이 된다
