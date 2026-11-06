# 실행 기록 / 작업 재개 지점

## 2026-11-06 — MVP_C 화면 구성 복원

- 사용자 첨부 3개 화면을 우선 기준으로 재작성. 홈/그룹 차량/브리핑 분리, 원본 텐트·프로필 PNG 재사용. 공통 카드·배경·보조 버튼·여백을 캠핑/에너지/귀가/모달까지 적용. AGENTS 디자인 규칙 갱신.
- 홈 우측 700dp OpenStreetMap 강릉 주변 지도 추가. 서버에 좌표가 없어 실시간 차량 마커는 원본 앱에 유지. 로딩/오류/재시도 처리, 외부 HTTPS와 WebView 수명 관리.
- assembleDebug 성공, JVM 8개 통과. Pleos 직접 APK 설치 후 홈 지도·원본 자산·차량 상세 표시 확인. IDE deploy만으로 최신 코드 반영이 불명확해 직접 APK 재설치로 확인했다.
- 실제 차량 제어·서버 계약 변경 없음. 좁은 창·큰 글자·오프라인 지도 오류·음성 실기는 별도 검증하지 않음.


## 2026-11-06 — MVP_C 그룹 이동 통합

- origin/Part_C의 zip/MVP_C.zip 확인. Android 소스 없이 차량·모바일 APK 및 서버만 제공됨. 원본 서버 REST 계약을 유지해 services/group-sync에 취합.
- 함께 이동 탭: 차량 조회, 브리핑·요청·할 일·메모 전송. 라이트·플랫 공통 디자인. 조회 차량과 내 역할 구분, 실패 시 낙관적 완료 표시 없음.
- 도착 + 로컬 주차 확인 후 캠핑장/할 일 연결. 귀가 목적지와 캠핑장 분리. 차량 설정은 자동 변경하지 않음.
- assembleDebug 성공, JVM 8개 통과. Node 독립 API 테스트 통과: 3대 상태, 요청 중복, PATCH 완료, 404, 메모, 도착 갱신, 재시작 보존. 실제 그룹 데이터 초기화 없음.
- Pleos 배포 후 그룹 서버 수신·3대 현황·브리핑 렌더 확인. 원본 차량 APK 설치 및 명시적 실행 확인. 원본 APK가 기본 Pleos 내비게이션을 전면에 여는 동작 관찰. 원본의 차량 상태 POST도 서버에서 확인.
- 할 일 체크 행 접근성 라벨/터치 영역과 화면 하단 출처 표시 보완.
- Graphify 로컬 AST 갱신. JourneySession Kotlin 프로퍼티 일부 파싱 경고는 빌드 실패와 구분.
- 모바일 실기 동시 접속, 음성 인식, 실제 3대 차량, 도착 전환 UI 전체 흐름은 이번에 확인하지 않음. Android 원본 소스 확보 후 지도 화면 자체 병합 필요.


## 2026-11-06 — 라이트·플랫 테마 / 좌석별 분리 레이어

- 사용자 최신 레퍼런스 반영. 차콜/글래스 지침을 라이트·플랫·친근한 스타일로 교체. Pretendard 유지. 홈·계획·귀가·모달은 공통 lightColorScheme 사용.
- 내장 image_gen으로 투명 차체/한 좌석 PNG 생성, 프로젝트 리소스에 복사. 좌석마다 독립 Compose 선택 레이어, 3열 7석 안내 형상. 실제 차량 형상 측정 모델은 아님.
- CabinMap: 차량 높이 600~820dp 범위의 큰 평면도. 선택 색·윤곽·체크, RadioButton 접근성, 최소 56dp 터치 영역. 선택만으로 차량에 명령하지 않음.
- 선택 영역은 SDK area 토큰과 정확히 대응. 미지원 영역은 안내하며 가상값을 만들지 않음. 홈은 공조 온도와 시트 열선/통풍, 시트 화면은 등받이 등 지원 기능 연결.
- 온도 Slider는 드래그 종료 시 한 번 전달. 실제값/드래그 미리보기/반영 대기 분리. 기존 범위 정규화·중복 차단·복원 로직 유지.
- Android 공식 Slider/RadioButton 문서 확인. 새 의존성·권한 없음.
- assembleDebug 성공, JVM 8개 통과. Pleos에 배포, 홈 및 에너지 계획 라이트 화면 확인.
- 운전석→동승석 탭 시 선택 표시/제어 대상 전환 확인. 선택만으로 운전석 24°C·동승석 22°C 유지.
- 동승석 슬라이더 드래그 후 앱 '목표 온도 · 적용 확인', 22.5°C 및 시스템 하단 공조 22.5°C 일치 확인. 운전석은 24°C 유지.
- 스크롤 경계의 이미지 진입 카드 접근성 이름 추가. 최종 UI는 홈에 열어둠.
- 좁은 창/큰 글자/TalkBack/실제 주행은 이번 실행에서 별도 검증하지 않음. 기기 회귀 5개는 이전 커밋 결과이며 이번에는 재실행하지 않음.


