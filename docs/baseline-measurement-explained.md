# Baseline 측정 결과 읽기

이 문서는 `docs/measurements/baseline-latest.md`에 기록되는 성능 측정값을 해석하기 위한 안내서입니다.

`docs/baseline-measurement.md`가 측정 재실행 절차를 설명하는 문서라면, 이 문서는 결과 리포트에 나온 각 항목이 무엇을 의미하는지 설명하는 문서입니다.

## 목적

baseline은 성능 개선 전 기준점을 기록한 결과입니다.

- 이후 QueryDSL, 인덱스, Redis 캐시 등 개선 작업 전후를 같은 기준으로 비교할 수 있습니다.
- 단일 수치뿐 아니라 반복 측정 시 결과가 얼마나 안정적으로 유지되는지도 함께 확인할 수 있습니다.

즉, baseline 리포트는 현재 구현의 성능 특성을 정리한 비교 기준 문서입니다.

## 측정 대상

현재 baseline은 다음 네 가지를 측정합니다.

1. 채팅방 목록 조회 응답 시간
   `GET /api/chat-rooms?size=20`
2. 메시지 최신 페이지 조회 응답 시간
   `GET /api/chat-rooms/{roomId}/messages?size=30`
3. 메시지 cursor 페이지 조회 응답 시간
   `GET /api/chat-rooms/{roomId}/messages?size=30&cursorMessageId=...`
4. 채팅방 목록 조회 시 DB 쿼리 수
   `ChatRoomService#getChatRooms` 호출 동안 집계한 Hibernate `prepareStatementCount`

요약하면, "응답 시간이 얼마나 걸리는가"와 "그 과정에서 DB를 몇 번 조회하는가"를 함께 기록합니다.

## 측정 방식

성능 비교의 재현성을 높이기 위해 실행 환경, 데이터셋, 반복 횟수를 고정합니다.

### 실행 환경

- Spring `test` 프로필에서 실행합니다.
- MySQL과 Redis는 Testcontainers로 구동합니다.
- 애플리케이션 내부 통합 테스트에서 MockMvc 요청 처리 시간을 측정합니다.
- JVM heap은 `1g`로 고정합니다.

### 고정 데이터셋

- 측정 대상 사용자는 활성 채팅방 80개에 참여합니다.
- 일반 채팅방 79개에는 메시지 50개씩 적재합니다.
- 히스토리 측정용 채팅방 1개에는 메시지 10,000개를 적재합니다.
- room list는 페이지 크기 20, message history는 페이지 크기 30을 기준으로 측정합니다.

### 반복 방식

- 각 측정 전 워밍업 15회를 수행합니다.
- 실제 측정은 60회를 수행합니다.
- 같은 측정을 3라운드 반복합니다.
- room list query count는 10회 반복 측정합니다.

이 방식은 우연히 한 번 빠르거나 느린 결과가 아니라, 같은 조건에서 반복했을 때도 유사한 결과가 나오는지 확인하기 위한 것입니다.

## 리포트 상단 항목 해석

리포트 상단에는 측정 환경과 실행 설정이 요약됩니다.

- `generatedAt`
  리포트를 생성한 시각입니다.
- `javaVersion`, `os`, `availableProcessors`, `maxMemoryMiB`
  측정이 수행된 실행 환경 정보입니다.
- `reportPath`
  최신 리포트가 저장된 경로입니다.
- `dataset`
  채팅방 수, 메시지 수, 페이지 크기 등 고정 데이터셋 정보입니다.
- `iterations`
  워밍업 횟수, 본측정 횟수, 라운드 수, query count 반복 횟수입니다.
- `allowedDeviation`
  안정성 판단에 사용하는 허용 편차 기준입니다.
- `strictStabilityCheckEnabled`
  안정성 편차를 경고 수준으로 기록할지, 테스트 실패 조건으로 볼지 나타내는 설정값입니다.
- `baselineNotes`
  리포트 해석에 필요한 실행 메모입니다.

## 응답 시간 항목 해석

각 응답 시간 섹션에는 다음 항목이 공통으로 포함됩니다.

- `sampleCount`
  실제로 측정한 요청 수입니다.
