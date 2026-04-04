# GitHub Issues 목록

## M0. 프로젝트 셋업

### Issue 00. 프로젝트 초기 세팅

```markdown
## 목표
- 프로젝트를 실행 가능하고, 로컬 개발/테스트/CI까지 갖춘 초기 상태로 만든다.

## 작업
- [ ] Spring Boot 3.x / Java 17 / Gradle 프로젝트 생성
- [ ] 기본 의존성 추가
  - [ ] Spring Web
  - [ ] Validation
  - [ ] Spring Data JPA
  - [ ] Spring Security
  - [ ] WebSocket
  - [ ] MySQL Driver
  - [ ] Spring Data Redis
  - [ ] Lombok
  - [ ] Spring Boot Test
  - [ ] Testcontainers (MySQL, Redis)
- [ ] 기본 패키지 구조 생성
  - [ ] global/config
  - [ ] global/entity
  - [ ] global/security
  - [ ] global/exception
  - [ ] global/response
  - [ ] global/util
  - [ ] user/controller
  - [ ] user/dto/request
  - [ ] user/dto/response
  - [ ] user/entity
  - [ ] user/exception
  - [ ] user/repository
  - [ ] user/service
  - [ ] post, chat 패키지는 이후 도메인 이슈에서 생성
- [ ] application-local.yml 작성
- [ ] application-test.yml 작성
- [ ] docker-compose로 MySQL, Redis 실행
- [ ] DB/Redis 접속 정보 분리
- [ ] JPA 실행 확인
- [ ] .github/workflows/ci.yml 작성
- [ ] JDK 17 설정
- [ ] gradlew 실행 권한 확인
- [ ] ./gradlew test 실행 설정
- [ ] MySQL/Redis Testcontainers 기반 통합 테스트 설정
- [ ] GitHub Actions에서 Testcontainers 기반 테스트 실행 확인

## 테스트
- [ ] contextLoads() 테스트 작성
- [ ] 애플리케이션 실행 확인
- [ ] local 프로필로 서버 실행 확인
- [ ] test 프로필로 테스트 실행 확인
- [ ] MySQL Testcontainer 연동 테스트 확인
- [ ] Redis Testcontainer 연동 테스트 확인
- [ ] PR 또는 push 시 GitHub Actions 실행 확인

## 완료 기준
- [ ] 서버가 정상 실행된다
- [ ] MySQL과 Redis에 애플리케이션이 연결된다
- [ ] test 프로필에서 테스트가 돌아간다
- [ ] local/CI에서 Testcontainers 기반 테스트가 돌아간다
- [ ] CI가 green이다
- [ ] 첫 커밋을 push했다

## 선행 이슈
- 없음
```

---

## M1. 회원가입 / 로그인 / JWT 인증

### Issue 01. User 엔티티 및 UserRepository 구현

```markdown
## 목표
- 사용자 저장 구조를 만든다.

## 작업
- [x] User 엔티티 생성
  - [x] id
  - [x] email
  - [x] password
  - [x] nickname
  - [x] createdAt / updatedAt
- [x] UserRepository 생성
- [x] email/nickname unique 제약 설정

## 테스트
- [x] UserRepository 저장/조회 테스트

## 완료 기준
- [x] 사용자를 DB에 저장하고 조회할 수 있다

## 선행 이슈
- [ ] Issue 00
```

### Issue 02. 회원가입 API 및 공통 응답/예외 처리 구현

```markdown
## 목표
- 회원가입 기능과 공통 응답/예외 처리 기반을 만든다.

## 작업
- [x] `Argon2id` 기반 PasswordEncoder 설정
- [x] Swagger(OpenAPI) 의존성 추가 및 기본 설정
- [x] Swagger UI 접속 확인
- [x] 공통 성공 응답 DTO 작성
- [x] 공통 에러 응답 DTO 작성
- [x] 공통 응답 포맷을 `success`, `data`, `error`, `timestamp` 구조로 고정
- [x] ErrorCode / CustomException 기본 구조 작성
- [x] HTTP status 규칙(200/201/400/401/403/404/409)과 공통 에러 코드 초안 정리
- [x] GlobalExceptionHandler 작성
- [x] validation 예외를 공통 에러 응답으로 변환
- [x] SignUpRequest DTO 작성
- [x] 회원가입 응답 DTO 작성
- [x] UserService 회원가입 로직 구현
- [x] 이메일/닉네임 중복 검사 추가
- [x] 중복 이메일/닉네임 예외를 공통 에러 응답으로 변환
- [x] UserController 회원가입 API 작성
- [x] 회원가입 경로를 `POST /api/users/signup`으로 고정
- [x] 회원가입 API를 Swagger에서 확인할 수 있게 문서 노출

## 테스트
- [x] Swagger UI에서 회원가입 API 스펙 확인
- [x] 회원가입 성공 테스트
- [x] email 형식 검증 테스트
- [x] password 정책 검증 테스트
- [x] validation 실패 시 공통 에러 응답 테스트
- [x] 중복 이메일 실패 테스트
- [x] 중복 닉네임 실패 테스트

## 완료 기준
- [x] 회원가입 API가 동작한다
- [x] 회원가입/validation/도메인 예외가 공통 응답 포맷으로 반환된다
- [x] Swagger UI에서 회원가입 API를 확인하고 테스트할 수 있다

## 선행 이슈
- [ ] Issue 01
```

