# 차량 이미지

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
