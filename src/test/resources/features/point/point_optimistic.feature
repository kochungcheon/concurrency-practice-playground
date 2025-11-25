Feature: Optimistic locking 서비스(지수 백오프 + 지터)를 이용한 포인트 충전 동시성 제어
  개발자로서
  나는 Optimistic locking + exponenital backoff + jitter 전략이 동시성 경쟁에서도 일관된 잔액을 보장하길 원한다
  그래야 트래픽 피크를 분산시키면서 포인트 정합성을 지킬 수 있다

  Scenario: 10개의 동시 충전 요청이 지수 백오프 + 지터 전략을 통해 결국 모두 성공한다 with Optimistic
    Given Optimistic 계좌가 초기 잔액 0원으로 존재한다
    And Optimistic 재시도 모니터링을 초기화한다
    When 10명이 동시에 100원을 Optimistic 서비스로 충전한다
    Then Optimistic 최종 잔액은 1000원이 된다
    And Optimistic 서비스는 최대 5회까지만 재시도한다

  Scenario: 기본 재시도 한도는 최대 5회를 넘기지 않는다
    Given Optimistic 계좌가 초기 잔액 0원으로 존재한다
    And Optimistic 재시도 모니터링을 초기화한다
    When 10명이 동시에 100원을 Optimistic 서비스로 충전한다
    Then Optimistic 서비스는 최대 5회까지만 재시도한다

  Scenario: 지수 백오프 + 지터 전략이 허용 횟수를 초과하면 혼잡 알림을 반환한다
    Given Optimistic 계좌가 초기 잔액 0원으로 존재한다
    And Optimistic 재시도 한도를 1회로 제한한다
    When 2명이 동시에 100원을 Optimistic 서비스로 충전한다
    Then Optimistic 전략은 혼잡 상태를 사용자에게 알린다
