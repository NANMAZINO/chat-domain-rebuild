# Baseline 측정 실행 가이드

이 문서는 baseline 성능 측정을 같은 조건으로 다시 실행하기 위한 기준 문서입니다.

baseline은 이후 성능 개선 전후를 비교할 때 사용하는 기준점이므로, 실행 환경, 데이터셋, 반복 횟수를 고정한 상태에서 재측정해야 합니다.

## 목적

이 가이드는 다음 내용을 고정하기 위해 사용합니다.

- 어떤 API와 지표를 baseline 대상으로 삼는지
- 어떤 데이터셋으로 측정하는지
- 어떤 반복 조건으로 결과를 수집하는지
- 어떤 기준으로 안정성을 판단하는지

측정 결과의 의미와 숫자 해석 방법은 `docs/baseline-measurement-explained.md`를 참고합니다.

## 실행 방법

프로젝트 루트에서 아래 명령을 실행합니다.

```powershell
./gradlew baselineMeasurement
```

p95 안정성 편차까지 테스트 실패 조건으로 검사하려면 아래 명령을 사용합니다.

```powershell
./gradlew baselineMeasurement -Dbaseline.measurement.strict=true
```

## 산출물

- 최신 리포트는 `docs/measurements/baseline-latest.md`에 생성됩니다.
- 리포트 생성 단계까지 도달한 뒤 검증에 실패하는 경우 최신 리포트는 먼저 기록합니다.
- 기본 실행은 p95 안정성을 `PASS/WARN`으로 기록하고 태스크를 계속 진행합니다.

## 고정 데이터셋

baseline 비교의 일관성을 위해 아래 데이터셋을 고정합니다.

- 측정 대상 사용자 1명은 `ACTIVE` 상태의 채팅방 80개에 참여합니다.
- 각 채팅방은 작성자 1명, 측정 대상 사용자 1명, 추가 참여자 1명으로 구성합니다.
- 일반 채팅방 79개에는 방마다 메시지 50개를 적재합니다.
- 히스토리 측정용 대상 채팅방 1개에는 메시지 10,000개를 적재합니다.
- room list 측정은 `GET /api/chat-rooms?size=20` 1페이지를 기준으로 합니다.
- 메시지 히스토리 측정은 `GET /api/chat-rooms/{roomId}/messages?size=30` 최신 페이지와 cursor 페이지를 각각 기준으로 합니다.

## 고정 실행 조건

baseline 비교의 재현성을 위해 아래 실행 조건을 유지합니다.

- Spring profile은 `test`를 사용합니다.
- DB/Redis는 Testcontainers의 `mysql:8.4`, `redis:7.4`를 사용합니다.
- 측정은 애플리케이션 내부 통합 테스트로 수행합니다.
- `baselineMeasurement` 테스트 JVM heap은 `1g`로 고정합니다.
- p95는 MockMvc 기반 요청 처리 시간으로 측정합니다.
- room list query count는 `ChatRoomService#getChatRooms` 호출 시 Hibernate `prepareStatementCount`로 측정합니다.
- 워밍업은 각 측정 라운드마다 15회 수행합니다.
- 본측정은 각 측정 라운드마다 60회 수행합니다.
- p95 재현성 확인을 위해 같은 조건으로 3라운드를 연속 실행합니다.
- room list query count는 10회 반복 측정합니다.

## 허용 편차 기준

반복 측정 결과의 안정성은 아래 기준으로 판단합니다.

- 메시지 히스토리 최신 페이지 p95: 3라운드 기준 편차 30% 이하
- 메시지 히스토리 cursor 페이지 p95: 3라운드 기준 편차 30% 이하
- 채팅방 목록 조회 p95: 3라운드 기준 편차 30% 이하
- room list query count: 반복 측정 간 편차 0

## 비교 시 주의사항

baseline 결과를 before/after 비교에 사용할 때는 아래 사항을 함께 고려합니다.

- 현재 baseline room list 구현은 페이지 크기만큼 자르기 전에 전체 활성 방을 먼저 스냅샷으로 계산합니다.
- 따라서 room list query count는 page size보다 활성 채팅방 총수의 영향을 더 크게 받습니다.
- 같은 비교군을 유지하려면 채팅방 수와 메시지 수를 바꾸지 않아야 합니다.
- `baselineMeasurement` 태스크는 `test` 프로필의 새 스키마에서 실행되므로 측정 조건이 매번 새로 맞춰집니다.
- `local` 프로필 DB는 `ddl-auto: update`라서 과거 스키마 상태가 남아 있을 수 있으므로 baseline 비교 기준으로 사용하지 않는 편이 안전합니다.

## 관련 문서

- 측정 결과 해석 문서: `docs/baseline-measurement-explained.md`
- 최신 측정 결과: `docs/measurements/baseline-latest.md`
- 고정 baseline 기록: `docs/measurements/baseline-2026-04-07.md`
