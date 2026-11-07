# 3인 모바일 그룹 시연

## 실행

PC: Node.js 22 이상. 프로젝트 루트에서 터미널 두 개를 유지한다.

```powershell
node services/group-sync/companion.mjs
& 'C:/Program Files (x86)/cloudflared/cloudflared.exe' tunnel --url http://127.0.0.1:8788 --no-autoupdate
```

두 번째 터미널의 HTTPS 주소를 PC와 휴대폰에서 연다. PC가 CAR A로 그룹을 만들고 초대 링크를 공유한다. 휴대폰 두 대는 CAR B와 CAR C로 참여한다. 앱 설치는 필요 없다.

차량 앱: 함께 이동 → 그룹 연결 → 같은 HTTPS 원점 주소(경로·쿼리 제외), 초대 코드, CAR A → 연결. 차량 연결은 저장된다. 새 터널 주소로 바뀌면 다시 연결한다.

- 시작: 그룹 생성·초대 코드 참여·닉네임·초대 링크 복사.
- 설정: 목적지·일정·도착 목표(CAR A 편집), 참가자별 출발/이동/도착 상태.
- To-do: 추가·수정·삭제·담당 CAR A/B/C·TODO/DOING/DONE·필터.
- 대화: 메시지 공유, 메시지에서 공동 할 일 초안 생성. 차량에는 그룹 브리핑·대화로 표시.
- 약 1.5초마다 동기화. 차량 위치·배터리는 실제 수신 없으면 미상 유지.
- 문장 요약은 결정적 규칙이며 생성형 AI 연동이 아니다.
- 초대 코드 소지자가 시연 역할을 선택한다. 계정 기반 본인 인증/역할 소유권 검증은 아니다.

## 보관과 배포

상태는 `services/group-sync/data/companion.json`에 원자적으로 저장된다. 세션 토큰 원문은 서버에 저장하지 않고 해시만 보관한다. 브라우저에는 해당 참가자의 토큰이 저장된다. 코드·토큰·실제 대화 데이터는 Git에서 제외한다.

현재 백엔드는 PC에서 실행된다. PC·서버·터널 종료 시 연결이 끊어진다. Quick Tunnel 주소는 재실행마다 바뀐다. 상시 서비스에는 고정 HTTPS 서버와 지속 저장소가 필요하다.

Vercel에는 `services/group-sync/mobile`만 배포한다. `vercel.json`의 API rewrite 대상은 실행 중인 백엔드 원점으로 맞춘다. Vercel 로그인 후 해당 폴더에서 `npx vercel deploy --prod`로 배포한다. .vercel 및 자격 증명은 커밋하지 않는다. 이번 임시 Vercel 배포는 403 차단으로 사용할 수 없었고, 인증된 정식 배포는 아직 미완료다.

## 검증

- `npm test` (services/group-sync): 기존 서비스와 모바일 서비스 통합 테스트 2개 통과.
- 3개 참가자 세션, 발신자 위조 차단, 그룹 격리, 중복 메시지 키, 공동 할 일 동기화, 재시작 보존 검증.
- `node services/group-sync/verify-live.mjs <HTTPS 원점>`: 별도 시연 그룹을 생성하고 외부 HTTPS에서 3인·메시지 2개·할 일 1개 검증. 토큰은 출력하지 않는다.
- 실제 휴대폰 두 대의 브라우저 화면·키보드·접근성 검증은 미실시. 브라우저 자동화가 중단되어 모바일 시각 검증도 미실시.
