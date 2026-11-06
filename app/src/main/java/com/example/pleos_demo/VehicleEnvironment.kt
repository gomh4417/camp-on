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
    var zone by rememberSaveable { mutableStateOf("전체") }
    val scope=rememberCoroutineScope()
    var help by remember { mutableStateOf(false) }
    if(help) PleosGuide { help=false }
    val controls=s.reading.controls.filter { it.category()==group }
    val zones=listOf("전체")+controls.map { it.zone() }.filter { it!="전체" }.distinct()
    val shown=controls.filter { zone=="전체" || it.zone()==zone }
    val pending=s.reading.controls.filter { it.writable && it.id in s.overrides }
    val close={ if(!s.busy) dismiss() }
    Dialog(onDismissRequest=close,properties=DialogProperties(usePlatformDefaultWidth=false,dismissOnBackPress=!s.busy,dismissOnClickOutside=false)) {
        Surface(Modifier.fillMaxSize().safeDrawingPadding(),color=CampBackground) {
            BoxWithConstraints {
                val wide=maxWidth>=1000.dp
                Column(Modifier.fillMaxSize().padding(if(wide)32.dp else 16.dp),verticalArrangement=Arrangement.spacedBy(20.dp)) {
                    Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(16.dp)) {
                        Column(Modifier.weight(1f)) {
                            Caption("CABIN CONTROL")
                            Text("내가 머무는 공간",style=MaterialTheme.typography.headlineLarge)
                        }
                        TextButton(onClick={help=true}) { Text("사용법") }
                        SecondaryAction("닫기",!s.busy,action=close)
                    }
                    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                        listOf("공조","조명","시트").forEachIndexed { i,label->
                            FilterChip(group==i,{group=i;zone="전체"},label={Text(label)},
                                modifier=Modifier.weight(1f).heightIn(min=64.dp),shape=RoundedCornerShape(18.dp))
                        }
                    }
                    if(wide) Row(Modifier.weight(1f),horizontalArrangement=Arrangement.spacedBy(24.dp)) {
                        EnvironmentScene(s,group,zones,zone,{zone=it},Modifier.weight(1.15f).fillMaxHeight().verticalScroll(rememberScrollState()))
                        Column(Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState()),
                            verticalArrangement=Arrangement.spacedBy(16.dp)) {
                            EnvironmentControls(s,shown,zone)
                        }
                    } else Column(Modifier.weight(1f).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(16.dp)) {
                        EnvironmentScene(s,group,zones,zone,{zone=it},Modifier.fillMaxWidth())
                        EnvironmentControls(s,shown,zone)
                    }
                    HorizontalDivider(color=CampLine)
                    if(s.message.isNotBlank()) Text(s.message,color=CampAccent,style=MaterialTheme.typography.bodyMedium)
                    Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(16.dp)) {
                        Column(Modifier.weight(1f)) {
                            Text(if(s.busy) "차량 응답 확인 중" else if(pending.isEmpty()) "차량의 현재 설정" else "${pending.size}개 변경 · 아직 차량에 적용 전",
                                style=MaterialTheme.typography.titleMedium)
                            Caption(if(!s.parkingState.allowsControl) "주차 P · 연결 상태를 확인하세요"
                                else if(!s.active) "시작·적용 시 원래 설정을 저장합니다"
                                else "아래 적용 버튼을 누르면 차량으로 전송합니다")
                        }
                        if(pending.isNotEmpty()) TextButton(onClick={s.overrides.clear();s.save()},enabled=!s.busy) { Text("변경 취소") }
                        PrimaryAction(if(s.active) "차량에 적용" else "캠핑 시작·적용",
                            pending.isNotEmpty() && s.canControl && (s.active || s.written.isEmpty())) {
                            scope.launch {
                                if(!s.active) s.start()
                                if(s.active) s.applyMode(onlyEdited=true)
                            }
                        }
                    }
                }
            }
        }
    }
}
@Composable private fun EnvironmentScene(s:CampSession,group:Int,zones:List<String>,zone:String,select:(String)->Unit,modifier:Modifier) {
    Column(modifier.clip(RoundedCornerShape(28.dp)).background(CampSurface)) {
        Box(Modifier.fillMaxWidth().heightIn(min=240.dp)) {
            Image(painterResource(if(group==2)R.drawable.cabin_top else R.drawable.cabin_clay),
                if(group==2)"시트 위치 안내" else "차량 실내 제어 영역",
                Modifier.fillMaxWidth().height(if(group==2)480.dp else 340.dp),
                contentScale=if(group==2)ContentScale.Fit else ContentScale.Crop)
            if(group==0) Row(Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(20.dp),
                horizontalArrangement=Arrangement.SpaceBetween) {
                listOf("운전석","동승석").filter { it in zones }.forEach { name->
                    val c=s.reading.controls.firstOrNull { it.kind==ControlKind.TEMPERATURE && it.zone()==name }
                    Button(onClick={select(name)},shape=RoundedCornerShape(16.dp),contentPadding=PaddingValues(18.dp)) {
                        Column(horizontalAlignment=Alignment.CenterHorizontally) {
                            Text(name)
                            Text(c?.let { control->controlValue(control,s.current[control.id].takeIf { s.fresh && s.reading.isFresh(control.id,s.now) }) }?:"—",
                                style=MaterialTheme.typography.headlineSmall)
                        }
                    }
                }
            }
            if(group==2) Column(Modifier.align(Alignment.Center).fillMaxWidth().padding(24.dp),
                verticalArrangement=Arrangement.spacedBy(100.dp)) {
                listOf(listOf("운전석","동승석"),listOf("뒷좌석 왼쪽","뒷좌석 오른쪽")).forEach { row->
                    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
                        row.filter { it in zones }.forEach { name->
                            Button(onClick={select(name)},modifier=Modifier.heightIn(min=56.dp)) { Text(name) }
                        }
                    }
                }
            }
        }
        Column(Modifier.padding(24.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            Caption(listOf("TEMPERATURE & AIR","LIGHT & ATMOSPHERE","SEAT & COMFORT")[group])
            Text(listOf("공기를 맞추세요","빛으로 만드는 휴식","편안한 자리")[group],
                style=MaterialTheme.typography.headlineMedium)
            Caption(if(group==0) "실내 ${s.reading.cabin.takeIf { s.fresh }.fmt()}°C · 사진 위 좌석을 선택하세요"
                else if(group==2) "사진 위 좌석을 선택해 열선·통풍과 위치를 조절하세요" else "아래 영역을 선택해 실내등과 밝기를 조절하세요")
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                zones.forEach { name->FilterChip(zone==name,{select(name)},label={Text(name)},modifier=Modifier.heightIn(min=56.dp)) }
            }
        }
    }
}
@Composable private fun EnvironmentControls(s:CampSession,controls:List<VehicleControl>,zone:String) {
    Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.SpaceBetween) {
        Text(if(zone=="전체") "전체 설정" else zone,style=MaterialTheme.typography.headlineSmall)
        Caption("현재값 → 변경값")
    }
    if(controls.isEmpty()) Caption("이 영역에서 지원하는 제어가 없습니다.")
    controls.forEach { c->
        val actual=s.current[c.id].takeIf { s.fresh && s.reading.isFresh(c.id,s.now) }
        val draft=s.overrides[c.id]
        val value=draft?:actual
        Panel(Modifier.fillMaxWidth(),emphasis=draft!=null) {
            Row(verticalAlignment=Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(c.kind.title,style=MaterialTheme.typography.titleLarge)
                    Caption(c.zone())
                }
                Text(if(c.writable) "현재 ${controlValue(c,actual)}" else "조회 전용 · ${controlValue(c,actual)}",
                    color=CampMuted,style=MaterialTheme.typography.bodyMedium)
            }
            if(c.writable && value!=null) {
                val enabled=!s.busy && s.fresh
                when {
                    c.choices.isNotEmpty()->Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                        c.choices.forEach { (v,label)->FilterChip(value==v,{s.setTarget(c,v)},label={Text(label)},enabled=enabled,modifier=Modifier.heightIn(min=56.dp)) }
                    }
                    c.kind in listOf(ControlKind.POWER,ControlKind.AC,ControlKind.RECIRCULATION,ControlKind.AUTO,ControlKind.DUAL,ControlKind.AIR_CLEANER,ControlKind.DEFROSTER)->Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                        Text(controlValue(c,value),Modifier.weight(1f),style=MaterialTheme.typography.headlineSmall)
                        Switch(value>0,{s.setTarget(c,if(it)1.0 else 0.0)},enabled=enabled,
                            modifier=Modifier.semantics { contentDescription="${c.kind.title} · ${c.zone()}" })
                    }
                    else->Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(20.dp)) {
                        SecondaryAction("−",enabled && value>c.min,Modifier.sizeIn(minWidth=64.dp).semantics { contentDescription="${c.kind.title} 낮추기 · ${c.zone()}" }) { s.setTarget(c,value-c.step) }
                        Column(Modifier.weight(1f),horizontalAlignment=Alignment.CenterHorizontally) {
                            Text(controlValue(c,value),style=MaterialTheme.typography.displaySmall,color=if(draft!=null)CampAccent else CampInk)
                            if(draft!=null) Caption("변경 예정")
                        }
                        SecondaryAction("+",enabled && value<c.max,Modifier.sizeIn(minWidth=64.dp).semantics { contentDescription="${c.kind.title} 높이기 · ${c.zone()}" }) { s.setTarget(c,value+c.step) }
                    }
                }
            } else if(c.writable) Caption("현재값 수신 대기 · 공조 전원과 콘솔 연결을 확인하세요")
            s.results[c.id]?.let { Text(it,color=if(it.startsWith("실패"))CampWarning else CampMuted,style=MaterialTheme.typography.bodySmall) }
        }
    }
}
