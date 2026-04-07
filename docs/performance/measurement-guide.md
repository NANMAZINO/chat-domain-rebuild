# 성능 측정 가이드

이 문서는 성능 측정을 다시 실행할 때 필요한 기준과, 생성된 raw 리포트를 읽는 기준을 함께 정리한 문서다.

## 어디부터 읽으면 되는가

- 최종 비교 결과를 먼저 보려면 [공식 재측정 결과](results/issue-22-remeasurement.md)를 본다.
- 단계별 변경 판단을 보려면 [성능 개선 기록](improvements.md)을 본다.
- 원본 수치를 직접 확인하려면 [measurements/](measurements/) 아래 스냅샷을 본다.

## 측정 목적

이 프로젝트의 성능 측정은 다음 두 가지를 함께 확인하기 위한 것이다.

- 같은 데이터셋과 같은 반복 조건에서 before/after를 비교할 수 있는가
- 반복 실행 시 결과가 비교 가능한 수준으로 유지되는가

즉, 이 문서는 실행 절차와 해석 기준을 한 곳에 모아 두는 기준 문서다.

## 실행 방법

프로젝트 루트에서 아래 명령을 실행한다.

```powershell
./gradlew.bat baselineMeasurement -Dbaseline.measurement.strict=true
```

현재 구현의 최종 after 스냅샷은 strict 실행 결과를 기준으로 남긴다. historical before는 이미 기록된 rerun 스냅샷을 사용하며, strict 옵션은 안정성 편차를 실패 조건으로만 바꾸고 raw p95 수집 방식은 바꾸지 않는다. 1차 개선 수치는 별도 raw 스냅샷이 아니라 대표 요약 기록으로만 남아 있다.

## 산출물 구분

- `measurements/baseline-latest.md`
  가장 최근에 실행한 raw 리포트다. 측정 태스크가 덮어쓰는 생성 산출물이라 fresh clone에는 없을 수 있다.

- [measurements/baseline-rerun-same-machine-2026-04-07.md](measurements/baseline-rerun-same-machine-2026-04-07.md)
  same-machine before 스냅샷이다.

- [measurements/improved-2026-04-07.md](measurements/improved-2026-04-07.md)
  strict 재측정 after 스냅샷이다.

- [measurements/improved-phase-1-summary-2026-04-07.md](measurements/improved-phase-1-summary-2026-04-07.md)
  2차 개선 이전에 남아 있던 1차 개선 재측정 요약 기록이다. full raw/stability 스냅샷은 아니다.

- [measurements/baseline-2026-04-07.md](measurements/baseline-2026-04-07.md)
  최초 baseline 기록이다.

즉, `measurements/baseline-latest.md`는 실행할 때마다 갱신되는 raw 결과이고, `baseline-rerun-same-machine-2026-04-07.md`와 `improved-2026-04-07.md`는 공식 before/after 스냅샷이다. `improved-phase-1-summary-2026-04-07.md`는 단계 비교를 위한 대표 요약 기록으로만 사용한다.

## 측정 대상

현재 측정 대상은 아래 네 가지다.

1. 채팅방 목록 조회 요청 처리 시간
   `GET /api/chat-rooms?size=20`
2. 메시지 최신 페이지 조회 요청 처리 시간
   `GET /api/chat-rooms/{roomId}/messages?size=30`
3. 메시지 cursor 페이지 조회 요청 처리 시간
   `GET /api/chat-rooms/{roomId}/messages?size=30&cursorMessageId=...`
4. 채팅방 목록 조회 시 query count
   `ChatRoomService#getChatRooms` 호출 동안 집계한 Hibernate `prepareStatementCount`

## 고정 데이터셋과 실행 조건

