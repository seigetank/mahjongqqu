# mahjongqqu

Android 앱 표시 이름은 **마탱이**입니다.

마작패를 사각형으로 드래그해 선택하고, 선택된 패의 숫자 합계가 `10`이면 제거하는 타임어택 퍼즐 게임입니다. 현재 저장소에는 Android 네이티브 앱, 순수 Kotlin 게임 엔진, Ktor 기반 검증 서버 골격, CI와 배포 준비 문서가 포함되어 있습니다.

## 현재 구현

- Kotlin + Jetpack Compose + Canvas Android 앱
- `1`~`9` 만수/통수/삭수 마작패 렌더링
- 저작권 이슈를 줄인 Public Domain / CC0 마작패 개별 PNG 자산
- 사각형 드래그 선택
- 합계 `10` 제거 규칙
- 랜덤 타임어택, 주간 챌린지, 무한 모드
- 콤보와 점수 계산
- DataStore 기반 로컬 최고 기록
- Android UI와 분리된 순수 Kotlin 게임 엔진
- Ktor 서버의 세션 발급 및 결과 검증 골격
- Codex 대화 이동용 아카이브 워크플로

## 요구 환경

- JDK 17
- Android SDK Platform 36
- Android Studio 또는 Android SDK command line tools
- Gradle wrapper는 저장소에 포함
- Docker는 로컬 서버 컨테이너 테스트가 필요할 때만 사용

머신별 경로는 Git에 커밋하지 않습니다. Android Studio가 생성하는 `local.properties`는 `.gitignore`에 포함되어 있습니다.

## Android 빌드

```powershell
.\gradlew.bat :app:assembleDebug --no-daemon
```

에뮬레이터나 기기에서 `app` 모듈을 실행하면 됩니다.

## 서버 실행

```powershell
.\scripts\run-server.ps1 -Port 8080
```

Docker가 필요한 경우:

```powershell
docker compose up --build
```

주요 엔드포인트:

- `GET /health`
- `POST /v1/game-sessions`
- `POST /v1/game-results`
- `GET /v1/leaderboards/{seasonId}/{mode}`

## 검증

```powershell
.\scripts\check-portable-paths.ps1
.\gradlew.bat :game:engine:test :server:test --no-daemon
.\gradlew.bat :app:assembleDebug --no-daemon
```

## 자산 라이선스

마작패 이미지는 [FluffyStuff/riichi-mahjong-tiles](https://github.com/FluffyStuff/riichi-mahjong-tiles)의 개별 PNG를 사용합니다.

- 라이선스: Public Domain / CC0 1.0
- 출처 문서: `assets/third_party/fluffystuff_riichi_mahjong_tiles/README.md`

## Codex 대화 이동

여러 컴퓨터에서 이어서 작업하기 위해 Codex 대화 아카이브는 `codex/conversations/` 아래에 둡니다. 원본 Codex 로컬 상태, 인증 파일, DB, 로그는 커밋하지 않습니다.

```powershell
.\scripts\export-codex-conversation.ps1
```

GitHub에 올리기 전에는 `codex/conversations/*.json`에 토큰, 개인키, 로컬 전용 절대경로가 없는지 확인합니다.
