# CAMP:ON

Pleos Connect 차량 캠핑 앱. 차량 상태, 활동별 공조·조명, 에너지 계획, 철수 흐름을 하나의 로컬 세션으로 연결합니다.

## 실행
1. Android Studio에서 프로젝트를 열고 Gradle Sync.
2. Pleos Connect v2.1.0 AVD 실행.
3. app 구성을 실행.
4. 차량을 P에 둔 뒤 캠핑 시작.
5. 모드 설정에서 프리셋을 선택하고 적용.

공식 Vehicle SDK 2.3.2를 사용합니다. capability 조회 결과에 따라 제어 항목이 달라집니다.
주차·주행 규제 상태를 읽을 수 없으면 조작 버튼이 잠깁니다.

## 개발
기존 AGP 9.5.0, Gradle 9.6.0, Compose BOM 2026.02.01 사용.
API 37 컴파일 SDK와 Gradle daemon JDK 25 설정을 사용합니다. 앱 Java 호환 수준은 11입니다.

```sh
./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
./gradlew :app:connectedDebugAndroidTest
```

## 구성
- CampUi: 네 화면과 Pretendard 글래스 스타일
- CampSession: 공통 상태, 프리셋, 명령 결과, 조건부 복원
- VehicleSource: Pleos capability와 차량 읽기/쓰기
- ParkingGate: Android Car 주차·주행 규제 확인
- Energy: 단위가 명시된 독립 계산

[다른 PC에서 이어서 개발](docs/continue-on-another-pc.md) · [통합 명세](docs/camp-on-spec.md) · [실행 기록](docs/camp-on-verification.md)

Pretendard © Kil Hyung-jin, SIL Open Font License 1.1.
라이선스: app/src/main/assets/Pretendard-OFL.txt.
