package com.example.pleos_demo

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable internal fun PleosGuide(dismiss:()->Unit) {
    AlertDialog(onDismissRequest=dismiss,title={Text("Pleos · 사용 안내")},
        text={
            Column(Modifier.heightIn(max=680.dp).verticalScroll(rememberScrollState()),
                verticalArrangement=Arrangement.spacedBy(16.dp)) {
                Text("Running Device = Android 화면",style=MaterialTheme.typography.titleMedium)
                Text("CAMP:ON과 차량 기본 화면이 실행되는 에뮬레이터입니다.")
                Text("웹 콘솔 = 가상 차량",style=MaterialTheme.typography.titleMedium)
                Text("PC의 127.0.0.1:8088에서 차량 상태·입력을 만듭니다. 탭을 활성 상태로 유지하세요. 앱에서 SDK 값이 읽혀도 콘솔 연결은 별도로 끊길 수 있습니다.")
                HorizontalDivider()
                Text("온도를 바꾸려면",style=MaterialTheme.typography.titleMedium)
                Text("1. 주차 P 상태에서 공조 전원을 켭니다.\n2. 공조 이미지 → 운전석 또는 동승석 → + / −로 변경값을 만듭니다.\n3. 하단 ‘차량에 적용’을 누릅니다. 시작 전이면 ‘캠핑 시작·적용’을 누릅니다.\n4. ‘적용 확인’과 현재값을 확인합니다. 좌우 온도는 선택한 좌석별로 비교하세요.")
                Text("웹 콘솔에서는 Climate → Target Temperature의 같은 좌석을 확인합니다. 실내 온도는 센서값이라 목표 온도 변경과 동시에 바뀌지 않습니다.")
                HorizontalDivider()
                Text("내 차량 주행",style=MaterialTheme.typography.titleMedium)
                Text("콘솔의 Motion → Gear Selection에서 기어 상태를 확인하세요. P와 속도 0에서는 정지합니다. D 전환 뒤에는 콘솔에서 지원하는 가속 입력이 필요합니다. 주변 차량 움직임은 내 차량의 속도와 별개입니다.")
                Text("주행하면 CAMP:ON 제어는 잠깁니다. 공조·시트 조절 검토는 P로 돌아온 뒤 진행하세요.")
                Text("반영되지 않을 때",style=MaterialTheme.typography.titleMedium)
                Text("PC 콘솔 탭 새로고침 → 공조 전원 확인 → 같은 좌석 비교 → 카드 실패 메시지 확인 순서로 확인하세요. 실패한 요청을 성공값으로 표시하지 않습니다.")
            }
        },confirmButton={TextButton(onClick=dismiss,modifier=Modifier.heightIn(min=56.dp)) { Text("확인") }})
}