## 2026-11-06 — 지연 반영 수정 / 탑뷰 직접 조절

- 기존 실패 원인: SDK 전송 접수 후 8회 × 250ms만 기다리고 '차량 반영 확인 필요' 예외 처리. 로그에서 이전 운전석 23.5°C가 약 50초 뒤 조회됨.
- 접수·반영 대기·실제 요청값 수신을 분리. 대기 요청을 자동 재전송하지 않으며, 5초 정기 조회에서 늦은 값도 확인한다.
- 앱 재시작 후 미확정 요청·원래 값·마지막 요청을 유지한다. 이전 요청 대기 중 복원은 보류. 복원 요청 지연도 재전송하지 않고 원래 값 수신을 확인한다.
- 홈과 제어창 탑뷰 양옆에 좌우 온도 +/- 배치. 좌석 선택/별도 적용 단계 제거. 개별 제어는 캠핑 시작 없이 가능하며 기록은 같은 저장소에 유지한다.
- 개별 공조는 알려진 비-P 기어도 허용하되 플랫폼 제한은 유지한다. 시트/조명/모드/복원은 P. 주행 중 시스템 화면 허용은 미확인, DO를 임의 선언하지 않음.
- 공식 Pleos 주행 제한 정책/개발 안내 및 Android CarUxRestrictions 재검토. 기존 API·의존성·폰트 유지.
- assembleDebug / assembleDebugAndroidTest / JVM 8개 성공. installDebug/installDebugAndroidTest 후 user 10에서 CampSessionTest 5개 통과. 앱 데이터 삭제 없이 실행.
- 추가 회귀: 지연 요청/재시작/중복 차단/성급한 복원 방지/복원 지연 수신, 비-P 공조·플랫폼 제한·기어 unknown·stale·시트 차단.
- 실제 홈 운전석 + 클릭으로 23.5→24.0°C 전송. 반영 대기 화면을 거쳐 앱·Running Device 하단 바·웹 /vehicle/state의 driver.temp_c=24.0 일치 확인. 웹 browser.active=true, hvac.emulator_active=true, source=subscription.
- 동승석은 22.0°C 유지. 사용자 검토용으로 앱 홈 탑뷰를 열어둠. 시각 확인 후 탑뷰 크기·대기 상태 문구를 보완하고 재빌드/배포 성공. 실제 주행/모든 옵션은 재검증하지 않음.
- Graphify 로컬 AST 갱신. CampSession/VehicleSource 일부 Kotlin 구문 부분 추출 경고는 기존 파서 한계이며 빌드 결과와 구분.


## 2026-11-06 — 이미지 중심 제어 UX / 사용 안내

- 홈: 차량 이미지와 공조·조명·시트 이미지 진입 카드 우선, 모드 선택은 아래 배치.
- 차량 환경: 전체 작업 화면, 넓은 창은 이미지/좌석 선택 + 제어 카드 2열, 좁은 창은 세로 배치. 적용 영역은 스크롤 밖 고정.
- 카드의 현재값/변경 예정값 분리. +/−로는 쓰지 않고 ‘차량에 적용’ 버튼으로 명시적 전송. 세션 전에는 ‘캠핑 시작·적용’.
- 성공 시 편집값 해제, 실패 항목 유지. 새 편집 시 이전 처리 결과 제거. 중복 개별 적용 회귀 assertion 추가.
- ‘차량 연결 정상’을 ‘SDK 수신 중’으로 수정: SDK 조회와 웹 콘솔 연결은 별도 상태.
- 앱 내부 사용법 및 docs/pleos-usage.md 추가. 디자인 규칙 AGENTS.md 수정.
- assembleDebug / assembleDebugAndroidTest / JVM 8개 통과. 기기 회귀 테스트는 이번에 다시 실행하지 않음.
- Pleos에서 홈·공조·시트 화면 확인. 사용자 운전석 편집값 23.5°C 보존, 실제 22°C와 고정 적용 버튼 표시 확인.
- 조사 시 browser.active=false, emulator_active=false, Park, speed=0, accelerator=0. 콘솔 비활성은 실제 미반영의 별도 원인.
- Chrome UI 접근은 현재 URL 신뢰도 정책 검사로 차단됨. 브라우저 조작 중단, 콘솔 새로고침 요청. 이번 UI 변경 후 실제 쓰기/주행 재확인은 대기 상태.
- 아래 이전 실제 쓰기 성공 결과와 구분한다.


