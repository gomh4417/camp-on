package com.example.pleos_demo

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

internal fun cabinLightStatus(power:String?,brightness:Double?):String = when(power?.uppercase()) {
    "OFF","꺼짐" -> "OFF"
    "ON","켜짐" -> brightness?.let { "ON · ${it.toInt()}%" } ?: "ON · 밝기 확인 필요"
    "AUTO","자동" -> "AUTO"
    else -> "상태 확인 필요"
}
@Composable internal fun FHome(s:CampSession,still:Boolean,modifier:Modifier,onPanel:(String)->Unit,
    onStill:()->Unit,onMode:(CampMode)->Unit,onTemperature:(Int)->Unit) {
    val temp=s.observed(ControlKind.TEMPERATURE)
    val auto=s.observed(ControlKind.AUTO)
    val power=s.observed(ControlKind.CABIN_LIGHT)
    val light=s.observed(ControlKind.LIGHT)
    val lightLabel=cabinLightStatus(power?.second?.let { power.first.choices[it] },light?.second)
    val b=s.budget.takeIf { s.endValid() }
    Column(modifier,verticalArrangement=Arrangement.spacedBy(20.dp)) {
        Row(Modifier.fillMaxWidth().height(96.dp),verticalAlignment=Alignment.CenterVertically) {
            Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(10.dp)) {
                Text("캠핑의 밤",fontSize=36.sp,fontWeight=FontWeight.ExtraBold,color=FInk)
                Text("차 안의 편안함을, 캠핑장까지",fontSize=21.sp,color=FMuted)
            }
            Row(Modifier.width(676.dp).height(76.dp).clip(RoundedCornerShape(20.dp)).background(Color(0xFFE8EDF5)).padding(8.dp),
                horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                CampMode.visible.forEach { mode->
                    val selected=s.selected==mode
                    val color=if(selected)Color.White else FMuted
                    Row(Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(16.dp)).background(if(selected)FBlue else Color.Transparent)
                        .clickable(enabled=!s.busy,role=Role.Tab){onMode(mode)}.semantics { this.selected=selected },
                        horizontalArrangement=Arrangement.spacedBy(10.dp,Alignment.CenterHorizontally),verticalAlignment=Alignment.CenterVertically) {
                        Image(painterResource(when(mode){CampMode.COOK->R.drawable.figma_vector_cook;CampMode.SLEEP->R.drawable.camp_bed;else->R.drawable.figma_seat}),
                            null,Modifier.size(28.dp),colorFilter=ColorFilter.tint(color))
                        Text(mode.label,fontSize=24.sp,fontWeight=FontWeight.Medium,color=color,textAlign=TextAlign.Center)
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth().weight(1f),horizontalArrangement=Arrangement.spacedBy(24.dp)) {
            Box(Modifier.weight(960f).fillMaxHeight().clip(RoundedCornerShape(28.dp)).background(FPale)) {
                CampScene(s.now,still,Modifier.fillMaxSize())
                Text(if(s.active)"●  ${s.mode.label} · ${s.modeStatus}" else "●  캠핑 준비",
                    Modifier.padding(24.dp).clip(RoundedCornerShape(23.dp)).background(Color.White).padding(horizontal=18.dp,vertical=10.dp),
                    color=FBlue,fontSize=20.sp,fontWeight=FontWeight.Medium)
            }
            Column(Modifier.weight(536f).fillMaxHeight().clip(RoundedCornerShape(28.dp)).background(Color.White).padding(28.dp),
                verticalArrangement=Arrangement.spacedBy(12.dp)) {
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(10.dp)) {
                    Text("계획 후 남는 전력",fontSize=22.sp,color=FMuted)
                    Row(verticalAlignment=Alignment.Bottom,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                        Text(b?.margin.fmt(),fontSize=64.sp,fontWeight=FontWeight.ExtraBold,color=FInk)
                        Text("kWh",Modifier.padding(bottom=12.dp),fontSize=26.sp,color=FMuted)
                    }
                    Text(if(s.endValid())"귀가·예비·예상 사용량을 제외한 여유" else "종료 날짜·시각을 다시 선택해 주세요",fontSize=20.sp,color=FMuted)
                    Row(Modifier.fillMaxWidth().height(14.dp).clip(RoundedCornerShape(7.dp)).background(FPale)) {
                        if(b!=null) listOf(b.home to Color(0xFF96A8C1),b.reserve to Color(0xFFBBC8DB),
                            b.consumption to Color(0xFF81AEFF),b.margin to FBlue).forEach { (value,color)->
                            if(value>0)Box(Modifier.weight(value.toFloat()).fillMaxHeight().background(color))
                        }
                    }
                    Row(horizontalArrangement=Arrangement.spacedBy(16.dp)) {
                        EnergyLabel("귀가",b?.home,Color(0xFF96A8C1),Modifier.weight(1f))
                        EnergyLabel("예비",b?.reserve,Color(0xFFBBC8DB),Modifier.weight(1f))
                    }
                    Row(horizontalArrangement=Arrangement.spacedBy(16.dp)) {
                        EnergyLabel("예상 사용",b?.consumption,Color(0xFF81AEFF),Modifier.weight(1f))
                        EnergyLabel("여유",b?.margin,FBlue,Modifier.weight(1f))
                    }
                    Text("현재 배터리 전력 ${b?.current.fmt()} kWh",fontSize=20.sp,color=FMuted)
                    if(b?.continuedShortfall?.let { it>0 }==true)Text("귀가·예비 전력 ${b.continuedShortfall.fmt()} kWh 부족",fontSize=20.sp,color=FBlue)
                }
                Box(Modifier.fillMaxWidth().height(70.dp)) { FButton("에너지 계획 설정  →",0,0,480,70,true){
                    onPanel(if(s.fields["plan.ready"]=="true")"plan" else "setup")
                } }
            }
        }
        Row(Modifier.fillMaxWidth().height(224.dp).clip(RoundedCornerShape(28.dp)).background(Color.White),
            horizontalArrangement=Arrangement.spacedBy(0.dp)) {
            Box(Modifier.weight(1f).fillMaxHeight().semantics { contentDescription="실내 온도 영역" }) {
                Column(Modifier.fillMaxHeight().padding(24.dp).padding(end=84.dp).clickable(role=Role.Button){onPanel("climate")},
                    verticalArrangement=Arrangement.spacedBy(10.dp)) {
                    Text("실내 온도",fontSize=23.sp,fontWeight=FontWeight.Medium)
                    Text(temp?.second?.let { "${it.fmt()}°" }?:"—",fontSize=56.sp,fontWeight=FontWeight.ExtraBold)
                    Text(if(auto?.second==1.0)"자동 공조" else if(auto?.second==0.0)"수동 공조" else "상태 확인 필요",fontSize=19.sp,color=FMuted)
                }
                Column(Modifier.align(Alignment.CenterEnd).padding(end=24.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
                    listOf(1 to "온도 올리기",-1 to "온도 내리기").forEach { (direction,label)->
                        val enabled=temp?.let { (c,v)->v!=null && s.canAdjust(c) && if(direction>0)v<c.max else v>c.min }==true
                        Box(Modifier.size(72.dp,68.dp).clip(RoundedCornerShape(18.dp)).background(FPale).clickable(enabled,role=Role.Button){onTemperature(direction)}
                            .semantics { contentDescription=label },contentAlignment=Alignment.Center) {
                            Image(painterResource(if(direction>0)R.drawable.figma_vector_up else R.drawable.figma_vector_down),null,Modifier.size(30.dp),alpha=if(enabled)1f else .4f)
                        }
                    }
                }
            }
            listOf(Triple("실내 조명",R.drawable.figma_sun,"light"),Triple("시트",R.drawable.figma_seat,"seat"),
                Triple("외부 전원",R.drawable.figma_vector_plug,"plan")).forEachIndexed { i,(label,icon,target)->
                Column(Modifier.weight(1f).fillMaxHeight().clickable(role=Role.Button){onPanel(target)}
                    .semantics { contentDescription="${label} 영역" }.padding(24.dp),
                    verticalArrangement=Arrangement.spacedBy(14.dp)) {
                    Image(painterResource(icon),null,Modifier.size(40.dp))
                    Text(label,fontSize=25.sp,fontWeight=FontWeight.Medium)
                    Text(when(i){0->lightLabel;1->"좌석별 조절";else->"${s.equipmentIds.size}개 등록"},fontSize=22.sp,color=FBlue)
                }
            }
        }
        Row(Modifier.fillMaxWidth().height(64.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(20.dp)) {
            Text("외부 — · 실내 ${s.reading.cabin.takeIf { s.fresh && s.reading.isFresh("실내 온도",s.now) }.fmt()}°",Modifier.weight(1f),fontSize=21.sp,color=FMuted)
            TextButton(onClick=onStill,modifier=Modifier.heightIn(min=56.dp)) { Text(if(still)"정지 화면 · 움직임 켜기" else "정지 화면으로 보기",fontSize=20.sp) }
            Box(Modifier.size(240.dp,64.dp)) { FButton(if(s.active)"캠핑 종료  →" else "캠핑 시작  →",0,0,240,64,enabled=!s.busy){onPanel(if(s.active)"departure" else "start")} }
        }
    }
}
@Composable private fun EnergyLabel(label:String,value:Double?,color:Color,modifier:Modifier) {
    Column(modifier,verticalArrangement=Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            Box(Modifier.size(10.dp).background(color,RoundedCornerShape(5.dp)))
            Text(label,fontSize=19.sp,color=FMuted)
        }
        Text(value.fmt()+" kWh",fontSize=21.sp,fontWeight=FontWeight.Medium,color=FInk)
    }
}
