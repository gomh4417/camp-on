package com.example.pleos_demo

import android.content.Intent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.toggleable
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

@Composable internal fun JourneyScreen(j:JourneySession,s:CampSession,wide:Boolean,onArrived:()->Unit) {
    val scope=rememberCoroutineScope()
    val context=LocalContext.current
    var connection by rememberSaveable { mutableStateOf(false) }
    var address by rememberSaveable { mutableStateOf(j.serverUrl) }
    var selected by rememberSaveable { mutableStateOf("") }
    var memo by rememberSaveable { mutableStateOf("") }
    val vehicle=j.vehicles.firstOrNull { it.optString("id")==selected }?:j.myVehicle
    Text(j.groupName,style=MaterialTheme.typography.headlineLarge)
    Caption("함께 이동  →  캠핑  →  귀가")
    Row(horizontalArrangement=Arrangement.spacedBy(12.dp),verticalAlignment=Alignment.CenterVertically) {
        Text(j.destination,Modifier.weight(1f),style=MaterialTheme.typography.titleLarge)
        SecondaryAction("연결 설정") { connection=true }
    }
    Caption(if(j.fresh) "그룹 시연 서버 연결됨 · 내 차량 ${j.name(j.vehicleId)}" else j.error.ifBlank { "그룹 서버에 연결하고 있어요" })
    if(j.message.isNotBlank()) Text(j.message,color=CampAccent)
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(12.dp)) {
        j.vehicles.forEach { v->
            FilterChip(selected=vehicle?.optString("id")==v.optString("id"),onClick={selected=v.optString("id")},
                label={Text("${v.label("name")} · ${v.label("status")}")},modifier=Modifier.heightIn(min=56.dp))
        }
    }
    val details:@Composable ()->Unit={
        Panel(Modifier.fillMaxWidth(),emphasis=true) {
            Text(vehicle?.label("name")?:"차량 현황",style=MaterialTheme.typography.headlineMedium)
            Text(vehicle?.label("status")?:"연결 대기",style=MaterialTheme.typography.headlineLarge)
            Caption(vehicle?.label("location")?:"—")
            Text("도착 ${vehicle?.label("eta")?:"—"} · 남은 거리 ${vehicle?.label("distance")?:"—"}")
            Caption("그룹 차량 배터리 ${vehicle?.label("battery")?:"—"}% · EV 계획에는 내 차량 SDK 값만 사용해요")
            SecondaryAction("지도·음성 메모 열기",modifier=Modifier.fillMaxWidth()) {
                try {
                    context.startActivity(Intent().setClassName("com.anchor.pleosvoice","com.anchor.pleosvoice.GroupDriveActivity")
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                } catch(_:android.content.ActivityNotFoundException) {
                    j.message="MVP_C의 차량 APK를 먼저 설치해주세요"
                } catch(_:SecurityException) { j.message="원본 지도 앱을 열 수 없어요" }
            }
        }
    }
    val briefing:@Composable ()->Unit={
        Panel(Modifier.fillMaxWidth()) {
            Text("우리 그룹 브리핑",style=MaterialTheme.typography.titleLarge)
            Text(j.state?.label("summary")?:"서버에 연결하면 브리핑이 나타나요")
            Caption(j.state?.label("headline")?:"")
            j.requests.forEach { r->
                HorizontalDivider()
                Text(r.label("item"))
                Caption("${j.name(r.optString("fromVehicleId"))} → ${j.name(r.optString("toVehicleId"))}")
                Row(horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                    SecondaryAction("할 일에 추가",j.editable) { scope.launch { j.addRequest(r.optString("id")) } }
                    TextButton(onClick={scope.launch { j.removeRequest(r.optString("id")) }},enabled=j.editable) { Text("삭제") }
                }
            }
        }
    }
    if(wide) Row(horizontalArrangement=Arrangement.spacedBy(20.dp)) {
        Column(Modifier.weight(1f)) { details() };Column(Modifier.weight(1f)) { briefing() }
    } else { details();briefing() }
    JourneyTodos(j,vehicle?.optString("id")?:j.vehicleId)
    Panel(Modifier.fillMaxWidth()) {
        Text("가는 길에 알려주세요",style=MaterialTheme.typography.titleLarge)
        OutlinedTextField(memo,{memo=it.take(500)},modifier=Modifier.fillMaxWidth(),
            label={Text("휴게소 방문이나 구매할 물건을 남겨요")},minLines=2)
        PrimaryAction("그룹에 보내기",j.editable && memo.isNotBlank()) {
            scope.launch { if(j.memo(memo))memo="" }
        }
        j.memos.takeLast(4).reversed().forEach { m->
            Caption(j.name(m.optString("vehicleId")))
            Text(m.label("text"))
        }
    }
    Panel(Modifier.fillMaxWidth(),emphasis=true) {
        Text("도착하면, 편안한 캠핑으로",style=MaterialTheme.typography.titleLarge)
        Caption("내 차량 도착과 주차를 확인한 뒤 캠핑장과 할 일을 이어가요. 차량 설정은 자동으로 바뀌지 않아요.")
        PrimaryAction("도착 · 캠핑으로 이어가기",j.arrived && s.parkingState.allowsControl && !s.busy) {
            if(j.enterCamp(s))onArrived()
        }
    }
    if(connection) AlertDialog(onDismissRequest={connection=false},title={Text("그룹 연결")},
        text={
            Column(verticalArrangement=Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(address,{address=it},label={Text("공유 서버 주소")},singleLine=true)
                Caption("에뮬레이터: http://10.0.2.2:8787 · 외부 서버: HTTPS")
                Text("내 차량")
                j.vehicles.forEach { v->
                    FilterChip(selected=j.vehicleId==v.optString("id"),onClick={j.selectVehicle(v.optString("id"))},
                        label={Text(v.label("name"))})
                }
                Caption(j.message)
            }
        },confirmButton={TextButton(onClick={if(j.connect(address)){connection=false;scope.launch { j.refresh() }}}) { Text("연결") }},
        dismissButton={TextButton(onClick={connection=false}) { Text("닫기") }})
}

@Composable private fun JourneyTodos(j:JourneySession,owner:String) {
    val scope=rememberCoroutineScope()
    Panel(Modifier.fillMaxWidth()) {
        Text("${j.name(owner)}의 할 일",style=MaterialTheme.typography.titleLarge)
        val todos=j.todos.filter { it.optString("ownerVehicleId")==owner }
        if(todos.isEmpty())Caption("등록된 할 일이 없어요")
        todos.forEach { t->
            Row(Modifier.fillMaxWidth().heightIn(min=56.dp).toggleable(
                value=t.optBoolean("done"),enabled=j.editable,role=Role.Checkbox,
                onValueChange={done->scope.launch { j.completeTodo(t.optString("id"),done) }}),verticalAlignment=Alignment.CenterVertically) {
                Checkbox(t.optBoolean("done"),onCheckedChange=null,enabled=j.editable)
                Text(t.label("title"),Modifier.weight(1f))
            }
        }
    }
}

@Composable internal fun JourneyCarryover(j:JourneySession,onJourney:()->Unit) {
    if(j.camping) {
        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
            Text(j.destination,Modifier.weight(1f),style=MaterialTheme.typography.titleLarge)
            TextButton(onClick=onJourney) { Text("그룹 현황") }
        }
        if(!j.fresh)Caption("그룹 연결 확인 필요 · 할 일 변경 잠김")
        JourneyTodos(j,j.vehicleId)
    }
}
