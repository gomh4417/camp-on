# 모바일 간격·프로필·귀가 흐름 검증

## 반영

- 모바일 입력폼, 차량·할 일 설명, 메시지 내부 세로 간격을 최소 8~10px로 조정했다.
- 닉네임 첫 유니코드 문자로 가입 미리보기·차량 목록·메시지·지도 핀 프로필을 통일했다. 공백은 제거하고 빈 이름은 `?`로 표시한다. 사진은 업로드하지 않는다.
- 휴식 모드 아이콘을 Google Material Symbols Outlined `chair_umbrella` 벡터로 교체했다. Apache 2.0 라이선스는 `app/src/main/assets/MATERIAL-ICONS-LICENSE.txt`에 포함했다.
- 종료·복원 성공 시 그룹 이동 화면으로 돌아가 완료된 출발 경로를 역순으로 재생한다. 귀가 완료 후 그룹 화면을 유지한다. 미해결 복원 항목은 기존 확인 절차를 유지한다.
- 앱·모바일의 정지 화면 버튼과 사용자 설정을 제거했다. 시스템 동작 줄이기는 계속 지원한다.

## 결과

- Android assembleDebug / assembleDebugAndroidTest 성공, 단위 테스트 19개 통과.
- npm test 11개 통과: 기존 그룹 동기화와 출발 흐름, 역방향 좌표·도착 단계, 닉네임 문자 처리 포함.
- 기기 테스트 4개 통과: GroupDepartureTest, KeyboardStabilityTest 2개, CampUxTest. 3명 HTTP 메시지, 출발→캠핑 화면, 저장 경로 재연결, 귀가 중복 방지·역방향 이동·완료, 종료 복원 후 그룹 화면, 키보드 입력을 확인했다.
- Playwright 320/390/430px × 가입·홈·설정·할 일·대화 = 15개 화면: 간격 부족·가로 넘침·브라우저 오류 0개. `mobile-spacing/results.json` 및 화면 PNG 저장.
- Android 캡처 `41-ux-home.png`에서 휴식 아이콘, `50-group-return.png`에서 역방향 이동·닉네임 핀을 확인했다.
- 공개 HTTPS 주소에서 갱신된 프로필 코드·간격 CSS·정지 버튼 제거·390px 화면을 확인했다. 운영 그룹에 테스트 참여자를 만들지 않았다.

## 범위

기기 흐름 테스트는 분리된 임시 서버, 주입된 경로, 테스트 차량 제어 구현을 사용했다. 실차 SDK·실제 GPS 이동을 재검증한 결과는 아니다. 귀가 애니메이션은 저장된 출발 경로를 뒤집는 시각적 표현이며, 역방향 도로의 실제 주행 가능 여부를 판단하는 길안내가 아니다. 관측 위치·기어·차량 제어 권한을 변경하지 않는다. 연결이 끊기거나 저장 경로가 없으면 안내하고 임의 경로를 만들지 않는다.

## 아이콘 원본

https://raw.githubusercontent.com/google/material-design-icons/master/symbols/web/chair_umbrella/materialsymbolsoutlined/chair_umbrella_24px.svg
