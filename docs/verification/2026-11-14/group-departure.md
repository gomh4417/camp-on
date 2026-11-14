# 그룹 연결·이동 UX 검증

## 변경
- 공유 서버 주소 입력과 불필요한 참여 안내 삭제. 내부 JourneySession 서버 설정 사용.
- 연결창 #FEFEFE / tonalElevation 0 / 4dp 그림자. 새 그룹 만들기는 메인 파랑, 코드로 참여는 테두리 없는 파란 텍스트.
- 지도는 그룹 이동, 출발지/목적지/지도 중심 선택, 설정 완료로 정리. 시연·일시정지·계속 이동·끝내기·정지 화면·다음 위치·하단 안내 삭제.
- 설정 성공 + 유효한 그룹 연결 때만 함께 출발하기 활성화. 등록 참여자만 이동하며 마지막 핀 도착 후 캠핑 준비로 전환.
- 메시지/지도 확대 왕복 시 상태 복원. 중복 출발 방지, 연결 끊김/참여자 변경 시 설정 무효화.
- 앱 기어 표시는 캠핑 화면에서만 SDK 관측값 사용. Pleos 시스템 기어 UI는 변경하지 않음.
- 로고 터치 영역은 헤더와 같은 흰 배경. 그룹 메시지 직접 진입 및 전체 메시지 빈 상태 추가.

## 검증
- Android 빌드 및 단위 테스트 19개 통과.
- npm test 10개 통과: 초대/메시지/할 일/재시작 및 지도 준비/출발/도착/중복/복원/실패/동작 줄이기.
- Pleos emulator-5554, user 10: KeyboardStabilityTest 2개, CampUxTest 1개 통과.
- GroupDepartureTest: 별도 18789 서버·임시 저장소에서 3명 가입, 휴게소 메모 HTTP 수신 및 전체 메시지 표시, 출발 활성화, WebView 핀 이동, 마지막 도착→캠핑 준비 통과.
- 첫 그룹 테스트는 WebView mapReady와 native 초기 동기화 사이에 fixture를 주입해 실패했다. 초기 멤버 동기화까지 기다리도록 테스트 수정 후 통과.
- 그룹 테스트의 경로는 결정적 fixture이며 실제 OSRM 경로 요청은 이번에 실기기 재검증하지 않음. 로컬 경로 애니메이션이며 실제 GPS/기어/도착 상태나 차량 제어로 기록하지 않음.
- 실제 설치 앱에서 주소 입력 없음, 버튼·배경·기어 숨김 및 연결창 확인. 운영 서버에 테스트 그룹/메시지를 만들지 않음.
- 기존 임시 모바일 주소와 서버는 유지. 모바일 물리 기기 2대의 동시 조작은 이번 검증 범위가 아님.

## 화면
- [연결창](android/live-group-connection.png)
- [전체 메시지](android/figma-verification/47-group-messages.png)
- [그룹 이동](android/figma-verification/48-group-moving.png)

[Android WebView 공식 API](https://developer.android.com/reference/android/webkit/WebView#evaluateJavascript(java.lang.String,%20android.webkit.ValueCallback))의 UI 스레드 비동기 콜백을 사용하며 JS native interface는 추가하지 않았다.