- 측정 대상 사용자 1명은 `ACTIVE` 상태의 채팅방 80개에 참여한다.
- 각 채팅방은 작성자 1명, 측정 대상 사용자 1명, 추가 참여자 1명으로 구성한다.
- 일반 채팅방 79개에는 방마다 메시지 50개를 적재한다.
- 히스토리 측정용 대상 채팅방 1개에는 메시지 10,000개를 적재한다.
- Spring profile은 `test`를 사용한다.
- DB/Redis는 Testcontainers `mysql:8.4`, `redis:7.4`를 사용한다.
- 측정은 애플리케이션 내부 통합 테스트로 수행한다.
- `baselineMeasurement` 테스트 JVM heap은 `1g`로 고정한다.
- 워밍업은 각 측정 라운드마다 15회 수행한다.
- 본측정은 각 측정 라운드마다 60회 수행한다.
- 같은 조건으로 3라운드를 연속 실행한다.
- room list query count는 10회 반복 측정한다.

## 허용 편차 기준

- 메시지 히스토리 최신 페이지 p95: 3라운드 기준 편차 30% 이하
- 메시지 히스토리 cursor 페이지 p95: 3라운드 기준 편차 30% 이하
- 채팅방 목록 조회 p95: 3라운드 기준 편차 30% 이하
- room list query count: 반복 측정 간 편차 0

## raw 리포트 읽는 방법

리포트 상단에는 실행 환경과 데이터셋 정보가 들어간다.

- `generatedAt`
  리포트를 생성한 시각이다.
- `dataset`
  채팅방 수, 메시지 수, 페이지 크기 등 고정 데이터셋 정보다.
- `iterations`
  워밍업 횟수, 본측정 횟수, 라운드 수, query count 반복 횟수다.
- `allowedDeviation`
  안정성 판단에 사용하는 허용 편차 기준이다.
- `strictStabilityCheckEnabled`
  안정성 편차를 경고로 남길지, 실패 조건으로 볼지 나타낸다.

각 요청 처리 시간 섹션에는 `minMs`, `avgMs`, `p95Ms`, `maxMs`가 들어간다. 비교에는 보통 `avgMs`보다 `p95Ms`를 우선해서 본다.

`stability` 섹션은 3라운드 반복 결과를 요약한다.

- `minP95Ms`
- `medianP95Ms`
- `maxP95Ms`
- `deviationPercent`
- `allowedDeviationPercent`
- `status`

`status: PASS`는 빠르다는 뜻이 아니라, 반복 측정 시 결과가 허용 범위 안에서 재현됐다는 뜻이다.

`Room List Query Count`는 요청 처리 시간 대신 Hibernate `prepareStatementCount`를 기록한다. `counts`는 반복 측정 목록이고, `maxDeviation`은 반복 간 최대 편차다.

## 해석 시 주의사항

- `measurements/baseline-latest.md`는 최신 실행 결과이므로 수치는 실행 시점마다 조금씩 달라질 수 있다.
- before/after 비교에는 같은 데이터셋과 같은 반복 횟수를 유지해야 한다.
- 현재 baseline room list 구현은 페이지 크기만큼 자르기 전에 전체 활성 방을 먼저 계산한다.
- 따라서 room list query count는 page size보다 활성 채팅방 총수의 영향을 더 크게 받는다.
- `baselineMeasurement`는 `test` 프로필의 새 스키마에서 실행되므로 조건이 매번 새로 맞춰진다.
- `local` 프로필 DB는 과거 스키마 상태가 남아 있을 수 있으므로 비교 기준으로 사용하지 않는 편이 안전하다.
- 포트폴리오 문서의 공식 before/after 대표 비교는 same-machine before와 strict after만 사용한다.
- 1차 개선 수치는 단계 비교가 필요할 때만 `improved-phase-1-summary-2026-04-07.md`를 통해 함께 본다.
- 메시지 히스토리 지표는 latest와 cursor를 분리해서 해석한다.
- latest 지표는 이번 재측정 결과만으로 수치적 개선 여부를 확정하지 않고, 공식 결과 문서의 해석 기준을 따른다.

## 관련 문서

- 공식 결과 문서: [results/issue-22-remeasurement.md](results/issue-22-remeasurement.md)
- 성능 개선 기록: [improvements.md](improvements.md)
