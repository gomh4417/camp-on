package com.example.pleos_demo

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp

@Composable internal fun ModeApplyPreview(s:CampSession,onlyEdited:Boolean=false) {
    val controls=s.plannedControls.filter { !onlyEdited || it.id in s.overrides }
    fun current(c:VehicleControl)=s.current[c.id].takeIf { s.fresh && s.reading.isFresh(c.id,s.now) }
    var after by remember { mutableStateOf(true) }
    var details by remember { mutableStateOf(false) }
    val changed=controls.filter { !canRestore(current(it),s.target(it)) }
    Column(verticalArrangement=Arrangement.spacedBy(10.dp)) {
    Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(16.dp)) {
        ModeArt(s.selected,Modifier.size(80.dp))
        Column(verticalArrangement=Arrangement.spacedBy(10.dp)) {
            Text("차 안이 이렇게 달라져요",style=MaterialTheme.typography.titleLarge)
            Caption("변경 예정 ${changed.size}개")
        }
    }
    Row(horizontalArrangement=Arrangement.spacedBy(12.dp)) {
        FilterChip(!after,{after=false},label={Text("현재 차량")},modifier=Modifier.heightIn(min=56.dp))
        FilterChip(after,{after=true},label={Text("적용 후 예상")},modifier=Modifier.heightIn(min=56.dp))
    }
    fun value(c:VehicleControl)=if(after)s.target(c) else current(c)
    val light=controls.firstOrNull { it.kind==ControlKind.LIGHT }
    val brightness=light?.let { c->value(c)?.let { ((it-c.min)/(c.max-c.min).coerceAtLeast(.001)).coerceIn(0.0,1.0) } }
    BoxWithConstraints(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp))
        .background(if(after)Color(0xFFF0F5FF) else CampBackground).padding(16.dp)) {
        val labelWidth=maxWidth*.24f
        val carWidth=minOf(maxWidth*.46f,240.dp)
        val carHeight=carWidth*2
        Box(Modifier.fillMaxWidth().height(carHeight),contentAlignment=Alignment.Center) {
            Box(Modifier.width(carWidth).height(carHeight)) {
                if(brightness!=null) Box(Modifier.fillMaxSize().padding(horizontal=14.dp,vertical=30.dp)
                    .clip(RoundedCornerShape(64.dp)).background(Color(0xFFFFC85A).copy(alpha=(brightness*.5).toFloat())))
                Image(painterResource(R.drawable.cabin_flat_shell),null,Modifier.fillMaxSize(),contentScale=ContentScale.FillBounds)
                cabinSeats.forEach { seat->
                    val local=controls.filter { seat.area in it.id.substringAfter(":").split("+") }
                    val heat=local.firstOrNull { it.kind==ControlKind.SEAT_TEMPERATURE }?.let { value(it) }
                    val touched=after && local.any { it in changed }
                    val tint=when { heat==null || heat==0.0->if(touched)Color(0xFFADCFFF) else Color.White
                        heat>0->Color(0xFFFFC8AE);else->Color(0xFFADCFFF) }
                    Box(Modifier.offset(carWidth*seat.x,carHeight*seat.y).width(carWidth*seat.width).height(carHeight*.16f)
                        .border(if(touched)2.dp else 0.dp,if(touched)CampAccent else Color.Transparent,RoundedCornerShape(10.dp))) {
                        Image(painterResource(R.drawable.cabin_flat_seat),seat.label,Modifier.fillMaxSize(),contentScale=ContentScale.FillBounds,
                            colorFilter=ColorFilter.tint(tint,BlendMode.Modulate))
                        if(heat!=null) Text(if(heat>0)"열선" else if(heat<0)"통풍" else "OFF",
                            Modifier.align(Alignment.BottomCenter).background(Color.White,RoundedCornerShape(6.dp)).padding(3.dp),
                            style=MaterialTheme.typography.labelSmall,color=CampInk)
                    }
                }
            }
            listOf("ROW_1_LEFT" to Alignment.CenterStart,"ROW_1_RIGHT" to Alignment.CenterEnd).forEach { (area,align)->
                val temp=controls.firstOrNull { it.kind==ControlKind.TEMPERATURE && area in it.id.substringAfter(":").split("+") }
                Column(Modifier.align(align).width(labelWidth),verticalArrangement=Arrangement.spacedBy(8.dp),horizontalAlignment=Alignment.CenterHorizontally) {
                    Caption(if(area.endsWith("LEFT"))"운전석" else "동승석")
                    Text(temp?.let { value(it).fmt()+"°" }?:"—",style=MaterialTheme.typography.headlineMedium,color=CampAccent)
                    if(temp!=null && after) Caption("현재 ${current(temp).fmt()}°")
                }
            }
        }
    }
    Caption(if(after)"파란 테두리: 바뀌는 좌석 · 주황: 열선 · 파랑: 통풍" else "차량에서 최근 수신한 상태")
    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(12.dp)) {
        val fan=controls.firstOrNull { it.kind==ControlKind.FAN }
        SummaryTile("바람",fan?.let { controlValue(it,value(it)) }?:"변경 없음",Modifier.weight(1f))
        SummaryTile("실내 조명",light?.let { controlValue(it,value(it)) }?:"변경 없음",Modifier.weight(1f))
    }
    val power=controls.firstOrNull { it.kind==ControlKind.POWER }
    val ac=controls.firstOrNull { it.kind==ControlKind.AC }
    Caption(listOfNotNull(power?.let { "공조 ${controlValue(it,value(it))}" },ac?.let { "A/C ${controlValue(it,value(it))}" }).joinToString(" · "))
    if(!onlyEdited) {
        val capacity=s.capacityKwh?.takeIf { it>0 }
        val beforeEnd=capacity?.let { s.budget?.departure?.div(it)?.times(100) }
        val afterEnd=capacity?.let { s.previewBudget?.departure?.div(it)?.times(100) }
        Text("캠핑이 끝날 때 배터리",style=MaterialTheme.typography.titleMedium)
        BatteryPreviewBar("현재 설정 유지",beforeEnd,Color(0xFF98A5B8))
        BatteryPreviewBar("${s.selected.label} 적용",afterEnd,CampAccent)
        Caption("현재 ${s.soc.fmt()}% · 적용 후 남은 소비 ${s.previewBudget?.consumption.fmt()} kWh 예상")
    }
    TextButton(onClick={details=!details},modifier=Modifier.heightIn(min=56.dp)) { Text(if(details)"상세 변경값 접기" else "상세 변경값 보기 · ${controls.size}개") }
    if(details) controls.forEach { c->
        Text(c.label,style=MaterialTheme.typography.labelMedium)
        Text("${controlValue(c,current(c))}  →  ${controlValue(c,s.target(c))}",color=if(c in changed)CampAccent else CampMuted)
    }
    Caption("확인하고 적용을 눌러야 차량에 전송돼요.")
    }
}
@Composable private fun SummaryTile(title:String,value:String,modifier:Modifier) {
    Column(modifier.clip(RoundedCornerShape(16.dp)).background(CampBackground).padding(16.dp),
        verticalArrangement=Arrangement.spacedBy(8.dp)) {
        Caption(title);Text(value,style=MaterialTheme.typography.titleMedium)
    }
}
@Composable private fun BatteryPreviewBar(label:String,percent:Double?,color:Color) {
    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
        Caption(label);Text(percent.fmt()+"%",color=color,style=MaterialTheme.typography.titleMedium)
    }
    Box(Modifier.fillMaxWidth().height(28.dp).clip(RoundedCornerShape(8.dp)).background(CampLine)) {
        if(percent!=null)Box(Modifier.fillMaxWidth((percent/100).toFloat().coerceIn(0f,1f)).fillMaxHeight().background(color))
    }
}
