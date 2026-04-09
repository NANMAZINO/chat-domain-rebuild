# 성능 결과와 기록

자세한 raw 값은 `measurements/` 아래 스냅샷을 참조한다.

## 측정 파일 구성

- [기준선 측정](measurements/baseline-measurement.md)
  공식 before 비교에 사용하는 기준선이다.
- [1차 측정](measurements/first-improvement-measurement.md)
  1차 개선 시점의 대표 수치만 남긴 중간 기록이다.
- [2차 측정](measurements/second-improvement-measurement.md)
  공식 after 비교에 사용하는 2차 측정 스냅샷이다.

## 비교 결과

| 지표 | 기준선 | 1차 측정 | 2차 측정 | 비고 |
| --- | ---: | ---: | ---: | --- |
| 방 목록 median p95 | `1589.914ms` | `47.195ms` | `43.766ms` | 1차 `97.03%` 개선, 2차 `97.25%` 개선 |
| 메시지 히스토리 최신 페이지 median p95 | `35.692ms` | `39.699ms` | `41.895ms` | 1차 `11.23%` 악화, 2차 `17.38%` 악화 |
| 메시지 히스토리 cursor 페이지 median p95 | `86.883ms` | `120.251ms` | `76.373ms` | 1차 `38.41%` 악화, 2차 `12.10%` 개선 |
| 방 목록 쿼리 수 | `161` | `1` | `1` | 1차와 2차 모두 `99.38%` 감소 |

## 결과 해석

- 방 목록 조회는 1차에서 전체 활성 방을 먼저 훑던 흐름을 QueryDSL projection + room summary 기반 조회로 바꾸면서 병목을 거의 제거했고, 2차에서도 그 구조를 유지했다.
- 메시지 히스토리 조회는 1차와 2차 모두 개선 작업이 들어갔다. 다만 latest 페이지 p95는 두 차례 측정 모두 기준선보다 높아, 현재 수치만으로는 latest 페이지 개선을 확인했다고 쓰지 않는다.
- 메시지 히스토리 cursor 페이지도 1차부터 조회 구조를 손봤지만, 1차 요약 측정에서는 p95가 함께 내려가지 않았다. 이후 2차에서 `exists query`와 DTO projection을 적용한 뒤에는 기준선보다 낮아졌다.

## 단계별 기록

### 기준선

room list는 페이지 크기만큼 자르기 전에 전체 활성 방을 먼저 계산하는 구조였다. 그래서 방 수가 늘수록 응답 시간과 query count가 함께 커졌고, room list 병목이 가장 크게 드러났다.

### 1차 개선

1차는 room list 병목을 먼저 걷어내는 단계였다. 기존에는 페이지 크기만큼 자르기 전에 전체 활성 방을 먼저 계산하는 흐름이라 방 수가 늘수록 응답 시간과 query count가 함께 커졌다. 이를 QueryDSL projection 중심 조회로 바꾸고, `chat_rooms`에 저장된 `lastMessageId`, `lastMessagePreview`, `lastMessageAt` summary와 `chat_room_members.lastReadMessageId`를 한 번에 읽는 구조로 재구성했다. 이 단계에서 room list는 응답에 필요한 값만 바로 조회하게 되었고, query count도 `161 -> 1`로 줄었다.

같은 시점에 메시지 히스토리 조회도 개선했다. 기존의 단순 JPA/JPQL 조회를 `cursorMessageId` 기반 cursor pagination과 `(room_id, id desc)` 접근 패턴에 맞춰 정리했고, latest 페이지와 cursor 페이지가 같은 읽기 계약 위에서 동작하도록 구조를 맞췄다. 즉 1차에도 메시지 조회 개선은 분명히 들어갔지만, 보존된 1차 요약 측정에서는 latest/cursor p95가 모두 기준선보다 높게 나와 수치상 개선으로 이어졌다고 정리하지는 않는다.

### 2차 개선

2차는 메시지 읽기 경량화 단계였다. 먼저 멤버 확인에서 엔티티를 읽어 오는 대신 `existsByRoomIdAndUserIdAndStatus(...)`로 ACTIVE 멤버 여부만 확인하도록 바꿨다. 이어서 메시지 히스토리 조회도 `ChatMessage` 엔티티와 `join fetch`로 sender를 함께 읽는 방식 대신, `messageId`, `senderId`, `senderNickname`, `content`, `type`, `createdAt`만 담는 응답용 projection을 직접 조회하도록 조정했다. 서비스는 이 projection을 바로 `ChatMessageResponse`로 변환하므로 읽기 경로의 객체 적재 비용을 줄였다.

이 경량화는 latest 페이지와 cursor 페이지 모두에 적용된 메시지 조회 개선이었다. 다만 측정 결과는 다르게 나타났다. 오래된 로그를 다시 읽는 cursor 페이지는 기준선보다 낮아졌지만, latest 페이지는 `35.692ms -> 41.895ms`로 여전히 기준선보다 높았다. 따라서 현재 기록에서는 2차 개선이 메시지 조회 경로를 더 가볍게 만들었다고는 설명하되, latest 페이지까지 수치적으로 개선됐다고 서술하지 않는다.