## 2026-11-06 — 후속 실제 Pleos SDK 제어

최신 기록. 아래의 과거 '미해결/검증 생략' 기록은 당시 상태다.

- 실제 Pleos 모드 기본값. 기존 시연 선택 및 분리 저장소는 유지.
- SDK 2.3.2 공식 소스와 Pleos/Android 문서 재검토. 자동 공조, 좌우 온도 연동, 공기 청정, 바람 방향, 성에 제거, 실내등, 독서등 추가.
- 등받이 위치 조회+이동 capability 조합 시 피드백 이동. 실제 위치 변경은 명시적 편집만 대상. 모든 시트/옵션의 기기 동작을 확인한 것은 아니다.
- 개별 편집은 편집 항목만 쓰기. 전체 적용 후 최종 조회값 재검사. 지원 선택값 검증.
- CONTROL_CAR_LIGHTS, CAR_INFO 권한 추가. Android INFO_EV_BATTERY_CAPACITY / 1000으로 공칭 사용 가능 kWh 조회. 사용자 보정 우선, 신차 기준을 표시.
- 기존 minimized 프로퍼티 setter와 setMinimized 함수의 JVM 이름 충돌 수정.

환경 원인/실기 확인:
- Pleos_Connect_v2.1.0, emulator-5554에 설치·실행.
- 브라우저에서 http://127.0.0.1:8088/ 을 실제 열자 browser.active=true, emulator_active=true로 전환.
- 이전 비활성 상태에서는 HVAC_TEMPERATURE_SET이 PropertyNotAvailableException으로 실패했다.
- 활성화 뒤 SDK 조회 온도 22°C. 앱 RELAX 적용 후 양쪽 23°C, 풍량 2단, 조명 40%, 적용 완료 확인.
- /vehicle/state에서 fan_speed=2, ambient brightness_pct=40, source=subscription 확인. HTTP는 관측만 사용했다.
- 종료·복원 뒤 앱에서 온도 22°C, 조명 70%, 캠핑 준비 확인. 조명 70은 콘솔 활성화 전 세션 진입값.
- 최종 화면에서 실제 SOC 100%, 실내 19.1°C, 에너지 계획 수치 표시. 콘솔과 앱을 켜둠.
- assembleDebug / testDebugUnitTest / connectedDebugAndroidTest 성공. JVM 8개, 기기 4개 통과.
- 개별 편집이 다른 모드 기본값을 쓰지 않는 회귀 테스트 추가.
- Graphify 로컬 AST 갱신. 부분 Kotlin 파싱 한계는 유지.

남은 범위:
- 추가된 모든 옵션/시트 이동의 실기 조합 검증, 강제 종료 중 시트 STOP 전달 보장은 미확인.
- 공칭 용량은 노화 반영 현재 용량이 아니다. 배터리 SOC·실내 온도는 읽기 전용.
- Virtual Console 탭을 닫으면 시뮬레이션이 다시 비활성화될 수 있다.
- CAMP FLOW 실제 Intent 계약은 여전히 미제공.


## 2026-11-06 — 새 PDF 명세 / 개인 공모전 시연

후속 사용자 지시: 더미 데이터와 일반적 가정 허용. 별도 검증 불필요.
따라서 이번 변경의 빌드·단위/기기 테스트·최종 시각 검증은 실행하지 않았다.
아래 과거 성공 기록은 이번 변경의 성공 근거가 아니다.

구현:
- 기본 시연 모드 + 실제 Pleos 모드 분리, 저장소 분리. 실패 시 자동 전환 없음.
- 3개 활동 모드/3개 주요 화면, 차량 이미지 중심의 차콜 UI, Pretendard 유지.
- 공조/조명/시트 조절, 이름 있는 사용자 프리셋, 확인 후 적용/부분 실패 표시.
- 장비 수량·남은 시간·공조/기본 부하·귀가 조건에 따른 계산, 현재 부족과 미래 부족 분리.
- SOC 수신 이력/예상 그래프, SLEEP 최소화와 경고 유지.
- 종료/조건부 복원, 출발 확인, CAMP FLOW용 요약 보존 및 공유 요청.
- 두 차량 이미지 생성·리소스 삽입. 내장 image_gen 사용, Imagen 사용 아님.
- SDK 시트 열선/통풍 연결, 시트 위치는 실제 SDK에서 조회 전용, 시연에서 가상 제어.
- IPC 전 변경 기록 commit, 복원 전원 순서 개선, 실제 값으로 소비 계산.
- 기존 테스트 기대값은 변경된 부분 적용 정책/기본 장비 수에 맞춰 수정. 테스트 실행 없음.

