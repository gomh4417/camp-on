# 키패드 입력 / 모바일 접속 수정

## 원인과 수정

- DesignViewport가 키패드 표시로 줄어드는 높이를 배율 계산에 사용했다. LocalDensity 변경은 Compose Dialog의 native window 재생성 조건이므로 포커스와 키패드가 반복 해제될 수 있었다.
- 배율은 화면 너비로 고정하고 remember로 유지한다. 줄어든 높이는 스크롤로 처리한다. 기존 safeDrawingPadding의 IME 처리를 보존한다.
- 근거: [AndroidX Dialog 구현](https://github.com/androidx/androidx/blob/androidx-main/compose/ui/ui/src/androidMain/kotlin/androidx/compose/ui/window/AndroidDialog.android.kt)의 remember(view, density, ...) 및 DisposableEffect(dialog).

## 검증 결과

- assembleDebug, assembleDebugAndroidTest, testDebugUnitTest 성공.
- Pleos emulator-5554 / user 10: KeyboardStabilityTest 2개 + CampUxTest 1개, OK (3 tests), 41.22초.
- 그룹 대화상자: 반복 높이 변경 중 동일 Dialog semantics ID, 닉네임 한글 입력/추가 입력/포커스 유지, 초대 코드 0427 선행 0 보존.
- 장비 이름: 반복 높이 변경 중 한글 입력과 포커스 유지.
- 기존 캠핑 시작·날짜·시각·장비 중복·모드 명시 적용 회귀 통과. 테스트 VehicleSource만 사용.
- 실제 설치 앱의 그룹 연결에서 시스템 키패드 표시, keyboardcheck 입력, 삭제, 뒤로 가기로 키패드 닫기 확인. native Window{efa948e}가 전체 과정에서 동일했고 닉네임 포커스 유지. 검증 문구는 삭제했으며 그룹 생성/메시지 전송은 하지 않았다.
- 화면 증거: [키패드 입력](android/live-keyboard-stable.png).

## 모바일 주소

- 기존 winning-luxury-sustained-repeat.trycloudflare.com: curl 오류 6, DNS 조회 실패.
- 현재 주소: https://corrected-bugs-highlights-scheduled.trycloudflare.com/
- /api/health HTTP 200, {"ok":true}. Chrome 390×844에서 공개 페이지·폰트·4개 GIF 시간·정지 이미지·가로 넘침 없음 검증 통과. 실제 참여자 생성 없음.
- 화면 증거: [공개 모바일 화면](mobile/public-live-390.png).
- 서버와 기존 터널은 정상 실행 중이므로 재시작하지 않았다. 임시 Quick Tunnel은 프로세스 종료 시 중단되고 다시 만들면 주소가 바뀐다. 영구 호스팅이 완료된 것은 아니다.
- [Cloudflare 공식 안내](https://developers.cloudflare.com/tunnel/get-started/quick-tunnels/).

실제 휴대폰 키보드는 이번 검증 대상이 아니다. 물리 차량 SDK 동작에는 변경 없음.
