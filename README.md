# CAMP:ON

개인 공모전용 차량 캠핑 앱. COOK·RELAX·SLEEP, 공조·조명·시트, 에너지 계획과 종료·귀가를 한 세션으로 연결합니다.

## 실행

1. Android Studio에서 열고 Gradle Sync.
2. Android 기기/에뮬레이터에서 app 실행.
3. 기본 시연 모드에서 캠핑 시작 → 모드 선택 → 변경 확인 → 적용.
4. 차량 환경 조절과 현재 설정 저장, 에너지 계획, 종료·복원 흐름을 사용합니다.

상단 시연·설정에서 일반·부족·일부 실패·주행·수신 지연·연결 끊김 상황을 선택합니다.
캠핑 종료·복원 후 실제 Pleos 연결로 전환할 수 있습니다. 연결 실패를 시연 데이터로 대체하지 않습니다.

## 구현

- CampDesign: 차콜 표면·Pretendard·타입·버튼·패널.
- CampUi: 3개 주요 화면, 에너지 상세, 취침 최소화, 프리셋·제어 다이얼로그.
- CampSession: 공유 상태, 영속 프리셋/계획/결과, 조건부 복원, 수신 이력.
- DemoVehicleSource: 명시적 더미 차량과 상황 전환.
- VehicleSource / ParkingGate: Pleos SDK 및 Android Car 연결.
- Energy: 단위·수량·남은 소비·귀가 부족 계산.
- drawable-nodpi: AI 생성 전면 실내·시트 탑뷰 이미지.

단일 app, 기존 Kotlin/Compose/SDK 버전 유지.
기본 부하·공조 계수는 계획용 가정입니다. 실제 소비 계측이 아닙니다.
CAMP FLOW 연동 규격은 미제공이며, 준비 요약과 시스템 공유까지만 구현했습니다.

## 이번 변경의 실행 상태

사용자 요청으로 빌드·테스트·최종 시각 검증을 생략했습니다.
실제 Pleos 공조 쓰기/DDS bridge 문제의 해결을 확인한 상태는 아닙니다.

[명세](docs/camp-on-spec.md) · [작업 기록](docs/camp-on-verification.md) · [이미지 생성 정보](docs/image-assets.md) · [다른 PC](docs/continue-on-another-pc.md)

Pretendard © Kil Hyung-jin, SIL Open Font License 1.1.
라이선스: app/src/main/assets/Pretendard-OFL.txt.