### Issue 03. 로그인 API 및 JWT 발급 구현

```markdown
## 목표
- 로그인 후 JWT를 발급한다.

## 작업
- [x] LoginRequest / LoginResponse DTO 작성
- [x] 로그인 서비스 구현
- [x] JwtTokenProvider 구현
- [x] 로그인 경로를 `POST /api/auth/login`으로 고정
- [x] 로그인 성공 응답에 `accessToken`, `tokenType=Bearer`, `user(userId, email, nickname)` 포함
- [x] 로그인 실패를 공통 에러 응답과 `401 Unauthorized` 규칙에 맞추기
- [x] 로그인 API 구현

## 테스트
- [x] 로그인 성공 시 토큰 반환 테스트
- [x] 로그인 성공 응답 필드 계약(`accessToken`, `tokenType`, `user.userId`, `user.email`, `user.nickname`) 확인
- [x] 비밀번호 틀림 테스트
- [x] 로그인 실패 시 공통 에러 응답과 상태 코드 확인

## 완료 기준
- [x] 로그인 성공 시 JWT와 사용자 정보를 지정된 응답 계약으로 받을 수 있다

## 선행 이슈
- [x] Issue 02
```

### Issue 04. JWT 필터와 SecurityConfig 적용

```markdown
## 목표
- 보호 API에 인증을 적용한다.

## 작업
- [x] SecurityConfig 작성
- [x] JwtAuthenticationFilter 작성
- [x] JwtPrincipal 또는 UserDetails 구현
- [x] CustomUserDetailsService 구현
- [x] 인증이 필요한 URL 보호
- [x] 인증 없이 접근 가능한 URL 예외 처리
- [x] AuthenticationEntryPoint / AccessDeniedHandler 작성
- [x] Security 401/403 응답을 공통 에러 포맷으로 맞추기
- [x] 공개 API를 `/api/users/signup`, `/api/auth/login`, `GET /api/posts`, `GET /api/posts/{postId}`로 고정
- [x] `GET /api/posts`, `GET /api/posts/{postId}`는 permitAll로 설정
- [x] `PATCH /api/posts/{postId}/close`, `DELETE /api/posts/{postId}`는 인증 필요로 설정

## 테스트
- [x] 인증 없이 보호 API 접근 시 401/403 확인
- [x] 401/403 에러 바디가 공통 응답 포맷인지 확인
- [x] JWT 포함 시 접근 성공 확인
- [x] 회원가입/로그인 외 보호 API가 기본적으로 인증 필요인지 확인
- [x] 비회원 게시글 목록 조회 성공 테스트
- [x] 비회원 게시글 상세 조회 성공 테스트

## 완료 기준
- [x] 공개 API와 보호 API 경계가 문서 확정 정책과 일치한다
- [x] security 예외도 공통 에러 응답 규약과 일치한다

## 선행 이슈
- [x] Issue 03
```

---

## M2. 게시글 CRUD / 채팅방 자동 생성

### Issue 05. 게시글 생성/조회/수정 + 모집 종료/soft delete 구현

```markdown
## 목표
- 최소 게시글 기능을 만든다.

## 작업
- [x] Post 엔티티 작성
  - [x] title
  - [x] content
  - [x] maxParticipants
  - [x] status
  - [x] author
- [x] PostRepository 작성
- [x] PostService 작성
- [x] PostController 작성
  - [x] 생성
  - [x] 목록
  - [x] 상세
  - [x] 수정
  - [x] 모집 종료 `PATCH /api/posts/{postId}/close`
  - [x] 삭제 `DELETE /api/posts/{postId}` (soft delete)
- [x] 게시글 목록/상세는 비회원도 조회 가능하게 처리
- [x] 게시글 목록 query parameter를 `page`, `size`, `status`, `keyword`로 맞추기
- [x] `status` 미지정 시 기본적으로 `OPEN`, `CLOSED`만 조회
- [x] 게시글 삭제는 물리 삭제가 아니라 `status = DELETED`로 처리
- [x] 모집 종료는 `OPEN -> CLOSED` 상태 전이로 처리
- [x] `DELETED` 상태 게시글 상세 조회는 `POST_NOT_FOUND`로 처리
- [x] 게시글 수정/모집 종료/삭제는 작성자만 가능하게 처리
- [x] soft delete 시 연결된 `chatRoom`, `chatMessages`는 유지
- [x] `CLOSED`, `DELETED` 상태는 신규 참여만 막고 기존 `ACTIVE` 멤버의 채팅방 조회/메시지 송수신 권한은 유지

## 테스트
- [x] 게시글 생성 테스트
- [x] 게시글 목록/상세 조회 테스트
- [x] 비회원 게시글 목록/상세 조회 성공 테스트
- [x] 모집 종료 성공 테스트
- [x] 작성자 외 수정/모집 종료/삭제 차단 테스트
- [x] soft delete 후 목록/상세 미노출 테스트

## 완료 기준
- [x] 게시글 생성/조회/수정/모집 종료/soft delete가 문서 기준대로 동작한다

## 선행 이슈
- [x] Issue 04
```

