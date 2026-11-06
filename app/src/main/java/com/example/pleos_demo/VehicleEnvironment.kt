package com.example.pleos_demo

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.launch

private fun VehicleControl.category()=when(kind) {
    ControlKind.LIGHT,ControlKind.CABIN_LIGHT,ControlKind.READING_LIGHT->1
    ControlKind.SEAT_POSITION,ControlKind.SEAT_TEMPERATURE->2
    else->0
}
private fun VehicleControl.zone():String {
    val area=label.substringAfter(" · ","")
    return when {
        area.isBlank()->"전체"
        "+" in area->if("뒷좌석" in area) "전체 좌석" else "앞좌석 공통"
        else->area.replace("앞좌석 왼쪽","운전석").replace("앞좌석 오른쪽","동승석")
            .replace("REAR_WINDSHIELD","뒷유리").replace("FRONT_WINDSHIELD","앞유리").replace("WINDSHIELD","앞유리")
    }
}
@Composable internal fun ControlEditor(s:CampSession,initialGroup:Int=0,dismiss:()->Unit) {
    var group by rememberSaveable { mutableIntStateOf(initialGroup) }
    var advanced by rememberSaveable { mutableStateOf(false) }
    var help by remember { mutableStateOf(false) }
    val scope=rememberCoroutineScope()
    if(help) PleosGuide { help=false }
    val controls=s.reading.controls.filter { it.category()==group }
    val temperatures=controls.filter { it.kind==ControlKind.TEMPERATURE }
    val basic=controls.filter { it.kind in listOf(ControlKind.POWER,ControlKind.FAN,ControlKind.AC,ControlKind.RECIRCULATION) }
    val extra=controls-temperatures-basic
    val close={ if(!s.busy) dismiss() }
    Dialog(onDismissRequest=close,properties=DialogProperties(usePlatformDefaultWidth=false,dismissOnBackPress=!s.busy,dismissOnClickOutside=false)) {
        Surface(Modifier.fillMaxSize().safeDrawingPadding(),color=CampBackground) {
            BoxWithConstraints {
                val wide=maxWidth>=900.dp
                Column(Modifier.fillMaxSize().padding(if(wide)24.dp else 16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
                    Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                        Column(Modifier.weight(1f)) {
                            Caption("CABIN CONTROL")
                            Text("내 공간, 바로 조절",style=MaterialTheme.typography.headlineMedium)
                        }
                        TextButton(onClick={help=true}) { Text("사용법") }
                        SecondaryAction("닫기",!s.busy,action=close)
                    }
                    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                        listOf("공조","조명","시트").forEachIndexed { i,label->
                            FilterChip(group==i,{group=i;advanced=false},label={Text(label)},
                                modifier=Modifier.weight(1f).heightIn(min=56.dp),shape=RoundedCornerShape(18.dp))
                        }
                    }
                    if(wide) Row(Modifier.weight(1f),horizontalArrangement=Arrangement.spacedBy(20.dp)) {
                        Column(Modifier.weight(1.3f).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(12.dp)) {
                            CabinPlan(s,temperatures,Modifier.fillMaxWidth())
                        }
                        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(12.dp)) {
                            if(group==0) {
                                Text("공기 조절",style=MaterialTheme.typography.titleLarge)
                                basic.forEach { DirectControl(s,it) }
                                TextButton(onClick={advanced=!advanced}) { Text(if(advanced)"추가 설정 접기" else "바람 방향 · 자동 공조 · 성에 제거") }
                                if(advanced) extra.forEach { DirectControl(s,it) }
                            } else controls.forEach { DirectControl(s,it) }
                            if(controls.isEmpty()) Caption("지원하는 제어가 없습니다.")
                        }
                    } else Column(Modifier.weight(1f).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(12.dp)) {
                        CabinPlan(s,temperatures,Modifier.fillMaxWidth())
                        if(group==0) {
                            basic.forEach { DirectControl(s,it) }
                            TextButton(onClick={advanced=!advanced}) { Text(if(advanced)"추가 설정 접기" else "추가 공조 설정") }
                            if(advanced) extra.forEach { DirectControl(s,it) }
                        } else controls.forEach { DirectControl(s,it) }
                        if(controls.isEmpty()) Caption("지원하는 제어가 없습니다.")
                    }
                    HorizontalDivider(color=CampLine)
                    Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                        Column(Modifier.weight(1f)) {
                            Text(if(s.busy) "차량 응답 확인 중" else if(s.written.keys.any { s.awaiting(it) }) "전송됨 · 차량 반영 대기" else "누르면 바로 전송됩니다",
                                color=CampAccent,style=MaterialTheme.typography.titleMedium)
                            Caption(if(s.parkingState.unrestricted!=true) "Pleos 화면 제한 · 차량 상태를 확인하세요"
                                else if(group!=0 && s.parkingState.parked!=true) "시트·조명은 주차 후 조절하세요"
                                else "표시값은 차량 수신값 · 변경 전 설정은 보관됩니다")
                            if(s.message.startsWith("실패")) Text(s.message,color=CampWarning,style=MaterialTheme.typography.bodySmall)
                        }
                        TextButton(onClick={scope.launch { s.refresh() }},enabled=!s.busy && !s.refreshing) { Text("상태 확인") }
                    }
                }
            }
        }
    }
}
@Composable internal fun CabinPlan(s:CampSession,temperatures:List<VehicleControl>,modifier:Modifier) {
    Column(modifier.clip(RoundedCornerShape(28.dp)).background(CampSurface).padding(16.dp),
        verticalArrangement=Arrangement.spacedBy(12.dp)) {
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
            Caption("CABIN TOP VIEW")
            Caption("실내 ${s.reading.cabin.takeIf { s.fresh && s.reading.isFresh("실내 온도",s.now) }.fmt()}°C")
        }
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            if(maxWidth>=680.dp && temperatures.isNotEmpty()) {
                Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                    Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(12.dp)) {
                        temperatures.filterIndexed { i,_->i%2==0 }.forEach { DirectControl(s,it,true) }
                    }
                    Image(painterResource(R.drawable.cabin_top),"위에서 본 차량 실내",Modifier.width(260.dp).height(500.dp),contentScale=ContentScale.Fit)
                    Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(12.dp)) {
                        temperatures.filterIndexed { i,_->i%2==1 }.forEach { DirectControl(s,it,true) }
                    }
                }
            } else {
                Column(verticalArrangement=Arrangement.spacedBy(12.dp)) {
                    Image(painterResource(R.drawable.cabin_top),"위에서 본 차량 실내",Modifier.fillMaxWidth().height(260.dp),contentScale=ContentScale.Fit)
                    temperatures.forEach { DirectControl(s,it,true) }
                }
            }
        }
        Caption("앞쪽 ↑    운전석 왼쪽 · 동승석 오른쪽")
    }
}
@Composable private fun DirectControl(s:CampSession,c:VehicleControl,compact:Boolean=false) {
    val scope=rememberCoroutineScope()
    val actual=s.current[c.id].takeIf { s.fresh && s.reading.isFresh(c.id,s.now) }
    val pending=s.awaiting(c.id)
    val enabled=actual!=null && s.canAdjust(c)
    val send:(Double)->Unit={value->scope.launch { s.applyDirect(c,value) }}
    Panel(Modifier.fillMaxWidth(),emphasis=pending) {
        Text(if(c.kind==ControlKind.TEMPERATURE)c.zone() else c.kind.title,style=MaterialTheme.typography.titleMedium)
        if(c.kind!=ControlKind.TEMPERATURE) Caption(c.zone())
        if(actual!=null) when {
            c.choices.isNotEmpty()->Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                c.choices.forEach { (v,label)->FilterChip(actual==v,{send(v)},label={Text(label)},enabled=enabled,modifier=Modifier.heightIn(min=56.dp)) }
            }
            c.kind in listOf(ControlKind.POWER,ControlKind.AC,ControlKind.RECIRCULATION,ControlKind.AUTO,ControlKind.DUAL,ControlKind.AIR_CLEANER,ControlKind.DEFROSTER)->Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                Text(controlValue(c,actual),Modifier.weight(1f),style=MaterialTheme.typography.headlineSmall)
                Switch(actual>0,{send(if(it)1.0 else 0.0)},enabled=enabled,
                    modifier=Modifier.semantics { contentDescription="${c.kind.title} · ${c.zone()}" })
            }
            else-> {
                Text(controlValue(c,actual),style=if(compact)MaterialTheme.typography.headlineLarge else MaterialTheme.typography.headlineMedium)
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                    SecondaryAction("−",enabled && actual>c.min,Modifier.weight(1f).heightIn(min=64.dp).semantics { contentDescription="${c.kind.title} 낮추기 · ${c.zone()}" }) { send(actual-c.step) }
                    SecondaryAction("+",enabled && actual<c.max,Modifier.weight(1f).heightIn(min=64.dp).semantics { contentDescription="${c.kind.title} 높이기 · ${c.zone()}" }) { send(actual+c.step) }
                }
            }
        } else Caption("차량값 수신 대기")
        if(pending) Caption("요청 ${controlValue(c,s.written[c.id])} · 반영 대기")
        else if(!c.writable) Caption("조회 전용")
        else s.results[c.id]?.takeIf { it.startsWith("실패") }?.let { Text(it,color=CampWarning,style=MaterialTheme.typography.bodySmall) }
    }
}
