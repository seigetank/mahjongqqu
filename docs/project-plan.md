# mahjongqqu Project Plan

앱 표시 이름은 **마탱이**입니다.

## 개요

마탱이는 숫자 `1`부터 `9`까지의 마작패를 사각형 영역으로 선택해 합계 `10`을 만들면 제거하는 Android 타임어택 퍼즐 게임입니다.

핵심 방향은 단순합니다.

- 게임 규칙은 Android UI와 분리된 순수 Kotlin 엔진에서 처리합니다.
- 같은 시드와 같은 액션 로그는 항상 같은 결과를 만들어야 합니다.
- 공식 점수는 서버에서 리플레이 검증으로 다시 계산합니다.
- 랭킹 플레이는 광고나 결제로 유리해지지 않아야 합니다.
- 프로젝트 파일은 여러 컴퓨터에서 옮겨도 동작하도록 상대 경로를 사용합니다.

## 게임 규칙

- 플레이어는 보드 위를 드래그해 사각형 영역을 만듭니다.
- 선택 사각형 안에 중심점이 들어온 제거되지 않은 패만 선택됩니다.
- 선택된 패의 숫자 합계가 정확히 `10`이면 패를 제거합니다.
- 합계가 `10`이 아니면 상태를 변경하지 않습니다.
- 제거된 칸은 빈칸으로 유지합니다.
- 중력, 자동 정렬, 자동 리필은 적용하지 않습니다.
- 제한시간이 `0`이 되면 게임이 종료됩니다.

## 기본 보드

- 기본 크기: `10 x 14`
- 총 패 수: `140`
- 숫자 범위: `1`~`9`
- 계열: 만수, 통수, 삭수
- 제한시간: `120초`
- 목표 합계: `10`
- 최소 선택 패 수: `2`

## 모드

- 랜덤 타임어택: 매 판 다른 시드로 플레이합니다.
- 주간 챌린지: 같은 주의 모든 사용자가 같은 고정 시드로 경쟁합니다.
- 무한 모드: 제한시간 동안 여러 보드를 이어서 플레이합니다.

## 점수

기본 점수:

```text
baseScore = clearedTileCount * 100
```

다중 패 보너스:

```text
multiTileBonus = (clearedTileCount - 2)^2 * 50
```

계열 보너스:

- 모두 같은 계열: 기본 점수의 `20%`
- 만수/통수/삭수 모두 포함: 기본 점수의 `10%`

## 기술 구조

- Android: Kotlin, Jetpack Compose, Compose Canvas
- Game Engine: Android API에 의존하지 않는 순수 Kotlin 모듈
- Local Storage: DataStore
- Backend: Kotlin, Ktor
- 검증: 서버가 시드와 액션 로그로 결과를 재생해 점수를 재계산
- CI: GitHub Actions

## 자산 정책

앱에 포함되는 마작패 이미지는 개별 PNG 파일로 관리합니다. 현재 자산은 Public Domain / CC0 1.0으로 배포되는 `FluffyStuff/riichi-mahjong-tiles`의 `Export/Regular` PNG 파일을 사용합니다.

## 출시 전 남은 작업

- Play Games Services v2 연동
- Play Integrity 검증 연동
- Firebase Crashlytics 및 Remote Config 연결
- PostgreSQL/Redis 영속 저장소 연결
- 계정 삭제 및 개인정보 처리 흐름
- 실제 운영용 시즌 롤오버
- Google Play 스토어 등록정보와 Data safety 작성