### Issue 06. 게시글 생성 시 ChatRoom 자동 생성

```markdown
## 목표
- 게시글 1개 생성 시 채팅방 1개가 자동으로 생성되게 한다.

## 작업
- [x] ChatRoom 엔티티 작성
  - [x] postId
  - [x] memberCount
  - [x] lastMessageId
  - [x] lastMessagePreview
  - [x] lastMessageAt
- [x] ChatRoomRepository 작성
- [x] `posts` 1 : 1 `chat_rooms` 관계와 `postId` unique 제약 반영
- [x] `memberCount`, `lastMessage*`를 조회 최적화용 파생 컬럼으로 관리
- [x] 게시글 생성 시 ChatRoom 자동 생성 로직 추가

## 테스트
- [x] 게시글 생성 후 ChatRoom 생성 여부 확인

## 완료 기준
- [x] Post 1 : 1 ChatRoom 관계가 동작한다

## 선행 이슈
- [x] Issue 05
```

### Issue 07. 게시글 작성자 자동 채팅방 참여

```markdown
## 목표
- 게시글 작성자를 최초 채팅방 멤버로 등록한다.

## 작업
- [ ] ChatRoomMember 엔티티 작성
  - [ ] roomId
  - [ ] userId
  - [ ] status(ACTIVE, LEFT)
  - [ ] lastReadMessageId
  - [ ] lastReadAt
- [ ] ChatRoomMemberRepository 작성
- [ ] `(roomId, userId)` unique 제약 반영
- [ ] `joinedAt`, `leftAt` 컬럼 반영
- [ ] 게시글 생성 시 작성자 멤버 등록
- [ ] memberCount 증가

## 테스트
- [ ] 게시글 작성자 자동 참여 테스트
- [ ] memberCount = 1 확인

## 완료 기준
- [ ] 게시글 생성 후 작성자가 채팅방 멤버가 된다

## 선행 이슈
- [ ] Issue 06
```

---

## M3. 참여 / 나가기 / 멤버십 정책

### Issue 08. 게시글 참여 API 구현

```markdown
## 목표
- 게시글 참여 시 채팅방에도 참여하게 한다.

## 작업
- [ ] POST /api/posts/{postId}/join 구현
- [ ] 게시글 참여 = 채팅방 참여 로직 구현
- [ ] 중복 참여 방지 처리
- [ ] ACTIVE 멤버 중복 참여는 `CHAT_MEMBER_ALREADY_ACTIVE`로 처리
- [ ] `OPEN` 상태 게시글만 참여 가능하도록 처리
- [ ] `CLOSED` 상태는 `POST_ALREADY_CLOSED`, `DELETED` 상태는 `POST_NOT_FOUND`로 참여 차단

## 테스트
- [ ] 참여 성공 테스트
- [ ] 중복 참여 처리 테스트
- [ ] CLOSED 게시글 참여 실패 테스트
- [ ] DELETED 게시글 참여 실패 테스트

## 완료 기준
- [ ] 참여 API가 게시글 상태와 멤버십 정책을 함께 검증한다

## 선행 이슈
- [ ] Issue 07
```

### Issue 09. 나가기 및 LEFT -> ACTIVE 복구 정책 구현

```markdown
## 목표
- row 삭제 대신 상태 전환 정책을 구현한다.

## 작업
- [ ] POST /api/posts/{postId}/leave 구현
- [ ] leave 시 ACTIVE -> LEFT 처리
- [ ] 재참여 시 LEFT -> ACTIVE 복구
- [ ] joinedAt / leftAt 처리
- [ ] 비참여자 leave 요청 차단

## 테스트
- [ ] 나가기 테스트
- [ ] 재참여 복구 테스트
- [ ] 비참여자 leave 실패 테스트

## 완료 기준
- [ ] row 삭제 없이 상태 전환이 동작한다

## 선행 이슈
- [ ] Issue 08
```

### Issue 10. 정원 제한 및 membership 검증 구현

