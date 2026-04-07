# Historical Baseline Rerun Snapshot (2026-04-07)

이 문서는 2026-04-07에 baseline 구현 커밋 `b961dac`를 같은 머신에서 다시 실행해 얻은 before 수치 기록입니다.
Issue 22의 공식 same-machine before 스냅샷이며, 최초 baseline 기록은 [Baseline Snapshot (2026-04-07)](baseline-2026-04-07.md)에 따로 보관합니다.

## 기준 리포트

- 측정 일시: 2026-04-07 07:19:23 KST
- 측정 대상 커밋: `b961dac` (`test: baseline 측정 기준과 비교 문서 정리`)
- 실행 명령: `./gradlew baselineMeasurement`
- 기록 방식: detached worktree에서 생성된 raw latest 리포트 값을 옮겨 적어 확정했습니다.
- 참고: 이 rerun은 non-strict로 실행했습니다. `strict` 옵션은 안정성 편차를 실패 조건으로만 바꾸며 raw p95 수집 방식과 수치 계산 방식은 바꾸지 않습니다.

## 대표 before 수치

- 채팅방 목록 조회 p95 median: `1589.914ms`
- 메시지 히스토리 최신 페이지 p95 median: `35.692ms`
- 메시지 히스토리 cursor 페이지 p95 median: `86.883ms`
- room list query count: `161`

## 반복 측정 결과

### 채팅방 목록 조회 p95

- round-1 p95: `1589.914ms`
- round-2 p95: `1329.220ms`
- round-3 p95: `1695.474ms`
- median p95: `1589.914ms`
- stability status: `PASS`

### 메시지 히스토리 최신 페이지 p95

- round-1 p95: `42.517ms`
- round-2 p95: `26.637ms`
- round-3 p95: `35.692ms`
- median p95: `35.692ms`
- stability status: `WARN`

### 메시지 히스토리 cursor 페이지 p95

- round-1 p95: `86.883ms`
- round-2 p95: `68.802ms`
- round-3 p95: `87.513ms`
- median p95: `86.883ms`
- stability status: `PASS`

### Room List Query Count

- 반복 측정값: `[161, 161, 161, 161, 161, 161, 161, 161, 161, 161]`
- max deviation: `0`

## 측정 조건

- Spring profile: `test`
- DB/Redis: Testcontainers `mysql:8.4`, `redis:7.4`
- Java: `17.0.18`
- OS: `Windows 11 10.0`
- JVM heap: `1g`
- active room count: `80`
- regular room message count: `50`
- target room message count: `10000`
- room list page size: `20`
- history page size: `30`
- warmup: `15`
- measurement iterations: `60`
- rounds: `3`
- query count iterations: `10`
