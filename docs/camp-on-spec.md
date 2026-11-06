# CAMP:ON — 공모전 시연 명세

2026-11-06. 사용자가 제공한 01 통합 기능명세, 02 모드별 제어, 03 공통 처리 기준, 04 차량 와이어프레임 PDF 반영.
후속 지시에 따라 개인 공모전용 가정·더미 데이터 허용. 빌드·테스트·시각 검증은 요청으로 생략한다.

## 화면과 기능

- 캠핑 현황: COOK / RELAX / SLEEP, 적용 전 변경 내용과 예상 소비, 차량 내부 이미지, 공조·조명·시트 조절, 항목별 처리 결과, 에너지 배분.
- 에너지 계획: 종료 예정, 남은 시간, 장비 W·h·수량, 귀가 목적지·거리·전비, 사용 가능 용량·보호 예비량·기본 부하. 입력 자동 보존 및 명시적 저장.
- 에너지 상세: 실제 수신 이력의 실선/점, 미래 예상의 점선. 수신 공백은 연결하지 않는다. 더미 수신 이력에는 시연 출처 표시.
- 종료·귀가: 사용자 확인 후 조건부 복원, 항목별 결과, 출발 체크, 종료 예상 잔여·도착 예상·추가 충전량, CAMP FLOW용 요약 준비·공유.
- SLEEP 최소화: 배터리·종료 시각·경고·화면 열기 유지. 차량 기능 자동 차단 없음.
- 사용자 프리셋: 이름 + 현재 확인된 차량 값 저장·불러오기·삭제. 선택과 실제 적용 분리.

## 실행 방식

기본은 공모전 시연 모드다. 상단에 시연 표시 한 곳을 두며, 앱에 반복 면책 문구를 넣지 않는다.
DemoVehicleSource가 일반적인 SUV를 가정한다: SOC 75%, 실내 25°C, 조명·공조·시트 지원.
계획 초기값: 사용 가능 용량 110.3 kWh, 귀가 150 km, 전비 5 km/kWh, 예비 11 kWh, 종료 8시간 후.
냉장고 60 W / 6 h / 1대와 외부 조명 30 W / 6 h / 1대.
시연 상황: 일반, 에너지 부족(SOC 30%), 일부 실패(조명), 주행 중, 수신 지연, 연결 끊김.

실제 Pleos 연결은 설정에서 명시적으로 선택한다.
실패를 더미 데이터로 대체하지 않는다. 시연 세션(camp_on_demo_v3)과 실제 세션(camp_on_v2), 시연 차량 값은 별도로 저장한다.
활성 캠핑 또는 미완료 복원이 있으면 연결 모드 전환을 막는다.

## 디자인

차콜 불투명 표면, 얇은 윤곽선, 블루그레이 강조, 경고만 웜 앰버.
상단 상태·3개 탭, 넓은 화면에서는 환경/에너지 2열, 좁은 화면에서는 1열.
터치 영역 56dp 이상, 시스템 safe drawing inset, 스크롤·줄바꿈.
Pretendard Regular / Medium / ExtraBold 유지. 새 폰트나 UI 의존성 없음.
CampDesign.kt에 색·타입·패널·버튼 정의.

생성 이미지:
- app/src/main/res/drawable-nodpi/cabin_clay.png: 전면 실내 클레이 렌더.
- app/src/main/res/drawable-nodpi/cabin_top.png: 3열 SUV 탑뷰, 시트 조절 화면.
- 생성 도구·프롬프트: image-assets.md.

## 제어·복원