```markdown
## 목표
- 채팅방 인원 제한과 멤버십 검증을 구현한다.

## 작업
- [ ] maxParticipants 초과 참여 차단
- [ ] 비멤버 검증 메서드 작성
- [ ] ChatMembershipService 작성
- [ ] 참여/재참여/나가기 시 `member_count` 갱신을 같은 트랜잭션으로 묶기
- [ ] v1에서는 참여 시 정원 검증과 member_count 갱신의 정합성을 우선 보장하기
- [ ] v1에서는 `chat_rooms` 조회 시 비관적 락으로 경쟁 조건을 줄이는 정책을 적용

## 테스트
- [ ] 정원 초과 실패 테스트
- [ ] 비멤버 차단 테스트
- [ ] 참여/나가기 후 member_count 일관성 테스트
- [ ] 동시 참여 race 상황에서 정원 초과와 member_count 정합성 유지 테스트

## 완료 기준
- [ ] 정원 검증과 member_count 정합성이 함께 보장된다
- [ ] 동시 참여 상황에서도 중복 카운트나 초과 참여가 발생하지 않는다

## 선행 이슈
- [ ] Issue 09
```

---

## M4. 단일 인스턴스 채팅 Baseline

### Issue 11. WebSocket/STOMP 기본 연결 설정

```markdown
## 목표
- WebSocket/STOMP 연결 자체를 먼저 성공시킨다.

## 작업
- [ ] WebSocketConfig 작성
- [ ] /ws-stomp 엔드포인트 설정
- [ ] pub/sub prefix 설정
- [ ] destination 규칙을 `/sub/chat-rooms/{roomId}`, `/pub/chat-rooms/{roomId}/messages`로 고정
- [ ] ChatStompController 기본 구조 생성

## 테스트
- [ ] WebSocket 연결 성공 확인
- [ ] 특정 room 구독 성공 확인

## 완료 기준
- [ ] 클라이언트가 WebSocket에 연결되고 구독할 수 있다

## 선행 이슈
- [ ] Issue 10
```

### Issue 12. STOMP CONNECT 인증 및 SUBSCRIBE 권한 검증 구현

```markdown
## 목표
- WebSocket/STOMP에서도 JWT 인증을 적용한다.

## 작업
- [ ] StompJwtChannelInterceptor 작성
- [ ] CONNECT 헤더 Authorization 파싱
- [ ] JWT 검증 후 Principal 설정
- [ ] 인증 실패 시 차단
- [ ] SUBSCRIBE 시 room membership 즉시 검증
- [ ] LEFT 상태 또는 비멤버 구독 차단
- [ ] SEND도 `ACTIVE` 멤버만 가능하도록 권한 기준 정리
- [ ] 게시글 `CLOSED`, `DELETED` 여부와 무관하게 채팅 권한은 `ACTIVE` 멤버십 기준으로 판단
- [ ] 인증/멤버십 실패 시 즉시 차단하고 공통 에러 규칙과 맞추기

## 테스트
- [ ] 인증 없는 CONNECT 차단 테스트
- [ ] 잘못된 토큰 차단 테스트
- [ ] 비멤버 SUBSCRIBE 차단 테스트
- [ ] LEFT 멤버 SUBSCRIBE 차단 테스트

## 완료 기준
- [ ] CONNECT뿐 아니라 SUBSCRIBE도 멤버십 기준으로 보호된다

## 선행 이슈
- [ ] Issue 11
```

### Issue 13. ChatMessage 저장 및 단일 인스턴스 메시지 송신 구현

```markdown
## 목표
- 메시지를 저장하고 같은 인스턴스의 구독자에게 전달한다.

## 작업
- [ ] ChatMessage 엔티티 작성
- [ ] ChatMessageRepository 작성
- [ ] ChatSendRequest DTO 작성
- [ ] ChatMessageService 구현
- [ ] sender를 payload에서 받지 않고 principal.userId로 결정
- [ ] `senderId`, `nickname` 등 발신자 식별 필드는 요청 payload에서 받지 않기
- [ ] membership 검증 후 메시지 저장
- [ ] 메시지 타입을 v1에서 `TEXT`, `SYSTEM`으로 제한
- [ ] 응답/브로드캐스트 payload를 `messageId`, `sender`, `content`, `type`, `createdAt` 기준으로 정리
- [ ] 메시지 응답의 nickname은 현재 `users.nickname` 기준으로 조회
- [ ] 단일 인스턴스에서 메시지 브로드캐스트

## 테스트
- [ ] 메시지 저장 테스트
- [ ] 비멤버 SEND 차단 테스트
- [ ] payload sender 무시 테스트

## 완료 기준
- [ ] 메시지가 저장되고 송신된다

## 선행 이슈
- [ ] Issue 12
```

