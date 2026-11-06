# CAMP:ON

개인 공모전용 차량 캠핑 앱. 함께 이동·그룹 브리핑·구매 할 일부터 도착 후 COOK·RELAX·SLEEP, 공조·조명·시트, 에너지 계획과 귀가까지 연결합니다.

[MVP_C 통합 범위·서버 실행·사용법](docs/group-journey.md). 원본 Android 소스가 없어 지도·음성은 원본 APK를 연결해 엽니다.

## 실행

1. Android Studio에서 열고 Gradle Sync.
2. Android 기기/에뮬레이터에서 app 실행.
3. 기본 실제 Pleos 모드에서 캠핑 시작 → 모드 선택 → 변경 확인 → 적용.
4. 큰 차량 평면도에서 좌석을 선택하고 온도 슬라이더·+/−로 조절합니다. 슬라이더는 손을 뗄 때 한 번 전송합니다. 좌석 선택 자체는 차량을 변경하지 않습니다. 실제 수신값·반영 대기는 구분합니다.
5. 상단 ‘사용법’ 또는 [Pleos 사용 안내](docs/pleos-usage.md)에서 두 창의 차이와 반영 확인 순서를 확인합니다.

Pleos 에뮬레이터는 `adb forward tcp:8088 tcp:8088` 후 브라우저에서 http://127.0.0.1:8088/ 을 열어둡니다. 콘솔이 닫혀 있으면 차량 DDS 시뮬레이션이 비활성화되어 공조 쓰기가 반영되지 않을 수 있습니다.
시연 모드는 설정에서 선택합니다. 활성 세션/미완료 복원 중에는 전환하지 않으며, 연결 실패를 시연 데이터로 대체하지 않습니다.

## 구현

- CampDesign: 라이트 테마·플랫한 흰 카드·블루 포인트·Pretendard.
- JourneySession / JourneyUi: MVP_C 서버 호환 그룹 현황·메모·요청·할 일, 도착 후 캠핑 연결.
- services/group-sync: Part_C 원본 호환 로컬 공유 서버.
- CampUi: 함께 이동, 이미지 카드 중심 홈, 에너지 계획·상세, 종료·귀가, 취침 최소화.
- CabinMap / VehicleEnvironment: 큰 차체 + 좌석별 독립 레이어, 선택 표시와 연결된 온도 슬라이더·제어. 전송·반영 대기·현재값 분리.
- PleosGuide: 앱 내부 실행·연결·온도 변경 안내.
- CampSession: 공유 상태, 영속 프리셋/계획/결과, 조건부 복원, 수신 이력.
- DemoVehicleSource: 명시적 더미 차량과 상황 전환.
- VehicleSource / ParkingGate: Pleos SDK 및 Android Car 연결.
- Energy: 단위·수량·남은 소비·귀가 부족 계산.
- drawable-nodpi: AI 생성 전면 실내·시트 탑뷰 이미지.

단일 app, 기존 Kotlin/Compose/SDK 버전 유지.
기본 부하·공조 계수는 계획용 가정입니다. 실제 소비 계측이 아닙니다.
CAMP FLOW 연동 규격은 미제공이며, 준비 요약과 시스템 공유까지만 구현했습니다.

## 이번 변경의 실행 상태

2026-11-06 후속 실제 연결 개발: assembleDebug 성공, JVM 8개·기기 4개 테스트 통과. Pleos에서 실행 중.
Virtual Console 활성화 후 SDK 온도 22→23°C, 풍량 3→2단, 조명 100→40% 반영 및 앱 변경값 복원 확인.
자동 공조·좌우 연동·공기 청정·바람 방향·성에 제거·실내등·독서등·시트 이동은 SDK capability 기반으로 제공하며, 모든 조합의 실기 반영을 검증한 것은 아닙니다.
차량 공칭 사용 가능 용량(신차 기준)을 Wh→kWh로 변환하여 계획에 사용합니다. 사용자 용량 보정을 우선하며 SOC·실내 온도는 조회 전용입니다.

[명세](docs/camp-on-spec.md) · [작업 기록](docs/camp-on-verification.md) · [이미지 생성 정보](docs/image-assets.md) · [다른 PC](docs/continue-on-another-pc.md)

Pretendard © Kil Hyung-jin, SIL Open Font License 1.1.
라이선스: app/src/main/assets/Pretendard-OFL.txt.
