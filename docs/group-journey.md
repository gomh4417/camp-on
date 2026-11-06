# 함께 이동 → 캠핑 통합

## 원본과 통합 범위

- 원본: origin/Part_C, commit 4dfa927c29fbe4fc15b395df4fd1cd59a08c911b, zip/MVP_C.zip. 요청에서 MAV_C로 지칭된 파일의 실제 이름은 MVP_C.
- 압축에는 차량·모바일 APK, Node 서버와 문서가 있다. Android 앱 소스는 없다.
- 서버 REST 계약을 유지한 services/group-sync와 CAMP:ON 네이티브 그룹 화면을 추가했다. 두 앱이 같은 서버에 연결하면 차량 현황·메모·요청·할 일을 공유한다.
- 원본 MapLibre 지도, 경로 시뮬레이션, STT는 원본 차량 APK를 명시적으로 연다. 내부 화면을 CAMP:ON 코드로 병합하거나 리디자인한 것은 아니다.
- 공주등장·트랄랄라·티라미수케는 원본의 시연 차량이다. 실제 차량 3대의 인증·위치 업로드를 새로 구현한 것이 아니다. 원본 차량 앱이 실행되어야 이동 상태가 갱신된다.
- 그룹 서버의 배터리를 내 차량 에너지 계산에 대입하지 않는다. 내 차량 제어는 기존 Pleos SDK 및 주차/제한/최신성 검사를 유지한다.

## 실행

1. 저장소 루트에서 PowerShell: `node services/group-sync/server.mjs`. Node 22 사용, 추가 npm 패키지 없음.
2. CAMP:ON → 함께 이동. 에뮬레이터 기본 주소는 `http://10.0.2.2:8787`.
3. 원본 APK가 없으면 Part_C 브랜치 zip/MVP_C.zip을 별도 폴더에 추출하고 `adb install -r <압축해제경로>/APK/group_drive_mvp_debug.apk`.
4. 지도·음성 메모 열기로 원본 앱 실행. 원본 APK가 Pleos 기본 내비게이션을 자동으로 전면에 띄울 수 있다(실행 확인됨). CAMP:ON 아이콘으로 돌아와 함께 이동 탭을 연다. 뒤로 가기 한 번으로 돌아오는 동작은 보장하지 않는다. 원본 앱과 모바일 앱도 같은 PC의 서버를 사용해야 한다. 모바일은 PC의 LAN IPv4:8787(원본 앱 가이드 참조).
5. CAMP:ON 연결 설정의 '내 차량'은 내 역할 지정이다. 메인 화면 차량 칩은 조회 대상만 바꾼다.
6. 메모 전송, 요청의 할 일 추가, 체크 완료를 다른 클라이언트에서 확인할 수 있다. 1.5초 간격 조회. 실패 시 완료로 표시하지 않고 최신 상태를 재조회한다.
7. 내 차량 서버 상태가 도착이고 로컬 차량의 주차 상태가 확인되면 '도착 · 캠핑으로 이어가기'. 캠핑장과 미완료 할 일이 유지된다. 캠핑 시작/물리 제어는 별도 사용자 조작이다.

## 저장·연결

- 서버 데이터: services/group-sync/data/state.json. Git 제외. GROUP_DATA_FILE 환경변수로 독립 테스트 저장소 지정 가능.
- 앱: 서버 주소·내 차량·캠핑 진입 단계 저장. 캠핑장은 CampSession의 campSite, 귀가 목적지는 destination으로 분리.
- HTTPS 외부 서버 허용. 개발 HTTP는 10.0.2.2/127.0.0.1/localhost만 허용. 모바일 CAMP:ON을 연결하려면 HTTPS 서버가 필요하다.
- 원본 서버는 인증 없는 로컬 공모전 데모다. 공개 인터넷에 그대로 노출하지 않는다.
- OPENAI_API_KEY가 없으면 원본 규칙 기반 구매 요청 추출을 사용한다. 이번 통합은 AI 키를 설정하거나 유료 API를 호출하지 않는다.
- [Android 네트워크 보안 구성](https://developer.android.com/privacy-and-security/security-config) 준수. 인터넷 일반 권한만 추가했다.

## 디자인

그룹 이동·브리핑·할 일과 도착 후 EV 캠핑 화면은 같은 Pretendard, 라이트 배경, 흰색 플랫 카드, 블루 강조, 56dp 이상 주요 터치 영역을 사용한다. 원본 APK 내부 지도 화면의 스타일 수정은 Android 소스 확보 후 진행한다.

## 확인

서버 독립 회귀: `node --test services/group-sync/server.test.mjs`.
테스트는 임시 데이터와 18787 포트만 사용하며 실제 그룹을 초기화하지 않는다.
