# CAMP:ON

## 제품
차량에서 머무는 캠핑 시간을 한 세션으로 관리한다. 사용자가 시작하며 네 화면이 같은 차량 상태와 에너지 계획을 공유한다.

- 캠핑 홈: 배터리, 에너지 배분, 현재 모드, 종료 시각, 차량 상태.
- 모드 설정: COOK / RELAX / MOVIE / SLEEP. 공조 전원·온도·풍량·A/C·내기순환·조명 프리셋을 저장하고 확인 후 적용.
- 에너지 계획: 사용 가능 용량, 귀가 거리, 전비, 예비량, 차량 소비와 외부 장비.
- 철수·귀가: 앱 변경값 복원, 출발 체크리스트, 추가 충전량.

## 디자인
차콜 배경, 반투명 그라디언트 패널, 얇은 테두리, 블루그레이 단일 강조색.
Pretendard 1.3.9 Regular / Medium / ExtraBold. SIL OFL은 앱 assets에 포함한다.
Compose dp와 시스템 inset을 사용한다. 주요 터치 영역 56dp 이상.
SLEEP은 앱 배경만 어둡게 한다. 차량 전체 화면 밝기는 변경하지 않는다.

## 환경
단일 :app. AGP 9.5.0 / Gradle 9.6.0 / Kotlin Compose plugin 2.2.10.
Compose BOM 2026.02.01. minSdk 28 / compileSdk 37 / targetSdk 37.
Pleos Connect v2.1.0 AVD: API 34, 2560×1440, 160dpi.
SDK: ai.pleos.playground:Vehicle:2.3.2. 기존 버전을 유지한다.

## 차량 연결
VehicleSource는 조회, 주차 상태, 개별 읽기/쓰기, 연결 해제를 분리한다.
PleosVehicleSource는 capability의 accessType과 좌석별 bounds를 먼저 읽는다.
현재 앱은 연결된 차량 응답만 표시한다. 실패 시 다른 데이터로 대체하지 않는다.

| 기능 | 연결 API |
|---|---|
| SOC | EvBattery.checkEvBatteryStateOfChargeCapabilities / getEvBatteryStateOfCharge |
| 충전 스위치 | checkEvChargeSwitchCapabilities / getEvChargeSwitchState |
| 실내 온도 | HVAC.checkHvacTemperatureCurrentCapabilities / getHvacTemperatureCurrent |
| 목표 온도 | checkHvacTemperatureCapabilities / getHvacTemperature / setHvacTemperature |
| 공조 전원 | checkHvacPowerCapabilities / getHvacPowerState / setHvacPower |
| 풍량 | checkHvacFanSpeedCapabilities / getHvacFanSpeed / setHvacFanSpeed |
| A/C | checkHvacAcCapabilities / getHvacAcState / setHvacAc |
| 내기순환 | checkHvacRecirculationCapabilities / getHvacRecirculationState / setHvacRecirculation |
| 조명 밝기 | Light.checkAmbientLightBrightnessCapabilities / getAmbientLightBrightness / setAmbientLightBrightness |
| 주차 | CarPropertyManager.GEAR_SELECTION == VehicleGear.GEAR_PARK |
| 주행 규제 | CarUxRestrictionsManager.currentCarUxRestrictions |

권한: pleos CAR_ENERGY, CAR_CLIMATE, CONTROL_CAR_CLIMATE, CAR_LIGHTS 및 Android CAR_POWERTRAIN.
android.car는 optional uses-library로 선언한다.
시트·창문·도어·V2L 쓰기, 내비게이션, 실시간 외부 전력 계측은 연결하지 않는다.
충전 스위치는 충전 전류 계측이 아니다.

## 명령과 복원
- 주차와 규제 해제 상태를 모두 확인한다. 알 수 없거나 오래되면 조작을 잠근다.
- 표시 중 차량 조회 5초, 주차 조회 2초. 상태 freshness 30초, 주차 10초. SDK 요청 timeout 6초.
- 각 쓰기 직전에도 주차를 확인한다.
- 현재값을 먼저 읽고 범위·증분을 적용한다. 쓰기 후 최대 8회 응답값 확인.
- 중복 요청 차단. 항목별 성공/실패/읽기 전용 상태를 보존한다.
- SharedPreferences에 시작값, 마지막 요청값, 결과, 프리셋, 장비, 체크리스트를 저장한다.
- IPC 전에 요청값을 기록하여 프로세스 종료 후에도 미완료 작업을 남긴다.
- 마지막 앱 요청값과 현재값이 일치할 때만 시작값을 복원한다. 수동 변경값은 유지한다.
- 복원 실패 항목은 삭제하지 않는다. 직접 복원 완료는 사용자가 선택한다.
- 주행 전환·앱 종료로 차량을 자동 조작하지 않는다.
- 저장소 v2는 이전 테스트 상태와 분리한다.

## 에너지
현재 kWh = 사용 가능 용량 × SOC / 100.
귀가 kWh = km / (km/kWh).
캠핑 가용 kWh = max(0, 현재 − 귀가 − 예비).
장비 kWh = W / 1000 × h × 사용률.
전체 소비 = 차량 기능 소비 + 장비 소비 / 0.9. 외부 장비에만 변환 효율 90% 적용.
귀가 자체 부족, 예비 포함 부족, 캠핑 계획 초과를 각각 계산한다.
누락값·비유한값·음수·전비 0·사용률 범위 오류는 계산하지 않는다.
용량·거리·전비·소비는 계획 입력값이다. SOC는 차량 응답이다.
모드별 kW는 편집 가능한 계획의 초기 계수이며 계측값이 아니다.
사용자가 차량 소비를 입력한 경우 모드 변경으로 덮어쓰지 않는다.
이력 그래프, 에너지 자동 차단, 실시간 남은 시간은 제공하지 않는다.

## 공식 자료
- https://document.pleos.ai/docs/connect/guide/docs-design/Introduction/intro
- https://document.pleos.ai/docs/connect/guide/docs-design/foundations/typography
- https://document.pleos.ai/docs/pleos-only/vehicle-app-developer-guide/intro
- https://document.pleos.ai/docs/pleos-only/vehicle-app-developer-guide/driving-restriction-development-guide
- https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/vehicle/ai.pleos.playground.vehicle.api/h-v-a-c/entry
- https://developer.android.com/training/cars/parked/automotive-os
- https://developer.android.com/reference/android/car/VehiclePropertyIds
- https://developer.android.com/develop/ui/compose/text/fonts
- https://github.com/orioncactus/pretendard

실행 결과는 camp-on-verification.md에 기록한다.
