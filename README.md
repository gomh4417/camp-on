# CAMP:ON

Pleos 차량용 캠핑 앱과 모바일 그룹 웹앱. 그룹 생성·함께 이동·메시지·공동 할 일부터 캠핑 준비, 요리·휴식·취침 모드, 에너지 계획, 종료·복원과 귀가 경로까지 연결합니다.

**처음 실행하는 분:** [다른 PC 실행 안내](docs/continue-on-another-pc.md)를 먼저 확인하세요.

[전체 코드 ZIP 다운로드](https://github.com/gomh4417/camp-on/archive/refs/heads/main.zip) · [그룹·모바일 연결 안내](docs/mobile-companion.md)

## 빠른 실행

저장소 전체를 받은 뒤 루트에서 그룹 서버를 실행합니다. 서버와 기본 테스트는 Node.js 내장 기능만 사용합니다. 현재 검증 버전은 Node.js 22.19.0입니다.

```powershell
node services/group-sync/companion.mjs
```

PC 웹: http://127.0.0.1:8788/ · 상태 확인: http://127.0.0.1:8788/api/health

Android Studio에서 루트를 열고 Gradle Sync → Pleos 에뮬레이터 → app Run. Android 기본 그룹 서버는 `http://10.0.2.2:8788`입니다. **app Run 버튼은 Node 서버·공개 터널을 자동으로 시작하지 않습니다.**

휴대폰은 실행 중인 서버의 HTTPS 터널 주소로 접속합니다. 대표가 숫자 4자리 코드를 정하고, 참여자는 닉네임·차량명을 입력합니다. 가입하지 않은 차량·메시지는 만들지 않습니다.

## 모바일 공개 주소

현재 공유한 `trycloudflare.com` 주소는 **임시 터널**입니다. 터널을 새로 만들면 URL이 바뀌고 PC·서버·터널이 꺼지면 접속할 수 없습니다. 고정 도메인과 상시 배포는 아직 완료되지 않았습니다. [접속 주소와 실행 명령](docs/mobile-companion.md)

과거 `mobile/vercel.json`은 사용이 끝난 임시 서버를 가리킵니다. 그대로 Vercel에 올려 실행하는 구성은 아닙니다. 새 PC에서는 [실행 안내](docs/continue-on-another-pc.md)의 Node 서버 방식을 사용하세요.

## 주요 코드

- `FigmaCampUi.kt` / `FigmaHome.kt`: 캠핑 화면, 모드·장비·날짜·종료 UI.
- `CampScene.kt` / `app/src/main/assets/camping`: 시간대별 GIF 반복·전환, 시스템 동작 줄이기 지원.
- `JourneySession.kt` / `JourneyUi.kt` / `JourneyMap.kt`: 그룹 연결·전체 메시지·지도 및 귀가 흐름.
- `services/group-sync/companion.mjs`: 그룹 API, 모바일 웹·GIF·폰트 제공, 로컬 JSON 영속 저장.
- `services/group-sync/mobile`: 반응형 모바일 웹, 닉네임 첫 글자 프로필.
- `CampSession.kt` / `VehicleSource.kt`: 공유 캠핑 상태, Pleos 제어·수신값·조건부 복원.
- `CabinMap.kt` / `VehicleEnvironment.kt`: 좌석별 선택과 차량 환경 제어.

단일 app 모듈과 기존 Kotlin/Compose/SDK 버전을 유지합니다. SDK 요청 접수와 실제 수신값을 구분합니다. 그룹 이동 핀은 실제 GPS가 아닌 경로 애니메이션이며 차량 위치·기어·제어 권한을 변경하지 않습니다. 에너지 결과는 명시된 입력·소비 가정에 따른 추정입니다.

## 검증과 기록

2026-11-14 개발 PC: Android 빌드, 단위 테스트 19개, Node 테스트 11개, 기기 흐름 테스트 4개, 모바일 화면 15개 검사를 통과했습니다. 기기 흐름 테스트는 격리 서버·테스트 차량 제어를 사용합니다. 전체 실차 기능을 모두 검증했다는 의미는 아닙니다.

```powershell
npm --prefix services/group-sync test
.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest
```

[최신 검증·스크린샷](docs/verification/2026-11-14/mobile-profile-return.md) · [그룹 경로 동작](docs/group-map-simulation.md) · [명세](docs/camp-on-spec.md) · [누적 기록](docs/camp-on-verification.md)

SDK·AVD·개인 인증·IDE 캐시·사용자 그룹 데이터는 제외됩니다. 소스, Gradle Wrapper, GIF, 폰트와 라이선스는 포함됩니다. 폰트·아이콘 라이선스는 `app/src/main/assets`에서 확인하세요.
