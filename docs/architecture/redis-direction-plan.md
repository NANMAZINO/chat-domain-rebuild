# Redis 활용 재정비 계획

이 문서는 현재 M7의 Redis cache 방향을 다시 점검하고, 이후 M8, M9 마일스톤까지 이어지는 흐름 안에서 Redis를 어디에 쓰는 것이 이 프로젝트에 가장 자연스러운지 정리한 계획 문서다.

핵심 결론부터 말하면, 이 프로젝트에서 Redis의 주역은 DTO cache보다 Pub/Sub이다. 다만 예외는 있다. room summary는 사용자별 상태가 없는 room-level snapshot이라, 일정 시간 이상 조용한 방에 한해 조건부 cache를 적용하는 판단은 타당하다. 반면 room list 1페이지를 Redis에 그대로 복제하는 방식은 데이터 변경 빈도와 무효화 비용을 함께 보면 설명 비용이 크다. 이번 계획에서는 Redis 역할을 `휴면 room summary cache + Pub/Sub`으로 좁히고, 그 외 확장 주제는 범위 밖으로 둔다.

## 왜 방향을 다시 잡는가

지금 채팅 도메인에서 자주 바뀌는 값은 마지막 메시지, 참여 인원, 읽음 상태, unread count다. 이 값들은 모두 사용자가 실제로 상호작용할 때 바로 움직인다. room summary와 room list는 이 값들을 여러 개 묶어 응답 DTO로 만든 결과물이다. 따라서 DTO 전체를 Redis에 저장하면, 조회는 빨라질 수 있어도 쓰기 시점마다 캐시를 넓게 지워야 한다.

특히 room list 1페이지는 사용자별 응답이다. 새 메시지 하나만 생겨도 그 방의 ACTIVE 멤버 수만큼 cache eviction이 필요하다. 읽음 처리까지 들어오면 방에 새 메시지가 없어도 사용자별 unread count가 달라진다. 이 구조에서는 cache hit ratio보다 invalidation fan-out이 먼저 문제로 드러난다.

이 프로젝트는 이후 마일스톤에서 Redis Pub/Sub, 읽음 처리, 전체 회귀 테스트를 예정하고 있다. 그 흐름을 같이 놓고 보면, 지금 시점에 Redis를 DTO cache에 강하게 묶는 것보다 Pub/Sub 중심으로 역할을 좁히는 편이 더 일관적이다.

## 판단 기준

이번 재정비에서는 기술을 많이 쓰는 것보다, 어떤 데이터가 Redis에 맞는지 설명할 수 있는지를 우선 기준으로 둔다.

- TTL이 본질인 데이터인가
- 잠시 유실돼도 다시 계산하거나 복구할 수 있는가
- DB의 진실값을 그대로 복제하지 않아도 되는가
- 쓰기보다 읽기가 훨씬 많아 cache hit 이득이 분명한가
- 이후 마일스톤과 연결했을 때 복잡도가 줄어드는가

이 기준으로 보면 room list DTO cache는 적합성이 낮고, Pub/Sub은 적합성이 높다. room summary는 이 기준을 전부 만족하지는 않지만, 사용자별 상태가 없는 응답이라는 점 때문에 휴면 방에 한한 제한적 cache 대상으로는 설명 가능하다.

## 항목별 판단

### 1. room summary Redis cache

휴면 방에 한해 제한적으로 유지할 수 있다.

room summary 응답은 `postTitle`, `memberCount`, `lastMessageId`, `lastMessagePreview`, `lastMessageAt`처럼 방 단위 상태만 담는다. 사용자별 `unreadCount`가 없기 때문에 뒤 마일스톤의 읽음 처리와 직접 충돌하지 않는다. 이 점이 room list와 가장 큰 차이다.

따라서 room summary cache는 “모든 방에 일괄 적용”이 아니라, 최근 일정 시간 동안 새 메시지가 없는 휴면 방에만 적용하는 것이 타당하다. 예를 들어 `lastMessageAt`이 있으면 그 시각을 기준으로 30분 이상 지난 방만 cache 대상으로 보고, `lastMessageAt`이 `null`인 방은 생성 시각 기준으로 30분 이상 지난 경우에만 포함한다. TTL은 1시간 정도로 길게 둔다. 정확성은 TTL이 아니라 기존의 after-commit eviction으로 맞춘다. 즉, 메시지 전송, 참여/나가기, 게시글 제목 수정 시에는 지금처럼 cache를 비우고, 조용한 기간에는 반복 조회를 Redis가 흡수하게 한다.

이 cache는 핵심 성능 전략이 아니라 범위를 좁힌 보조 최적화로 설명해야 한다. Redis의 대표 역할을 summary cache에 두지는 않는다.

### 2. 사용자별 room list 1페이지 Redis cache

제거 대상으로 본다.

room list는 마지막 메시지, 참여 인원, 게시글 제목, lastReadMessageId, unreadCount를 한 번에 묶는다. 이 값들 중 unreadCount는 읽음 처리 시점에 사용자별로 달라진다. 즉, 조용한 방이라도 사용자 상태가 조용하지 않을 수 있다. 뒤 마일스톤까지 고려하면 Redis DTO cache로 유지할수록 무효화 규칙만 늘어난다.