환경 조사:
- Windows Pleos_Connect_v2.1.0 AVD 실행 및 ADB 연결.
- 콘솔 포트 전달 후 /vehicle/state에서 DDS bridge 비활성 확인.
- 브라우저 도구가 URL을 확인하지 못해 콘솔 UI 작업 중단.
- 실제 공조 SDK 쓰기 반영 문제는 해결 확인되지 않았다. 시연 구현과 혼동하지 않는다.
- CAMP FLOW 실제 패키지/Intent 계약은 미제공. 경로 설정 완료를 주장하지 않는다.
- Graphify 로컬 AST 갱신 명령 성공. CampSession/VehicleSource 일부 Kotlin 구문은 부분 추출 경고. 빌드·런타임 검증이 아니다.

## 이전 기록 — 이번 변경 이전

2026-11-06, 사용자 요청으로 추가 개발·검증을 중단하고 현재 상태를 저장했다.

## 완료한 확인
- Gradle Sync 성공.
- assembleDebug, assembleDebugAndroidTest, testDebugUnitTest, lintDebug 함께 실행 성공.
- JVM 테스트 8개 통과.
- Pleos API 34에서 instrumentation 테스트 3개 통과.
- APK 설치·앱 실행 성공.
- 새 글래스 UI, Pretendard 세 굵기, 홈·모드 화면 표시 확인.
- SDK SOC 100%, 충전 스위치 false, 실내 온도 19.1°C, 풍량·공조 전원·A/C·내기순환·조명 조회 응답 확인.
- Android Car 주차 P 및 주행 규제 해제 상태 확인.
- 중복 명령, 부분 실패, 저장 후 재시작, 수동 변경 복원 보호, 연결 실패, 오래된 데이터는 테스트에서 확인했다.

## 미완료 / 다음 작업
1. Pleos 차량 쓰기 반영 문제를 먼저 해결한다.
   - SDK capability와 호출 시그니처 확인, 코드 연결 완료.
   - 공조 전원·A/C·내기순환 쓰기 후 조회값이 유지되었다.
   - 목표 온도 조회: PropertyNotAvailableException, HVAC_TEMPERATURE_SET, area 0x1 / 0x4.
   - 풍량 쓰기: PropertyAsyncError, HVAC_FAN_SPEED, area 5, error code 2.
   - 앱은 이를 전체 성공으로 표시하지 않는다.
2. Virtual Console을 켜고 DDS bridge 상태를 확인한다.
   - adb forward tcp:8088 tcp:8088
   - 브라우저 http://127.0.0.1:8088/
   - 공식 콘솔 /vehicle/state 응답: bridge_ready=false, emulator_active=false, browser.active=false.
   - hvac.dds_mode=subscribe_hvac_base.
   - 콘솔 HTML에는 /browser/session heartbeat가 1초마다 호출되며 browser timeout은 3초다.
   - 콘솔이 실제 열린 상태에서 SDK 쓰기 반영을 비교해야 한다. 이 원인은 아직 확정하지 않았다.
   - 브라우저 자동화는 사용자 브라우저 변경과 충돌하여 중단했다.
   - 앱에 콘솔 HTTP 우회 제어를 추가하지 않았다. 현재 앱은 SDK만 호출한다.
3. 모드 적용과 복원을 실제 에뮬레이터에서 끝까지 확인한다.
4. 에너지·철수 화면의 최종 시각 확인, 주행 전환 잠금 확인.
5. 필요하면 SDK 오류 전문 대신 짧은 오류 문구와 로그 분리, 긴 좌석명 압축.
6. 모든 수정 완료 후 최종 검증을 한 단계로 수행한다. 개발 중 반복 빌드는 피한다.

## 기존 상태
에뮬레이터에 캠핑 세션과 미완료 복원 기록이 남아 있다. 새 PC에는 이 앱 데이터가 옮겨지지 않는다.
사용자 요청에 따라 남은 검증·기능 수정은 진행하지 않았다.
Graphify 로컬 AST 갱신 완료: 144 nodes / 236 edges. CampSession·VehicleSource 일부 Kotlin 문법은 부분 분석. 실제 컴파일 성공.

## 참고
- https://document.pleos.ai/docs/connect/guide/getting-started/application-development/use-vehicle-emulator
- https://document.pleos.ai/docs/pleos-only/app-test-guide/virtual-console-guide
- https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/vehicle/ai.pleos.playground.vehicle.api/h-v-a-c/entry
