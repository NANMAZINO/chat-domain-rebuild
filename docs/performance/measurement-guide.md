# 성능 측정 가이드

## 실행 명령

PowerShell 기준 일반 rerun:

```powershell
.\gradlew.bat baselineMeasurement
```

PowerShell 기준 strict rerun:

```powershell
.\gradlew.bat baselineMeasurement '-Dbaseline.measurement.strict=true'
```

`strict`는 p95 편차를 실패 조건으로만 바꾸며, raw 수집 방식과 계산 방식은 바꾸지 않는다.
일반 실행과 strict 실행 모두 현재 측정 결과를 `docs/performance/measurements/latest-measurement.md`에 새로 작성한다.

## 고정 조건

- Spring profile: `test`
- DB/Redis: Testcontainers `mysql:8.4`, `redis:7.4`
- active room count: `80`
- regular room message count: `50`
- target room message count: `10000`
- room list page size: `20`
- history page size: `30`
- warmup: `15`
- measurement iterations: `60`
- rounds: `3`
- room list query count iterations: `10`
