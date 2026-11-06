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
                Text("1. 홈의 큰 차량 그림에서 좌석을 선택하고 + / − 또는 온도 슬라이더를 조절합니다. 슬라이더는 손을 뗄 때 전송합니다.\n2. 별도 적용이나 캠핑 시작 없이 해당 좌석으로 즉시 전송됩니다.\n3. 반영 대기 중에는 요청값을 따로 표시합니다. 현재값은 실제 차량 응답 후 바뀝니다.\n4. 공조 전원이 꺼져 있으면 공조 조절 화면에서 켭니다.")
                Text("웹 콘솔에서는 Climate → Target Temperature의 같은 좌석을 확인합니다. 실내 온도는 센서값이라 목표 온도 변경과 동시에 바뀌지 않습니다.")
                HorizontalDivider()
                Text("내 차량 주행",style=MaterialTheme.typography.titleMedium)
                Text("콘솔의 Motion → Gear Selection에서 기어 상태를 확인하세요. P와 속도 0에서는 정지합니다. D 전환 뒤에는 콘솔에서 지원하는 가속 입력이 필요합니다. 주변 차량 움직임은 내 차량의 속도와 별개입니다.")
                Text("공조는 차량 상태가 확인되고 Pleos가 조작을 허용할 때 사용할 수 있습니다. 시스템 주행 제한은 우회하지 않습니다. 시트 이동·조명·모드 일괄 적용·복원은 주차 P에서만 가능합니다.")
                Text("반영되지 않을 때",style=MaterialTheme.typography.titleMedium)
                Text("반영 대기는 실패와 다릅니다. PC 콘솔 탭을 활성화하고 공조 전원·같은 좌석을 확인하세요. 늦게 도착한 값도 자동 조회해 표시합니다. 기다리는 동안 같은 명령을 다시 보내지 않습니다. 변경한 설정은 종료·귀가 화면에서 복원할 수 있습니다.")
            }
        },confirmButton={TextButton(onClick=dismiss,modifier=Modifier.heightIn(min=56.dp)) { Text("확인") }})
}
