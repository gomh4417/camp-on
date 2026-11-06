# 실행 기록 / 작업 재개 지점

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
