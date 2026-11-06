# Pleos 화면과 CAMP:ON 사용법

## 두 창의 역할

- Android Studio Running Device: Pleos Android 에뮬레이터 화면. CAMP:ON, 기본 차량 UI, 하단 공조 바가 실행된다.
- PC 웹 Virtual Console: 가상 차량 상태와 입력을 생성하는 테스트 도구. 앱 화면 자체가 아니다.
- 앱 → Pleos SDK → 차량 서비스/시뮬레이터 → 실제 상태 재조회가 연결되어야 양쪽 표시가 바뀐다.
- SDK가 조회 응답을 주는 것과 콘솔의 browser.active/emulator_active는 다르다. 콘솔 비활성 상태에서도 기존 값이 조회될 수 있다.

## 온도 변경

1. Pleos 에뮬레이터 실행, `adb forward tcp:8088 tcp:8088`.
2. PC에서 http://127.0.0.1:8088/ 탭을 활성화하고 필요하면 새로고침한다. 백그라운드 탭 절전 등으로 heartbeat가 끊기면 콘솔은 비활성화될 수 있다.
3. 주차 P, 공조 전원 ON 확인.
4. CAMP:ON 홈의 공조 이미지 카드 → 운전석 또는 동승석 선택.
5. + / −는 변경 예정값 편집이다. 사진 위 배지는 계속 차량 현재값을 표시한다.
6. 화면 하단 고정 ‘차량에 적용’. 캠핑 시작 전에는 ‘캠핑 시작·적용’으로 진입값 저장과 명시적 적용을 진행한다.
7. 적용 확인, 현재값, 하단 차량 공조 바의 같은 좌석을 비교한다. 웹에서는 Climate → Target Temperature → 같은 좌석.
8. 실내 온도는 목표 온도와 다른 센서값이며 동시에 변하지 않는다.

실패한 항목은 편집값과 결과가 남는다. 성공 항목은 변경 예정값을 해제하고 재조회된 현재값을 표시한다. 다른 좌석/모드 기본값을 함께 쓰지 않는다.

## 내 차량이 움직이지 않을 때

- 이번 조사 시 기어 Park, 속도 0, 가속 0, 입력 비활성, browser.active=false였다.
- Motion → Gear Selection에서 P/R/N/D 상태를 설정한다. D 선택만으로 자동 가속되는 것은 아니다.
- 지원되는 콘솔 가속/주행 입력을 사용해야 한다. 이번 세션에서는 브라우저 URL 정책 검사로 직접 콘솔 UI를 읽거나 조작할 수 없어 정확한 키보드 단축키나 입력 장치 매핑은 확인하지 못했다. 임의의 키를 안내하지 않는다.
- 주변 교통 애니메이션은 내 차량 데이터 연결의 증거가 아니다. 속도·기어·가속 입력 상태로 확인한다.
- 주행 상태에서 CAMP:ON 물리 제어는 잠긴다. 다시 P로 돌아와 조절한다.
- 테스트 도구 주행 조작을 캠핑 앱 기능으로 추가하지 않았다.

## 이번 확인 범위

새 홈/공조 제어창은 Pleos Running Device에서 표시를 확인했다. 현재 22°C와 기존 변경 예정 23.5°C, 고정 적용 버튼을 구분해 표시한다.
이번 변경 뒤 실제 SDK 쓰기 재확인은 콘솔 재활성화 대기 상태다. 이전 버전의 성공 기록을 이번 확인 결과로 사용하지 않는다.

## 공식 문서

- [Virtual Console 셋업](https://document.pleos.ai/docs/connect/guide/getting-started/application-development/use-vehicle-emulator)
- [도메인별 조작](https://document.pleos.ai/docs/pleos-only/app-test-guide/virtual-console-guide)
