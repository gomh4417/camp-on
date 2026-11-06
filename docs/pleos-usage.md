# Pleos 화면과 CAMP:ON 사용법

## 두 창
- Running Device: Pleos Android와 CAMP:ON이 실행되는 화면. 하단 공조 바도 여기에 있다.
- 웹 Virtual Console: 가상 차량 상태·입력 생성 도구. 앱 화면이 아니다.
- SDK 조회 성공과 콘솔 활성 상태는 다르다. 콘솔 비활성 중에도 이전 값이 조회될 수 있다.

## 온도 변경
1. 에뮬레이터 실행, `adb forward tcp:8088 tcp:8088`.
2. PC의 http://127.0.0.1:8088/ 탭을 활성 상태로 유지한다.
3. 홈 탑뷰의 운전석·동승석 +/-를 누른다. 별도 좌석 선택·적용·캠핑 시작 없이 해당 값만 전송한다.
4. ‘반영 대기’는 SDK 전송 후 실제값을 기다리는 상태다. 요청값으로 현재값을 덮지 않는다. 자동 재전송도 하지 않는다.
5. 공조 전원이 꺼졌으면 공조 조절 화면에서 켠다. 웹 Climate → Target Temperature와 같은 좌석을 비교한다.
6. 실내 온도는 센서값이며 목표 온도 변경과 동시에 바뀌지 않는다.
7. 변경 전 값은 저장되며 종료·귀가 화면에서 복원할 수 있다.

콘솔이 비활성이면 탭을 활성화/새로고침하고 ‘상태 확인’을 누른다. 실제값이 늦게 도착해도 정기 조회에서 결과를 갱신한다. 이전 요청이 대기 중이면 중복 제어·복원을 보류한다.

## 주행
- 공조의 앱 내부 P 전용 제한을 해제했다. 알려진 기어·최신값·Pleos 조작 허용이 모두 필요하다.
- 시스템의 주행 중 화면 제한은 우회하지 않는다. 차량/지역별 제한이나 DO 정책으로 차단될 수 있다.
- 시트·조명·모드 일괄 적용·복원은 P에서만 가능하다.
- Motion → Gear Selection에서 D 선택만으로 가속되는 것은 아니다. 콘솔이 지원하는 주행 입력이 필요하다.
- 주변 차량 애니메이션과 내 차량 속도는 별개다. 정확한 콘솔 키보드 매핑 및 실제 주행 중 UI 허용은 미확인이다.

## 공식 문서
- [Virtual Console](https://document.pleos.ai/docs/pleos-only/app-test-guide/virtual-console-guide)
- [주행 제한 정책](https://document.pleos.ai/docs/pleos-only/vehicle-app-planning-guide/driving-restiction-policy)
- [주행 제한 개발](https://document.pleos.ai/docs/pleos-only/vehicle-app-developer-guide/driving-restriction-development-guide)