### Issue 14. Baseline 측정용 메시지 히스토리 조회(cursor 계약, 단순 구현)

```markdown
## 목표
- 개선 전 비교 기준이 되는 baseline 조회를 만들되 외부 API 계약은 처음부터 고정한다.

## 작업
- [ ] GET /api/chat-rooms/{roomId}/messages 구현
- [ ] `cursorMessageId`, `nextCursor`, `hasNext` 기준으로 응답 계약 고정
- [ ] 최근 메시지 순 정렬(`messageId desc`)
- [ ] ChatMessageResponse DTO 작성
- [ ] 구현체는 baseline 단계이므로 단순 JPA/JPQL로 먼저 구현
- [ ] 이 단계에서도 room `ACTIVE` 멤버만 히스토리 조회 가능하게 처리
- [ ] `CLOSED`, `DELETED` 게시글이어도 기존 `ACTIVE` 멤버는 히스토리 조회 가능하게 처리

## 테스트
- [ ] 메시지 히스토리 조회 테스트
- [ ] cursor 기준 다음 페이지 계산 테스트
- [ ] 응답 필드 계약(`cursorMessageId`, `nextCursor`, `hasNext`) 확인 테스트

## 완료 기준
- [ ] baseline 성능 측정이 가능한 단순 구현이 준비되고, 외부 API 계약은 이후에도 유지된다

## 선행 이슈
- [ ] Issue 13
```

### Issue 15. Baseline 내 채팅방 목록/summary 조회 구현(JPA, 최종 cursor 계약 유지)

```markdown
## 목표
- 개선 전 room list 및 room summary baseline을 만든다.

## 작업
- [ ] GET /api/chat-rooms 구현
- [ ] GET /api/chat-rooms/{roomId} 구현
- [ ] room list와 room summary는 게시글 상태와 무관하게 `ACTIVE` 멤버만 조회 가능하게 처리
- [ ] 정렬 기준을 `lastMessageAt desc, roomId desc`로 맞추기
- [ ] query parameter를 `cursorLastMessageAt`, `cursorRoomId`, `size`, `keyword`로 맞추기
- [ ] 응답 필드를 `nextCursorLastMessageAt`, `nextCursorRoomId`, `hasNext`로 맞추기
- [ ] room list 응답에 `lastReadMessageId`, `unreadCount` 필드를 최종 계약과 맞추기
- [ ] 읽음 이력이 없으면 `lastReadMessageId = null`로 응답하기
- [ ] `unreadCount`는 `lastReadMessageId = null`이면 전체 메시지 수, 값이 있으면 그 이후 메시지 수로 계산 규칙 고정
- [ ] 구현체는 baseline 단계이므로 단순 JPA/JPQL로 시작
- [ ] ChatRoomSummaryResponse DTO 작성
- [ ] ChatRoomDetailResponse DTO 작성

## 테스트
- [ ] 내 채팅방 목록 조회 테스트
- [ ] 채팅방 summary 조회 테스트
- [ ] 복합 cursor 기준 다음 페이지 계산 테스트
- [ ] room list 응답 필드 계약(`lastReadMessageId`, `unreadCount`) 확인 테스트
- [ ] 읽음 이력이 없는 멤버의 `unreadCount` 초기 계산 테스트

## 완료 기준
- [ ] room list API와 room summary API가 최종 응답 계약은 유지하면서 baseline 구현으로 동작한다

## 선행 이슈
- [ ] Issue 13
```

### Issue 16. Baseline 기능 회귀 테스트 정리

```markdown
## 목표
- baseline 단계 기능이 깨지지 않도록 테스트를 정리한다.

## 작업
- [ ] 회원/게시글/참여/채팅 흐름 통합 테스트 추가
- [ ] 최소 1개 end-to-end 시나리오 정리

## 테스트
- [ ] 전체 테스트 실행
- [ ] CI green 확인

## 완료 기준
- [ ] baseline 기능이 자동 검증된다

## 선행 이슈
- [ ] Issue 14
- [ ] Issue 15
```

---

## M5. Baseline 측정

### Issue 17. Baseline 측정용 데이터 및 측정 스크립트 준비

```markdown
## 목표
- 비교 가능한 측정 환경을 만든다.

## 작업
- [ ] 대량 메시지 테스트 데이터 준비
- [ ] 여러 채팅방 테스트 데이터 준비
- [ ] room list query count 확인 방법 준비
- [ ] 반복 호출 측정 스크립트 또는 테스트 작성
- [ ] 고정 데이터셋 규모, 워밍업 횟수, 본측정 반복 횟수, 실행 환경을 문서로 고정
- [ ] 허용 편차 기준을 정해 재측정 가능하게 만들기

## 테스트
- [ ] 같은 데이터셋으로 반복 실행 가능 확인
- [ ] 같은 실행 조건에서 측정값 편차가 허용 범위 안에 드는지 확인

## 완료 기준
- [ ] baseline 측정을 다시 돌릴 수 있다

## 선행 이슈
- [ ] Issue 16
```

