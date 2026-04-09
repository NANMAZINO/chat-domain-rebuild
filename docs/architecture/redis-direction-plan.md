# Redis 활용 재정비 계획

M7 이후 Redis 방향은 `휴면 room summary cache + Pub/Sub`으로 정리한다. Redis를 DTO cache 전반에 넓게 적용하지 않고, 실시간 분산 이벤트 전파를 중심 역할로 둔다. room summary cache는 예외적으로 유지하되, 사용자별 상태가 없는 휴면 방에만 제한한다.

## 방향을 다시 정한 이유

채팅 도메인에서 자주 바뀌는 값은 마지막 메시지, 참여 인원, 읽음 상태, unread count다. room summary와 room list는 이 값을 묶어 만든 응답 DTO다. DTO 전체를 Redis에 두면 조회 비용은 줄 수 있어도, 쓰기 시점마다 캐시 무효화 범위가 넓어진다.

특히 room list 1페이지는 사용자별 응답이다. 새 메시지가 생기면 방의 ACTIVE 멤버 수만큼 eviction이 필요하고, 읽음 처리만으로도 사용자별 unread count가 달라진다. 이 구조에서는 cache hit보다 invalidation fan-out 비용이 먼저 커진다.

반면 Redis Pub/Sub은 다중 인스턴스 WebSocket 전파 문제를 직접 해결한다. 이후 마일스톤 흐름까지 고려하면, Redis의 중심 역할은 cache보다 Pub/Sub에 두는 편이 일관적이다.

## 판단 기준

이번 재정비에서는 아래 기준으로 Redis 적용 대상을 정한다.

- TTL이 본질인 데이터인가
- 잠시 유실돼도 다시 계산하거나 복구할 수 있는가
- DB의 진실값을 그대로 복제하지 않아도 되는가
- 쓰기보다 읽기가 훨씬 많아 cache hit 이득이 분명한가
- 이후 마일스톤에서 복잡도를 줄이는가

이 기준에 따라 room list DTO cache는 제외하고, Pub/Sub은 핵심으로 유지한다. room summary는 사용자별 상태가 없다는 점 때문에 제한적 cache 대상으로만 인정한다.

## 항목별 결정

### 1. room summary Redis cache

휴면 방에 한해 유지한다.

room summary는 `postTitle`, `memberCount`, `lastMessageId`, `lastMessagePreview`, `lastMessageAt`처럼 방 단위 상태만 담고, 사용자별 `unreadCount`를 포함하지 않는다. 읽음 처리와 직접 충돌하지 않으므로 room list보다 캐시 대상에 가깝다.

다만 전체 방에 일괄 적용하지는 않는다. 최근 30분 이상 조용한 방만 cache 대상으로 본다. `lastMessageAt`이 있으면 그 값을 기준으로 하고, `lastMessageAt`이 `null`이면 생성 시각을 기준으로 판단한다. TTL은 1시간으로 둔다. 정확성은 TTL이 아니라 기존 after-commit eviction으로 맞춘다.

이 cache는 핵심 성능 전략이 아니라 제한된 보조 최적화로 다룬다.

### 2. 사용자별 room list 1페이지 Redis cache

제거한다.

room list는 마지막 메시지, 참여 인원, 게시글 제목, `lastReadMessageId`, `unreadCount`를 함께 담는다. 이 중 `unreadCount`와 `lastReadMessageId`는 사용자별로 달라지고 읽음 처리에도 바뀐다. DTO cache를 유지할수록 무효화 규칙만 늘어난다.

room list 성능은 Redis cache보다 조회 구조와 데이터 모델에서 해결한다. 현재 잡아 둔 QueryDSL projection과 cursor 기반 조회 방향을 그대로 유지한다.

### 3. 최근 메시지 조회 cache

도입하지 않는다.

최신 페이지는 가장 자주 바뀌고, 과거 cursor 페이지는 반복 조회 이점이 크지 않다. 이 영역은 Redis보다 DB 인덱스와 cursor 조회, 필요하면 클라이언트 캐시로 처리한다.

### 4. Redis Pub/Sub

핵심 활용으로 유지한다.

한 인스턴스에서 메시지를 저장한 뒤 commit 이후 Pub/Sub 이벤트를 발행하고, 다른 인스턴스는 이를 받아 각자 연결된 세션으로 fan-out 한다. Redis는 이 지점에서 가장 명확한 역할을 가진다.

## 실행 계획

### 단계 1. M7 재정리

- room summary cache와 room list cache를 분리해 정리한다.
- room summary는 휴면 방 조건부 cache로 범위를 좁힌다.
- room list cache는 제거한다.
- M7 결과는 Redis cache 도입 자체보다, 어떤 cache를 남기고 어떤 cache를 빼는지에 대한 판단 근거로 정리한다.

### 단계 2. M8 집중

- 메시지 저장 이후 after-commit Pub/Sub 발행 구조를 구현한다.
- 각 인스턴스에서 subscribe 후 로컬 세션 fan-out 구조를 완성한다.
- Pub/Sub은 실시간 전파용이고, DB가 source of truth라는 점을 문서와 테스트에 반영한다.

### 단계 3. M9 마무리

- 읽음 처리 API를 구현한다.
- room list cache 가정 없이 `unreadCount`와 room list 일관성을 보장하는 회귀 테스트를 강화한다.
- 전체 테스트 스위트를 green 상태로 유지한다.

## 최종 정리

- room summary Redis cache: 휴면 방 조건부 유지
- room list 1페이지 Redis cache: 제거
- 최근 메시지 조회 Redis cache: 도입하지 않음
- Redis Pub/Sub: 핵심 유지
