Feature: synchronized 파사드를 이용한 포인트 충전 동시성 제어
  개발자로서
  나는 synchronized 파사드가 동시에 들어오는 충전 요청을 직렬화해 처리하길 원한다
  그래야 높은 경쟁 상황에서도 포인트 잔액이 일관되게 유지된다

  Scenario: 10개의 동시 충전 요청이 손실 없이 모두 반영된다 with synchronized
    Given Synchronized 계좌가 초기 잔액 0원으로 존재한다
    When 10명이 동시에 100원을 synchronized 파사드로 충전한다
    Then Synchronized 최종 잔액은 1000원이 된다