- 읽기/쓰기 capability 분리, 쓰기 범위·증분 적용.
- 주차·주행 규제·데이터 최신성이 불명확하면 차단. 매 쓰기 직전 주차 재확인.
- 현재값 조회 → 시작값/요청값 저장 → SDK 쓰기 → 최대 8회 재조회.
- IPC 전 기록은 IO에서 commit. 중복 명령 차단. 실패·프로세스 종료 시 불확실한 요청 기록 유지.
- 일부 실패 때 적용 완료 모드를 바꾸지 않는다. 에너지 계산은 선택 모드 대신 확인된 차량 설정 사용.
- 복원: 현재값이 마지막 앱 요청값과 일치할 때만 시작값으로 복원. 이미 시작값이면 복원 완료 처리.
- 직접 바뀐 값, 읽기 실패, 복원 실패는 기록 유지. 직접 확인 완료는 별도 사용자 확인.
- 공조 전원 복원은 ON 먼저, OFF 마지막.
- 종료·주행·프로세스 종료에 의한 자동 물리 조작 없음. 종료·복원은 명시적 사용자 확인 후만 실행.
- 출처와 항목별 수신 시각 보존. 전체 30초/주차 10초 기준. 관측 이력은 최대 720개.

## 에너지

현재 kWh = 사용 가능 용량 × SOC / 100.
귀가 kWh = km / (km/kWh).
캠핑 가용 = max(0, 현재 − 귀가 − 예비).
장비 배터리 소비 = W / 1000 × 남은 h × 수량 × 사용률 / 0.9.
사용 시간은 종료 예정까지 제한하며 활성 세션의 경과 시간을 제외한다.
기본 부하 = 입력 kW × 남은 h. 조명·시트 등 일반 보조 부하는 이 계수에 포함한다.
공조 계획 계수 = 전원 OFF면 0, ON이면 0.24 + |실내−설정 온도| × 0.08 + 풍량 × 0.05 kW.
이는 공모전용 가정이며 계측값이 아니다. 지원 공조 조회가 불가능하면 계산 불가로 표시한다.
합계 = 외부 장비 + 공조 + 기본 부하. 모드 계수와 현재 공조 소비를 중복 합산하지 않는다.
계획 후 여유는 가용−합계로 음수도 표시. 가용량만 0 이상으로 제한한다.
귀가 자체 부족, 예비 포함 현재 확보 부족, 계획 지속 시 부족을 분리한다.
미입력·비유한값·음수·0 전비·잘못된 수량은 계산 불가.
경고는 확인 버튼으로 해제하지 않고 유효한 데이터와 계산 결과로만 해제한다.

## Pleos 범위와 남은 연동

SDK 2.3.2 유지. 공조 전원·목표 온도·풍량·A/C·내기순환·앰비언트 밝기.
시트 열선·통풍 capability, 읽기/쓰기 및 시트 등받이 위치 읽기 연결.
실제 SDK의 시트 위치는 이동 명령과 현재 위치 조회 API만 확인되어, 절대 위치 설정/안전한 복원은 미지원으로 둔다.
시연에서는 가상 시트 위치를 편집·복원할 수 있다.
CAR_SEATS / CONTROL_CAR_SEATS는 SDK 소스의 공식 권한 선언을 따른다.
실제 공조 SDK 쓰기 반영 및 DDS bridge 문제는 해결 확인되지 않았다. 시연 성공과 구분한다.

CAMP FLOW의 공식 패키지/Intent 규격이 제공되지 않아 목적지·귀가 조건·출발 예상 잔여·부족량·수신 시각·출처·조건 버전을 요약해 Android Sharesheet로 전달한다.
공유 요청과 실제 경로 설정 완료는 구분하며, 실패 시 요약을 보존한다. 실제 내비 경로 설정 성공을 표시하지 않는다.

## 기존 환경 유지

단일 :app. AGP 9.5.0 / Gradle 9.6.0 / Compose plugin 2.2.10 / Compose BOM 2026.02.01.
minSdk 28 / compileSdk 37 / targetSdk 37. Vehicle SDK 2.3.2.

## 공식 참조

- https://document.pleos.ai/docs/pleos-only/vehicle-app-developer-guide/intro
- https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/vehicle/ai.pleos.playground.vehicle.api/h-v-a-c/entry
- https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/vehicle/ai.pleos.playground.vehicle.api/seat/entry
- https://document.pleos.ai/docs/pleos-only/app-test-guide/virtual-console-guide
- https://developer.android.com/develop/ui/compose/sharing/send
- https://developer.android.com/develop/ui/compose/graphics/draw/overview
