# Issue 22. 성능 재측정 결과

## 개요

재구현 이후 성능이 실제로 어떻게 달라졌는지 다시 확인할 필요가 있었다. README에는 결과를 요약하고, 이 문서에는 공식 비교 기준과 결과를 남긴다.

## 비교 기준

- 공식 before: [same-machine before 스냅샷](../measurements/baseline-rerun-same-machine-2026-04-07.md)
- 1차 재측정 요약 기록: [1차 개선 요약 기록](../measurements/improved-phase-1-summary-2026-04-07.md)
- 최종 after: [strict after 스냅샷](../measurements/improved-2026-04-07.md)
- 최초 baseline 보관 기록: [최초 baseline 기록](../measurements/baseline-2026-04-07.md)

로컬에서 가장 최근 raw 결과를 다시 확인하려면 `docs/performance/measurements/baseline-latest.md`를 본다. 이 파일은 측정 태스크가 덮어쓰는 생성 산출물이라 fresh clone에는 없을 수 있다.

## 재측정 결과

아래 표는 기준선과 1차 개선, 2차 개선 결과를 함께 정리한 비교표다.

| 지표 | 기준선 | 1차 재측정 | 2차 재측정 | 비고 |
| --- | ---: | ---: | ---: | --- |
| 방 목록 median p95 | `1589.914ms` | `47.195ms` | `43.766ms` | 1차 `97.03%` 개선, 2차 `97.25%` 개선 |
| 메시지 히스토리 최신 페이지 median p95 | `35.692ms` | `39.699ms` | `41.895ms` | 1차 `11.23%` 악화, 2차 `17.38%` 악화 |
| 메시지 히스토리 cursor 페이지 median p95 | `86.883ms` | `120.251ms` | `76.373ms` | 1차 `38.41%` 악화, 2차 `12.10%` 개선 |
| 방 목록 쿼리 수 | `161` | `1` | `1` | 1차와 2차 모두 `99.38%` 감소 |

1차 재측정 값은 [1차 개선 요약 기록](../measurements/improved-phase-1-summary-2026-04-07.md)에 남아 있는 대표 수치를 기준으로 적었다. 1차 시점의 라운드별 raw/stability 기록은 별도 스냅샷으로 보존되지 않았으므로, 이 열은 `중간 단계 비교용 요약 값`으로 읽는 편이 맞다.

## 해석

방 목록 조회는 1차에서 크게 줄었고 2차에서도 개선 상태를 유지했다. query count도 1차부터 `161 -> 1`로 정리된 뒤 그대로 유지됐다.

메시지 히스토리 조회는 latest와 cursor를 분리해서 본다. latest 페이지는 1차와 2차 모두 기준선보다 높은 수치가 나왔고, cursor 페이지는 1차에서는 기준선보다 높았지만 2차에서 기준선보다 낮아졌다.

## 측정 조건

- 공식 before와 최종 after는 같은 데이터셋, 같은 반복 횟수, 같은 `test` 프로필 기준으로 비교했다.
- 최종 after 스냅샷은 `./gradlew baselineMeasurement -Dbaseline.measurement.strict=true` 실행 결과를 기준으로 고정했다.
- 1차 재측정 값은 동일한 측정 문맥에서 남아 있던 대표 요약 수치지만, raw/stability 스냅샷은 보존되지 않았다.
- 상세 절차와 raw 리포트 해석은 [측정 가이드](../measurement-guide.md)를 참고한다.

## 검증

2026-04-07 기준으로 아래 검증을 다시 실행했다.

```powershell
./gradlew.bat test
./gradlew.bat -Dbaseline.measurement.strict=true baselineMeasurement
```

두 명령 모두 Docker/Testcontainers가 정상 기동된 상태에서 통과했다.

## 정리

이번 재측정 결과는 "전체 조회가 일괄적으로 빨라졌다"로 정리하지 않는다. 방 목록 조회는 1차부터 크게 개선됐고, cursor 페이지는 2차에서 기준선보다 낮아졌다. latest 조회는 읽기 경량화를 적용했지만 측정 편차가 함께 있어, 이번 결과만으로는 수치적 개선 여부를 확정하지 않았다. 이번 이슈는 재측정과 확인 가능한 개선 정리까지를 범위로 마감한다.

## 관련 문서

- [측정 가이드](../measurement-guide.md)
- [성능 개선 기록](../improvements.md)