### Issue 18. Baseline 수치 기록

```markdown
## 목표
- before 수치를 남긴다.

## 작업
- [ ] 메시지 히스토리 조회 p95 기록
- [ ] 채팅방 목록 조회 p95 기록
- [ ] room list query count 기록
- [ ] README 또는 docs에 baseline 값과 측정 조건 저장

## 테스트
- [ ] 같은 조건에서 2회 이상 측정 확인
- [ ] 측정 조건이 바뀌면 결과를 동일 비교군으로 취급하지 않도록 문서 검토

## 완료 기준
- [ ] before 수치가 문서에 남아 있다

## 선행 이슈
- [ ] Issue 17
```

---

## M6. 조회 성능 개선

### Issue 19. chat_rooms summary 정합성 보강 및 갱신 로직 정리

```markdown
## 목표
- room list 조회를 빠르게 만들기 위해 M2에서 도입한 summary 구조를 일관되게 활용한다.

## 작업
- [ ] M2에서 도입한 summary 컬럼(`memberCount`, `lastMessageId`, `lastMessagePreview`, `lastMessageAt`)을 파생 상태로 일관되게 관리
- [ ] 메시지 저장 시 summary 갱신
- [ ] join/leave 시 memberCount 갱신
- [ ] room list 정렬 기준을 위해 `last_message_at desc, room_id desc`에 맞는 조회 구조 준비

## 테스트
- [ ] 메시지 저장 후 summary 반영 테스트
- [ ] join/leave 후 memberCount 반영 테스트
- [ ] last_message_at이 같은 경우 roomId desc tie-breaker 정렬 테스트

## 완료 기준
- [ ] chat_rooms가 조회 최적화용 파생 상태를 가진다

## 선행 이슈
- [ ] Issue 18
```

### Issue 20. cursor pagination 및 인덱스 적용

```markdown
## 목표
- cursor 조회를 인덱스와 쿼리 최적화로 개선한다.

## 작업
- [ ] chat_messages (room_id, id desc) 인덱스 추가
- [ ] cursorMessageId 기반 조회 쿼리 최적화
- [ ] hasNext 계산 로직 추가
- [ ] 잘못된 cursor는 `CHAT_MESSAGE_INVALID_CURSOR`와 `400 Bad Request`로 처리

## 테스트
- [ ] cursor pagination 테스트
- [ ] 정렬 순서 테스트
- [ ] 다음 페이지 존재 여부 테스트

## 완료 기준
- [ ] 히스토리 조회가 cursor pagination으로 동작한다

## 선행 이슈
- [ ] Issue 19
```

### Issue 21. QueryDSL 기반 내 채팅방 목록 조회 구현

```markdown
## 목표
- room list 조회를 QueryDSL projection으로 최적화한다.

## 작업
- [ ] QueryDSL 설정 추가
- [ ] ChatRoomQueryRepository 작성
- [ ] ChatRoomQueryRepositoryImpl 작성
- [ ] 동적 조건 처리
  - [ ] 내 채팅방 목록
  - [ ] `lastMessageAt desc, roomId desc` 정렬
  - [ ] `cursorLastMessageAt + cursorRoomId` 복합 cursor
  - [ ] post title 검색
  - [ ] projection DTO 반환

## 테스트
- [ ] QueryDSL room list 조회 테스트
- [ ] 검색 조건 테스트
- [ ] projection 결과 테스트
- [ ] 복합 cursor 기준 room list 페이지네이션 테스트

## 완료 기준
- [ ] room list 조회가 복합 cursor + QueryDSL projection 기준으로 동작한다

## 선행 이슈
- [ ] Issue 19
```

### Issue 22. 개선 후 성능 재측정

```markdown
## 목표
- baseline 대비 개선 수치를 확보한다.

## 작업
- [ ] 메시지 히스토리 조회 p95 재측정
- [ ] 채팅방 목록 조회 p95 재측정
- [ ] room list query count 재측정
- [ ] before/after 비교 기록

## 테스트
- [ ] baseline과 같은 데이터셋으로 측정

## 완료 기준
- [ ] README에 개선 수치가 기록된다

## 선행 이슈
- [ ] Issue 20
- [ ] Issue 21
```

---

## M7. Redis Cache

### Issue 23. Redis room summary 캐시 적용