- `minMs`
  가장 빨랐던 한 번의 응답 시간입니다.
- `avgMs`
  전체 평균 응답 시간입니다.
- `p95Ms`
  느린 요청 구간까지 반영한 대표 지표입니다.
- `maxMs`
  가장 느렸던 한 번의 응답 시간입니다.

실제 체감 성능을 판단할 때는 보통 `avgMs`보다 `p95Ms`를 더 중요하게 봅니다.

- 평균값은 일부 매우 빠른 요청의 영향을 크게 받을 수 있습니다.
- 반면 `p95Ms`는 사용자가 체감할 수 있는 "가끔 느린 응답"을 더 잘 반영합니다.

예를 들어 room list의 `avgMs`가 1100ms이고 `p95Ms`가 1450ms라면, 평균적으로는 1.1초 수준이지만 느린 경우까지 고려하면 약 1.45초까지 걸릴 수 있다고 해석할 수 있습니다.

## Stability 항목 해석

각 응답 시간 섹션의 마지막에는 `stability` 정보가 포함됩니다.

- `minP95Ms`
  3개 라운드 중 가장 낮은 p95 값입니다.
- `medianP95Ms`
  3개 라운드 중 가운데 p95 값입니다.
- `maxP95Ms`
  3개 라운드 중 가장 높은 p95 값입니다.
- `deviationPercent`
  라운드 간 p95 편차 비율입니다.
- `allowedDeviationPercent`
  허용 편차 기준입니다.
- `status`
  허용 범위 안에 들어왔는지 나타내는 결과입니다.

`status: PASS`는 "응답 속도가 빠르다"는 뜻이 아닙니다. 같은 조건에서 반복 측정했을 때 결과가 허용 범위 안에서 비교적 안정적으로 재현됐다는 뜻입니다. 따라서 느린 API도 반복 결과가 비슷하면 `PASS`가 될 수 있습니다.

## Room List Query Count 해석

`Room List Query Count`는 응답 시간 대신 DB 접근 횟수를 보여 주는 섹션입니다.

- `counts`
  10회 반복 측정한 쿼리 수 목록입니다.
- `maxDeviation`
  반복 측정 사이의 최대 편차입니다.
- `note`
  어떤 지점에서 어떤 방식으로 측정했는지 설명하는 메모입니다.

예를 들어 `counts`가 `[161, 161, 161, ...]`이라면, 채팅방 목록 조회 한 번마다 SQL 준비 또는 실행 횟수가 항상 161회였다는 뜻입니다.

이 값은 N+1 문제나 과도한 조회 같은 구조적 비효율을 파악할 때 특히 중요합니다.

## 권장 해석 순서

리포트를 처음 읽을 때는 다음 순서로 보는 편이 좋습니다.

1. `Room List p95`
   가장 느린 API가 무엇인지 먼저 확인합니다.
2. `Message History p95`
   최신 페이지와 cursor 페이지 중 어떤 구간이 더 무거운지 확인합니다.
3. `Room List Query Count`
   room list 성능 문제가 DB 접근 횟수와 연결되는지 확인합니다.
4. `stability`
   현재 수치가 우연한 결과인지, 반복해도 유사하게 재현되는지 확인합니다.

현재 baseline 구조에서는 room list 지표가 특히 중요합니다. 현재 구현은 페이지 크기만큼 잘라내기 전에 전체 활성 방을 먼저 계산하므로, page size보다 활성 채팅방 총수의 영향을 더 크게 받습니다. 이 때문에 room list 응답 시간과 query count가 함께 증가할 수 있습니다.

## 해석 시 주의사항

- `baseline-latest.md`는 최신 실행 결과이므로 세부 수치는 실행 시점마다 조금씩 달라질 수 있습니다.
- baseline과 개선 후 결과를 비교할 때는 반드시 같은 데이터셋과 같은 반복 횟수를 유지해야 합니다.
- `local` 환경은 과거 스키마 상태가 남아 있을 수 있으므로 baseline 비교 기준으로 사용하지 않는 편이 안전합니다.

## 관련 문서

- 측정 재실행 방법: `docs/baseline-measurement.md`
- 최신 측정 결과: `docs/measurements/baseline-latest.md`
