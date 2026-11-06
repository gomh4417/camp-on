# 차량 이미지

## 라이트·플랫 UI용 분리 레이어 (최신)

내장 image_gen 사용. 두 PNG는 투명 배경으로 생성해 리소스에 복사했다.
- app/src/main/res/drawable-nodpi/cabin_flat_shell.png: 좌석 없는 차체.
- app/src/main/res/drawable-nodpi/cabin_flat_seat.png: 한 좌석. 같은 리소스를 7개의 독립 선택 레이어로 배치.
- CabinMap.kt: 차체 정규화 좌표에 좌석 배치, 선택 색/윤곽/체크, SDK area 연결. 이미지 자체에 선택 상태나 제어값을 굽지 않는다.
- 실제 차량 형상·위치를 측정한 모델은 아니며 제어 영역 안내용이다.

차체 프롬프트: Flat 2D top-down cutaway SUV cabin shell, strictly orthographic plan, front at top. Portrait 1:2 tightly cropped. Cute refined casual Korean fintech illustration, pale cool gray chassis, white floor, slate outlines, blue-gray dashboard and left steering wheel, center console. EMPTY cabin without seats, space for separately layered three-row seats. No perspective, 3D volume, gradient, drop shadow, text or logo. Transparent outside car, fill 95 percent of canvas height.

좌석 프롬프트: Single isolated car seat sprite for a flat 2D top-down SUV cabin UI. Strict overhead orthographic, headrest at top, rounded rectangular backrest/cushion, two bolster lines. Friendly minimal vector-like pale blue-gray fill, thin slate outlines, white cushion inset. No gradient, 3D perspective, shadow or text. Transparent background, one symmetric seat, intended for separate clickable layers.

[온도 Slider 공식 문서](https://developer.android.com/develop/ui/compose/components/slider)
[선택 영역 접근성 공식 문서](https://developer.android.com/develop/ui/compose/components/radio-button)

## 이전 클레이 리소스 (현재 화면에서는 미사용)


2026-11-06. 사용자가 제공한 레퍼런스 3개를 참고해 내장 image_gen으로 생성.
Imagen 전용 도구는 없어 사용하지 않았다. 최종 시각 검증은 사용자 요청으로 생략.

## cabin_clay.png

경로: app/src/main/res/drawable-nodpi/cabin_clay.png
용도: 캠핑 현황의 차량 환경 히어로.

프롬프트: Wide 16:9 automotive cabin, viewed symmetrically from second row, slightly elevated.
Reference 1: all-white matte clay style, studio ambient occlusion, sculptural geometry.
References 2 and 3: three-row Korean SUV, rectilinear horizontal dashboard, left steering wheel, center console, two front seats.
Warm-white porcelain surfaces, light-gray seams, blank screens, restrained seat stitching and horizontal vents.
No circular turbine vents, city scene, people, logos, text, icons, UI or rounded image corners.

## cabin_top.png

경로: app/src/main/res/drawable-nodpi/cabin_top.png
용도: 시트 조절 화면.

프롬프트: Isolated top-down orthographic cutaway of modern three-row Korean SUV, front at top.
Two front seats, second-row split bench, third-row split bench and cargo area from reference 2;
rectilinear dashboard, left steering wheel, center console from reference 3.
All-white matte clay style from reference 1, soft gray ambient occlusion, detailed cushions.
Roof removed, full silhouette with margin, portrait 2:3, transparent background.
No green paint, ground, landscape, people, logos, text or UI.
