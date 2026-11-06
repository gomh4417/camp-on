# 다른 PC에서 이어서 개발

Android Studio 또는 Google 계정 로그인만으로 로컬 프로젝트가 복사되지 않습니다.
GitHub에서 clone해야 합니다. 저장소에는 소스·Gradle Wrapper·폰트·문서가 포함됩니다.

## 가져오기
1. Git과 Android Studio 설치. AGP 9.5.0 / Gradle 9.6.0을 지원하는 버전을 사용합니다.
2. 시작 화면의 Clone Repository 또는 Get from VCS 선택. 이미 프로젝트를 열었다면 File > New > Project from Version Control.
3. URL: https://github.com/gomh4417/camp-on.git
4. 원하는 로컬 폴더로 clone, main 브랜치 열기.
5. SDK Manager에서 Android API 37 SDK 설치, Gradle Sync.
6. Gradle daemon 설정은 gradle/gradle-daemon-jvm.properties의 JDK 25입니다. 자동 다운로드가 실패하면 JDK 25를 설치하고 Gradle JDK를 지정하세요.
7. SDK 경로 local.properties는 새 PC에서 Android Studio가 생성합니다. 다른 PC 경로를 복사하지 않습니다.

터미널:
```sh
git clone https://github.com/gomh4417/camp-on.git
cd camp-on
```

## Pleos 실행 환경
에뮬레이터 이미지와 AVD는 GitHub에 포함되지 않습니다.
공식 안내에 따라 해당 PC의 CPU/OS에 맞는 Pleos Connect 이미지를 설치하고 AVD를 만드세요.
기존 환경은 Apple Silicon / Pleos Connect v2.1.0 / Android API 34입니다.
Windows·Intel 호환 이미지는 공식 배포 목록에서 확인하세요.

- 설치: https://document.pleos.ai/docs/connect/guide/getting-started/application-development/setup-connect-sdk-emulator
- 콘솔: https://document.pleos.ai/docs/connect/guide/getting-started/application-development/use-vehicle-emulator

Pleos SDK Maven 주소는 settings.gradle.kts에 들어 있습니다.
계정 승인이나 저장소 접근 문제가 생기면 새 PC의 Pleos 접근 권한을 확인하세요.

## 이어서 할 작업
[실행 기록과 남은 작업](camp-on-verification.md)을 먼저 읽으세요.
최우선은 Virtual Console / DDS bridge 활성 상태에서 SDK 공조 쓰기 반영 확인입니다.
현재 코드 빌드와 자동 테스트는 통과했습니다. 에뮬레이터 쓰기 성공은 아직 확인하지 못했습니다.

새 대화에 아래 내용을 붙여 넣으면 됩니다:
> CAMP:ON 작업을 이어서 진행해줘. AGENTS.md, docs/camp-on-spec.md, docs/camp-on-verification.md를 먼저 읽어줘. UI는 Pretendard Regular/Medium/ExtraBold와 차콜 글래스 스타일을 유지해. Pleos Virtual Console의 DDS bridge 활성 상태를 확인하고 SDK 공조 제어가 실제 에뮬레이터에 반영되도록 남은 문제를 해결해. 모든 코드 수정 후 마지막에 검증해.

## 옮겨지지 않는 것
에뮬레이터 앱 데이터·진행 중 세션·IDE 로컬 설정·현재 대화 기록·개인 설치 스킬·인증 정보는 Git으로 옮겨지지 않습니다.
핵심 개발 규칙은 저장소 AGENTS.md, 진행 상황은 docs에 보관합니다.

## 두 PC에서 작업
작업 시작 전 git pull --ff-only.
작업 종료 후 변경사항 commit과 push.
같은 파일을 양쪽에서 동시에 수정하지 않는 편이 편합니다.
GitHub push 인증은 Android Studio의 GitHub 로그인 또는 Git 인증으로 설정합니다. Google 로그인과 별개입니다.