```markdown
## 목표
- room summary 조회에 Redis 캐시를 붙인다.

## 작업
- [ ] RedisConfig 작성
- [ ] ChatCacheRepository 작성
- [ ] room summary 캐시 저장/조회 구현
- [ ] 메시지 저장 후 summary 캐시 갱신 또는 삭제
- [ ] 참여/나가기 후 summary 캐시 갱신 또는 삭제
- [ ] room summary만 캐시 대상으로 유지하고 source of truth는 DB로 둔다

## 테스트
- [ ] cache miss -> DB 조회 테스트
- [ ] cache hit -> Redis 조회 테스트
- [ ] 참여/나가기 후 stale summary cache가 남지 않는지 테스트

## 완료 기준
- [ ] room summary에 캐시가 적용된다

## 선행 이슈
- [ ] Issue 15
- [ ] Issue 22
```

### Issue 24. 사용자별 room list 1페이지 캐시 적용

```markdown
## 목표
- 사용자별 첫 페이지 room list를 캐시한다.

## 작업
- [ ] room list 1페이지 캐시 key 설계
- [ ] 조회 시 캐시 적용
- [ ] 메시지 전송/참여/나가기 시 evict 처리
- [ ] 사용자별 room list 1페이지에만 캐시 적용
- [ ] 상황에 따라 evict 또는 refresh 정책을 선택할 수 있게 정리
- [ ] 읽음 처리 기반 evict는 Issue 28에서 이어서 반영

## 테스트
- [ ] cache hit 테스트
- [ ] cache evict 테스트
- [ ] stale cache 방지 테스트

## 완료 기준
- [ ] room list 1페이지 캐시가 동작한다

## 선행 이슈
- [ ] Issue 23
```

### Issue 25. 캐시 적용 후 query count / hit ratio 기록

```markdown
## 목표
- Redis 캐시의 효과를 수치로 남긴다.

## 작업
- [ ] room list query count 측정
- [ ] cache hit ratio 기록
- [ ] README에 캐시 적용 전/후 효과 정리

## 테스트
- [ ] 같은 요청 반복 시 캐시 효과 확인

## 완료 기준
- [ ] Redis를 왜 붙였는지 수치로 설명할 수 있다

## 선행 이슈
- [ ] Issue 24
```

---

## M8. Redis Pub/Sub / Scale-out

### Issue 26. Redis Pub/Sub after-commit 발행/구독 구조 구현

```markdown
## 목표
- 인스턴스 간 메시지 동기화 구조를 만든다.

## 작업
- [ ] ChatPubSubService 작성
- [ ] 메시지 저장 후 Pub/Sub publish
- [ ] subscribe 후 로컬 WebSocket 세션 fan-out
- [ ] DB 저장 이후에만 publish 되도록 정리
- [ ] Redis Pub/Sub 발행은 DB 저장 및 summary 갱신 트랜잭션 commit 이후에만 수행
- [ ] Pub/Sub 이벤트 payload를 `eventType`, `roomId`, `messageId`, `senderId`, `content`, `type`, `createdAt` 기준으로 정리

## 테스트
- [ ] publish payload 테스트
- [ ] subscribe 처리 테스트
- [ ] 트랜잭션 rollback 시 publish가 발생하지 않는지 테스트

## 완료 기준
- [ ] DB 반영 이전에 publish되지 않고, commit 이후에만 브로드캐스트 이벤트가 발행된다

## 선행 이슈
- [ ] Issue 25
```

### Issue 27. 2개 인스턴스 cross-instance 전달 시나리오 검증

```markdown
## 목표
- A 인스턴스에서 보낸 메시지를 B 인스턴스 사용자도 받는지 확인한다.

## 작업
- [ ] 2개 앱 인스턴스 실행 방법 정리
- [ ] Redis 연결 구성
- [ ] cross-instance 테스트 시나리오 작성
- [ ] 결과 로그/스크린샷 저장

## 테스트
- [ ] A 인스턴스 송신 -> B 인스턴스 수신 성공
- [ ] B 인스턴스 송신 -> A 인스턴스 수신 성공

## 완료 기준
- [ ] scale-out 전달 성공을 증명할 수 있다

## 선행 이슈
- [ ] Issue 26
```

---

## M9. 읽음 처리 / 회귀 테스트 / 문서화

### Issue 28. 읽음 처리 API 구현

```markdown
## 목표
- 마지막으로 읽은 메시지 정보를 저장한다.

## 작업
- [ ] PATCH /api/chat-rooms/{roomId}/read 구현
- [ ] lastReadMessageId 갱신
- [ ] lastReadAt 갱신
- [ ] M4에서 고정한 `unreadCount` 계산 규칙 유지
- [ ] room list `unreadCount` 계산 로직 반영
- [ ] room list `unreadCount`가 읽음 처리 이후 감소하도록 반영
- [ ] room list 캐시 evict 반영
- [ ] 더 작은 `lastReadMessageId`가 들어오면 no-op 처리
- [ ] 성공 응답은 최종 저장 상태를 반환

## 테스트
- [ ] 읽음 처리 성공 테스트
- [ ] 비멤버 읽음 처리 차단 테스트
- [ ] 읽음 처리 후 room list `unreadCount` 반영 테스트
- [ ] 이전보다 작은 메시지 ID 읽음 요청 no-op 테스트

## 완료 기준
- [ ] 읽음 처리가 단조 증가 방식으로 동작하고 unreadCount/캐시 무효화까지 반영된다

## 선행 이슈
- [ ] Issue 27
```

