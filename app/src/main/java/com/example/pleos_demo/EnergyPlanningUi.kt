package com.example.pleos_demo

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable internal fun ModeArt(mode:CampMode,modifier:Modifier=Modifier) {
    Image(painterResource(when(mode) {
        CampMode.COOK->R.drawable.mode_cook
        CampMode.SLEEP->R.drawable.mode_sleep
        else->R.drawable.mvp_camping
    }),null,modifier)
}

@Composable internal fun BatteryForecast(s:CampSession) {
    val capacity=s.capacityKwh
    val kw=s.climateKw?.let { climate->s.number("baseKw")?.let { it+climate } }
    val points=Energy.forecast(capacity,s.soc,s.remainingHours,kw,s.remainingEquipment())
    Text("시간에 따른 배터리",style=MaterialTheme.typography.titleMedium)
    if(points==null || capacity==null) {
        Caption("배터리·사용 계획을 확인하면 예상 그래프가 나타나요")
        return
    }
    val duration=s.remainingHours?:0.0
    val end=points.last().kwh/capacity*100
    val protected=s.budget?.let { (it.home+it.reserve)/capacity*100 }
    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
        Text("현재 ${s.soc.fmt()}%",color=CampAccent)
        Text("종료 예상 ${end.fmt()}%",style=MaterialTheme.typography.titleMedium)
    }
    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
        Column(Modifier.height(200.dp),verticalArrangement=Arrangement.SpaceBetween) {
            Caption("100%");Caption("50%");Caption("0%")
        }
        Canvas(Modifier.weight(1f).height(200.dp).semantics {
            contentDescription="배터리 현재 ${s.soc.fmt()}퍼센트, ${duration.fmt()}시간 뒤 ${end.fmt()}퍼센트 예상. 귀가와 예비 확보선 ${protected.fmt()}퍼센트"
        }) {
            fun x(h:Double)=(h/duration.coerceAtLeast(.001)*size.width).toFloat()
            fun y(percent:Double)=((1-percent.coerceIn(0.0,100.0)/100)*size.height).toFloat()
            listOf(0.0,25.0,50.0,75.0,100.0).forEach { drawLine(CampLine,Offset(0f,y(it)),Offset(size.width,y(it)),1.dp.toPx()) }
            val fill=Path().apply {
                moveTo(0f,size.height)
                points.forEach { lineTo(x(it.hours),y(it.kwh/capacity*100)) }
                lineTo(x(duration),size.height);close()
            }
            drawPath(fill,Brush.verticalGradient(listOf(CampAccent.copy(alpha=.2f),CampAccent.copy(alpha=.02f))))
            protected?.takeIf { it in 0.0..100.0 }?.let {
                drawLine(CampWarning,Offset(0f,y(it)),Offset(size.width,y(it)),2.dp.toPx(),
                    pathEffect=PathEffect.dashPathEffect(floatArrayOf(10f,8f)))
            }
            points.zipWithNext().forEach { (a,b)->
                drawLine(CampAccent,Offset(x(a.hours),y(a.kwh/capacity*100)),Offset(x(b.hours),y(b.kwh/capacity*100)),3.dp.toPx())
            }
            drawCircle(CampAccent,5.dp.toPx(),Offset(0f,y(points.first().kwh/capacity*100)))
            drawCircle(CampAccent,5.dp.toPx(),Offset(x(duration),y(end)))
        }
    }
    val time=SimpleDateFormat("HH:mm",Locale.KOREA)
    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
        Caption("지금");Caption(time.format(Date(s.now+(duration*1_800_000).toLong())))
        Caption(time.format(Date(s.endMillis?:s.now)))
    }
    Caption("파랑: 예상 잔량 · 점선: 귀가 + 예비 확보량")
    Caption("장비는 지금부터 지정 시간 동안 사용한다고 가정해요.")
}

private val equipmentCatalog=listOf(
    Equipment("인덕션",1200.0,.5),Equipment("전기포트",1500.0,.1),
    Equipment("냉장고",60.0,8.0,.4),Equipment("전기장판",60.0,8.0),
    Equipment("LED 조명",20.0,6.0),Equipment("선풍기",35.0,4.0),
    Equipment("프로젝터",100.0,2.0),Equipment("노트북 충전",65.0,2.0)
)
@Composable internal fun EquipmentPicker(s:CampSession) {
    Text("무엇을 가져왔나요?",style=MaterialTheme.typography.titleLarge)
    Caption("종류를 고르면 소비전력이 채워져요. 제품별 실제 소비는 다를 수 있어요.")
    equipmentCatalog.chunked(2).forEach { pair->
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(12.dp)) {
            pair.forEach { item->
                SecondaryAction("+ ${item.name}",modifier=Modifier.weight(1f)) { s.addEquipment(item) }
            }
        }
    }
    s.equipmentIds.toList().forEach { id->key(id) {
        var advanced by rememberSaveable { mutableStateOf(false) }
        HorizontalDivider(color=CampLine)
        Text(s.fields["name$id"].orEmpty(),style=MaterialTheme.typography.titleMedium)
        Caption("${s.fields["watts$id"]} W · ${s.fields["equipmentSource$id"]?:"입력값"}")
        Row(horizontalArrangement=Arrangement.spacedBy(12.dp)) {
            EquipmentField(s,"hours$id","앞으로 사용 · 시간",Modifier.weight(1f))
            EquipmentField(s,"quantity$id","수량",Modifier.weight(1f))
        }
        Row(horizontalArrangement=Arrangement.spacedBy(12.dp)) {
            TextButton(onClick={advanced=!advanced}) { Text(if(advanced)"접기" else "제품 표시값으로 수정") }
            TextButton(onClick={s.removeEquipment(id)}) { Text("삭제") }
        }
        if(advanced) {
            EquipmentField(s,"name$id","장비 이름")
            EquipmentField(s,"watts$id","제품 정격 소비전력 · W")
            EquipmentField(s,"duty$id","가동 비율 · 0~1")
        }
    } }
    SecondaryAction("+ 다른 장비 직접 입력",modifier=Modifier.fillMaxWidth()) { s.addEquipment() }
}
@Composable private fun EquipmentField(s:CampSession,key:String,label:String,modifier:Modifier=Modifier) {
    val numeric=!key.startsWith("name")
    val value=s.number(key)
    val valid=!numeric || value!=null && value.isFinite() && when {
        key.startsWith("quantity")->value>=1 && value%1==0.0
        key.startsWith("duty")->value in 0.0..1.0
        else->value>=0
    }
    OutlinedTextField(s.fields[key].orEmpty(),{
        s.edit(key,it)
        if(key.startsWith("watts")) s.edit("equipmentSource"+key.removePrefix("watts"),"사용자 입력")
    },modifier.fillMaxWidth(),label={Text(label)},singleLine=true,isError=!valid)
}