room list 성능 문제는 cache보다 조회 구조와 데이터 모델에서 해결하는 편이 맞다. 이 프로젝트는 이미 QueryDSL projection과 cursor 기반 조회로 그 방향을 잡고 있으므로, 그 흐름을 더 밀어주는 쪽이 낫다.

### 3. 최근 메시지 조회 cache

도입하지 않는다.

최신 페이지는 가장 자주 바뀌므로 cache와 상성이 좋지 않다. 과거 cursor 페이지는 상대적으로 안정적이지만, 사람 채팅에서 서버가 같은 과거 페이지를 반복해서 많이 제공해야 하는 패턴은 흔하지 않다. 이 경우 Redis를 두는 것보다 클라이언트 캐시가 더 자연스럽다.

즉, 메시지 히스토리는 DB 인덱스와 cursor 조회를 유지하고, Redis cache 대상에서는 제외한다.

### 4. Redis Pub/Sub

핵심 활용으로 유지한다.

이 프로젝트에서 Redis가 가장 설득력 있게 쓰이는 지점은 다중 인스턴스 WebSocket 메시지 전파다. 한 인스턴스에서 메시지를 저장한 뒤 commit 이후에 Pub/Sub 이벤트를 발행하고, 다른 인스턴스들은 그 이벤트를 받아 자기 서버에 붙은 세션으로 fan-out 한다. 이 흐름은 단일 인스턴스 한계를 넘는 구조를 명확하게 보여 준다.

이 프로젝트의 Redis 서사는 M7 cache보다 M8 Pub/Sub에서 완성된다.

## 권장 방향

이 프로젝트의 Redis 역할은 아래처럼 재정의한다.

1. M7의 Redis DTO cache는 room summary에 한한 휴면 방 조건부 cache만 남기고, room list cache는 제거하는 방향으로 정리한다.
2. M8에서는 Redis Pub/Sub를 핵심 목표로 삼는다.
3. M9에서는 읽음 처리, unread 반영, 회귀 테스트, 문서화를 마무리한다.

즉, Redis를 “빠른 조회를 위한 만능 cache”로 설명하지 않고, “실시간 분산 이벤트에 강한 도구”로 설명하는 방향으로 간다. room summary cache는 이 큰 방향 안에서 허용되는 좁은 예외로 본다.

## 실행 계획

### 단계 1. M7 재정리

- room summary cache와 room list cache의 유지 비용을 분리해 문서화한다.
- room summary는 휴면 방 조건부 cache로 범위를 좁히고, room list cache는 제거 대상으로 정리한다.
- room summary의 휴면 기준은 `30분`, TTL은 `1시간`으로 고정한다.
- 휴면 판단은 `lastMessageAt` 우선, `lastMessageAt == null`이면 생성 시각 기준으로 적용한다.
- M7의 성과는 “Redis cache 도입”보다 “어떤 cache는 남기고 어떤 cache는 빼는지 판단한 근거”로 정리한다.

### 단계 2. M8 집중

- 메시지 저장 이후 after-commit Pub/Sub 발행 구조를 구현한다.
- 각 인스턴스에서 subscribe 후 로컬 세션 fan-out 구조를 완성한다.
- Pub/Sub은 실시간 전파용이며, DB가 source of truth라는 점을 문서와 테스트에 함께 반영한다.
- M8의 선행조건에서 cache hit ratio 같은 제거 대상 과제는 떼어내고, Pub/Sub 자체가 바로 다음 핵심 단계가 되도록 정리한다.

### 단계 3. M9 필수 범위 완료

- 읽음 처리 API를 구현하고 unreadCount 반영 규칙을 안정화한다.
- room list cache를 전제로 한 invalidation 요구는 제거하고, cache 가정 없이 room list와 unreadCount가 일관되게 동작하도록 회귀 테스트를 강화한다.
- 전체 테스트 스위트를 green 상태로 고정한다.

## 문서와 설명 방향

README와 포트폴리오 설명은 아래 메시지를 중심으로 정리한다.

- 이 프로젝트는 Redis를 무조건 cache에 쓰지 않았다.
- chat DTO cache는 invalidation 비용이 커서 유지 가치가 낮다고 판단했다.
- Redis는 Pub/Sub 기반 실시간 분산 전파에 가장 큰 가치를 제공한다.
- room summary cache는 사용자별 상태가 없는 휴면 방에 한해 제한적으로 유지할 수 있다.

이 설명은 “Redis를 붙였다”보다 한 단계 높은 판단을 보여 준다. 어떤 기술을 썼는지만이 아니라, 어떤 자리에 쓰지 않기로 했는지까지 설명할 수 있어야 설계 판단이 또렷해진다.

## 결정 요약

- room summary Redis cache: 휴면 방 조건부 유지
- room list 1페이지 Redis cache: 제거
- 최근 메시지 조회 Redis cache: 도입하지 않음
- Redis Pub/Sub: 핵심 유지

결론적으로, 이 프로젝트의 Redis는 room summary의 제한적 cache를 제외하면 cache보다 event 쪽에서 더 큰 가치를 낸다. 이후 작업은 M7 재정리, M8 Pub/Sub, M9 읽음 처리와 회귀 테스트로 곧게 이어지도록 좁히는 것이 맞다.