### Issue 29. 전체 회귀 테스트 및 보안 테스트 정리

```markdown
## 목표
- 핵심 기능이 끝까지 자동 검증되게 만든다.

## 작업
- [ ] 회원가입/로그인 회귀 테스트
- [ ] 게시글/참여/나가기 회귀 테스트
- [ ] 메시지 저장/조회 회귀 테스트
- [ ] 공통 응답/예외 코드 회귀 테스트
- [ ] 비회원 게시글 목록/상세 조회 허용 회귀 테스트
- [ ] 채팅방 summary 조회 회귀 테스트
- [ ] room list unreadCount 회귀 테스트
- [ ] STOMP SUBSCRIBE 멤버십 검증 회귀 테스트
- [ ] CLOSED/DELETED 게시글 참여 차단 회귀 테스트
- [ ] CLOSED/DELETED 게시글의 기존 `ACTIVE` 멤버 채팅 허용 회귀 테스트
- [ ] 보안 테스트
  - [ ] 인증 없는 REST 차단
  - [ ] 인증 없는 STOMP 차단
  - [ ] impersonation 차단
- [ ] cache/summary 반영 테스트

## 테스트
- [ ] 전체 테스트 스위트 실행
- [ ] MySQL/Redis Testcontainers 기반 통합 테스트 실행
- [ ] CI green 확인

## 완료 기준
- [ ] 핵심 시나리오가 자동 검증된다

## 선행 이슈
- [ ] Issue 28
```

### Issue 30. README / ERD / API / 성능 지표 문서화

```markdown
## 목표
- 프로젝트를 처음 보는 사람도 이해할 수 있도록 문서화한다.

## 작업
- [ ] README 작성
  - [ ] 문제 정의
  - [ ] 핵심 목표
  - [ ] 도메인 모델
  - [ ] 아키텍처
  - [ ] 메시지 흐름
  - [ ] 테스트 전략
  - [ ] 정량 지표
- [ ] ERD 정리
- [ ] API 명세 정리
- [ ] API 문서에 공통 응답/에러 포맷과 에러 코드 규칙 추가
- [ ] API 문서에 HTTP status 규칙 추가
- [ ] 게시글 종료와 삭제를 분리된 API로 정리
- [ ] 채팅방 summary 조회 API와 room list unreadCount 계약 반영
- [ ] room list 복합 cursor 규칙 반영
- [ ] 읽음 이력이 없을 때 `lastReadMessageId = null`, `unreadCount = 전체 메시지 수` 규칙 반영
- [ ] STOMP CONNECT/SUBSCRIBE/SEND 권한 규칙 명시
- [ ] `CLOSED`, `DELETED` 이후 기존 `ACTIVE` 멤버 채팅 권한 유지 정책 반영
- [ ] Redis Pub/Sub after-commit 흐름 반영
- [ ] `deleted_at` 없이 `status = DELETED`로 soft delete하는 정책 반영
- [ ] `last_message_id`, `last_read_message_id`는 DB FK 없이 애플리케이션 레벨 포인터로 관리하는 정책 반영
- [ ] `chat_rooms.member_count`는 `ACTIVE` 멤버 수 기준이라는 정책 반영
- [ ] 메시지 응답 nickname은 현재 `users.nickname` 기준이라는 정책 반영
- [ ] `chat_messages.type`은 v1에서 `TEXT`, `SYSTEM`만 사용한다고 명시
- [ ] API 문서에서 baseline 메모와 최종 계약을 분리
- [ ] before/after 수치 반영
- [ ] scale-out 시나리오 결과 반영

## 테스트
- [ ] README만 읽고 실행 가능한지 점검

## 완료 기준
- [ ] README, ERD, API 문서가 모두 같은 확정 정책을 말한다

## 선행 이슈
- [ ] Issue 29
```

### Issue 31. 이력서용 문장 / 포트폴리오 요약 정리

```markdown
## 목표
- 프로젝트를 이력서에 바로 넣을 수 있는 문장으로 정리한다.

## 작업
- [ ] 프로젝트 제목 확정
- [ ] 한 줄 소개 작성
- [ ] 이력서 bullet 3개 작성
- [ ] 정량 수치 반영
- [ ] 면접 30초 소개 문장 작성

## 테스트
- [ ] README 내용과 이력서 문장이 일치하는지 확인

## 완료 기준
- [ ] 이력서에 바로 넣을 수 있는 상태다

## 선행 이슈
- [ ] Issue 30
```

---
