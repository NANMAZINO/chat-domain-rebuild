# M8 Redis Pub/Sub Cross-Instance 검증 기록

## 개요

M8의 목표는 단일 인스턴스 직접 브로드캐스트를 `after-commit Redis Pub/Sub` 기반 다중 인스턴스 브로드캐스트로 바꾸는 것이다. 이 문서는 README 최종 정리 대신, M8 범위에서 실제로 무엇을 검증했는지 남기는 용도다.

## 검증 환경

- 날짜: `2026-04-08`
- 실행 환경: 로컬 Windows + Docker Desktop
- 데이터 저장소: Testcontainers `mysql:8.4`, `redis:7.4`
- 앱 인스턴스:
  - 인스턴스 A: `@SpringBootTest(webEnvironment = RANDOM_PORT)` 기본 테스트 컨텍스트
  - 인스턴스 B: 같은 MySQL/Redis를 바라보도록 추가로 띄운 Spring Boot 컨텍스트
- 고정 외부 계약:
  - WebSocket endpoint: `/ws-stomp`
  - SEND destination: `/pub/chat-rooms/{roomId}/messages`
  - SUBSCRIBE destination: `/sub/chat-rooms/{roomId}`

## 검증 항목

### 1. after-commit publish

`ChatPubSubServiceTest`에서 아래를 확인했다.

- publish 채널이 `chat:events`로 고정되는지
- 내부 이벤트 payload가 `eventType`, `roomId`, `messageId`, `senderId`, `content`, `type`, `createdAt`만 담는지
- 트랜잭션 rollback 시 Redis publish가 발생하지 않는지

### 2. 단일 인스턴스 기존 계약 유지

기존 websocket/security 테스트를 그대로 재사용해 아래를 확인했다.

- `ChatMessagingIntegrationTest`
  - 메시지 저장 후 같은 인스턴스 구독자가 기존 broadcast payload를 받는지
  - DB에는 메시지가 1건만 저장되는지
- `WebSocketSecurityIntegrationTest`
  - CONNECT / SUBSCRIBE / SEND 인증/권한 규칙이 그대로 유지되는지

### 3. cross-instance fan-out

`CrossInstancePubSubScenarioTest`에서 아래 두 시나리오를 검증했다.

- A 인스턴스 송신 -> B 인스턴스 구독자 수신
- B 인스턴스 송신 -> A 인스턴스 구독자 수신

두 시나리오 모두 `RawStompTestClient`로 실제 STOMP frame을 주고받고, 수신 측이 기존 외부 broadcast payload를 그대로 받는지 확인했다.

## frame excerpt

실제 검증 기준은 테스트 assertion이고, 아래 JSON은 그때 확인한 필드 구조를 옮긴 예시 payload다.

```json
{
  "messageId": 1,
  "roomId": 1,
  "sender": {
    "userId": 1,
    "nickname": "cross-author"
  },
  "content": "A to B",
  "type": "TEXT",
  "createdAt": "2026-04-08T17:55:00"
}
```

핵심은 외부 payload 형식이 바뀌지 않았고, sender nickname도 현재 `users.nickname` 기준으로 조립된다는 점이다.

## 실행 명령

M8 확인에 사용한 명령은 아래와 같다.

```powershell
./gradlew.bat test --tests "io.github.nanmazino.chatrebuild.chat.pubsub.ChatPubSubServiceTest" --tests "io.github.nanmazino.chatrebuild.chat.controller.ChatMessagingIntegrationTest" --tests "io.github.nanmazino.chatrebuild.chat.controller.WebSocketSecurityIntegrationTest" --tests "io.github.nanmazino.chatrebuild.scenario.CrossInstancePubSubScenarioTest"
./gradlew.bat test
```

## 정리

이번 단계에서 확인한 것은 다음 한 줄이다.

`메시지 저장 -> summary 반영 -> commit -> Redis publish -> 각 인스턴스 listener -> 로컬 WebSocket fan-out`

읽음 처리, `unreadCount`, README/ERD/API 최종 동기화는 이 문서 범위에 포함하지 않는다.
