# 다른 PC에서 실행하기

2026-11-14 기준. Android 앱, 모바일 웹, 그룹 서버, GIF·폰트, Gradle Wrapper는 같은 저장소에 포함된다. GitHub ZIP을 받거나 clone한다. 모바일 폴더만 복사하면 서버가 제공하는 GIF·폰트가 빠지므로 저장소 전체를 받는다.

- 저장소: https://github.com/gomh4417/camp-on
- 전체 ZIP: https://github.com/gomh4417/camp-on/archive/refs/heads/main.zip

## 1. 준비

- Android Studio: 저장소의 AGP 9.5.0 / Gradle 9.6.0 설정을 지원하는 환경.
- Android SDK API 37. 앱 minSdk는 28이다.
- Gradle daemon JDK 25 (`gradle/gradle-daemon-jvm.properties`). 자동 다운로드가 실패하면 JDK 25를 설치해 지정한다.
- Node.js: 현재 검증 버전 22.19.0. 서버 실행과 npm test에는 외부 npm 패키지가 필요 없다.
- Pleos Connect 에뮬레이터: 공식 배포 이미지로 별도 설치. 현재 Windows 검증 환경의 AVD는 Pleos Connect v2.1.0이다. AVD·SDK·가상 차량 콘솔은 GitHub에 포함되지 않는다.

```powershell
git clone https://github.com/gomh4417/camp-on.git
```

ZIP은 압축을 푼 루트 폴더를 연다. `local.properties`의 SDK 경로는 새 PC의 Android Studio가 생성하도록 한다. 기존 PC의 파일을 복사하지 않는다.

## 2. 그룹 서버와 모바일 웹 시작

저장소 루트에서 실행하고 이 터미널을 켜 둔다.

```powershell
node services/group-sync/companion.mjs
```

- PC 브라우저: http://127.0.0.1:8788/
- 서버 확인: http://127.0.0.1:8788/api/health → `{"ok":true}`
- Android 에뮬레이터의 앱 기본 서버: http://10.0.2.2:8788
- 새 PC에서는 빈 그룹 저장소로 시작한다. 기존 참여자·대화·초대 코드·인증 토큰은 GitHub에 포함되지 않는다.
- 데이터는 `services/group-sync/data/companion-live.json`에 저장된다. 재시작 후에도 유지되며 Git에서는 제외한다. 필요하면 `COMPANION_DATA_FILE` 환경 변수로 별도 영속 경로를 지정한다.

**Android Studio의 app Run 버튼은 그룹 서버나 공개 터널을 시작하지 않는다.** 서버를 먼저 실행한다. 8788 포트를 바꾸면 Android 기본 서버 설정도 함께 수정해야 한다.

## 3. 휴대폰 접속

cloudflared를 별도 설치한 뒤, 두 번째 터미널에서 실행한다.

```powershell
cloudflared tunnel --url http://127.0.0.1:8788 --no-autoupdate
```

출력된 HTTPS 주소를 휴대폰에 공유한다. 같은 서버에 연결된 차량 앱에서 그룹을 만들고 숫자 4자리 코드를 정한 뒤, 휴대폰에서 그 코드로 참여한다. 닉네임·차량명은 각 참여자가 입력한다.

`trycloudflare.com` 주소는 임시 주소다. PC/서버/터널이 종료되면 접속되지 않으며 터널을 새로 생성하면 호스트명이 바뀐다. 다른 PC가 실행할 때는 그 PC에서 새로 출력된 주소를 사용한다.

고정 주소 배포는 아직 완료되지 않았다. 고정 호스트명의 Cloudflare Tunnel 또는 상시 서버와 도메인 구성이 별도로 필요하다. 주소가 고정되어도 로컬 PC를 서버로 사용하면 PC가 꺼질 때 서비스가 중단된다.

`services/group-sync/mobile/vercel.json`은 과거 임시 터널을 가리키는 설정이다. 현재 실행 방법은 위 Node 서버이며 이 파일로 Vercel에 그대로 배포하면 동작하지 않는다. 정적 웹만 배포해도 그룹 API·영속 저장소·GIF·폰트 제공이 자동으로 배포되는 것은 아니다.

## 4. Android 실행

1. Android Studio에서 저장소 루트를 열고 Gradle Sync한다.
2. Pleos 에뮬레이터를 시작하고 app 실행 구성을 선택한다.
3. 위 그룹 서버가 켜져 있는지 확인한다.
4. 처음의 함께 이동 화면에서 대표가 그룹을 생성한다. 모바일 참여자를 초대하고 출발지·목적지를 지정한다.
5. 설정 완료 → 함께 출발하기 → 지도 핀 도착 → 캠핑 준비로 이어진다. 캠핑 종료·복원이 완료되면 그룹 화면에서 역방향 경로를 재생한다.

그룹 이동 핀은 경로 애니메이션이며 실제 차량 GPS·기어·제어 권한을 변경하지 않는다. 실제 차량 제어는 Pleos 연결, 권한, 주차·주행 제한과 수신값에 따른다. 미상 값을 임의 데이터로 대체하지 않는다.

Pleos 콘솔은 SDK의 adb가 PATH에 있는 터미널에서 다음과 같이 연결한다.

```powershell
adb forward tcp:8088 tcp:8088
```

http://127.0.0.1:8088/ 을 열어 차량 콘솔을 유지한다. 이 콘솔은 그룹 서버(8788)와 다른 서비스다. 기기가 여러 개면 `adb -s <기기ID>`로 대상 기기를 지정한다.

- [Pleos 에뮬레이터 설치](https://document.pleos.ai/docs/connect/guide/getting-started/application-development/setup-connect-sdk-emulator)
- [Pleos 차량 콘솔](https://document.pleos.ai/docs/connect/guide/getting-started/application-development/use-vehicle-emulator)

SDK Maven 주소는 `settings.gradle.kts`에 포함되어 있다. 의존성 다운로드에는 네트워크와 해당 저장소 접근 권한이 필요하다. 일반 Android 기기에서는 Pleos 차량 제어를 검증할 수 없다.

## 5. 검증

저장소 루트:

```powershell
npm --prefix services/group-sync test
.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest
```

현재 개발 PC에서 Android 빌드·단위 테스트 19개, Node 테스트 11개, 기기 흐름 테스트 4개와 모바일 15개 화면 검사를 통과했다. GitHub main의 앱 코드 `fe80c52`를 별도 폴더에 새로 clone한 뒤 외부 npm 패키지 설치 없이 Node 테스트 11개가 다시 통과한 것도 확인했다. 새 PC의 Pleos 설치·실차 동작까지 보장하는 결과는 아니다.

브라우저 캡처 도구는 추가로 Playwright와 Chrome이 필요하다. 기본 앱·서버 실행에는 필요 없다. `services/group-sync`에서 `npm install --no-save --package-lock=false playwright` 후 실행한다. 기기 검증 스크립트에는 Windows adb 경로·emulator-5554·사용자 10 설정이 있으므로 다른 환경에 맞춰 확인한다.

최신 기록: [모바일·프로필·귀가 검증](verification/2026-11-14/mobile-profile-return.md), [그룹 이동 흐름](group-map-simulation.md).

## 제외되는 파일

개인 인증 정보, 서명 키, .env, IDE 캐시, SDK·AVD, node_modules, 빌드 산출물, 사용자 그룹 데이터는 GitHub에 올리지 않는다. APK는 위 빌드로 생성된다. 동료가 저장소를 받으면 같은 소스로 새 환경에 맞게 빌드할 수 있다.

작업 시작 전 `git pull --ff-only`, 완료 후 commit/push한다.
