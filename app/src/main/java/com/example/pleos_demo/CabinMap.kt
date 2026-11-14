package com.example.pleos_demo

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

internal data class CabinSeat(val area:String,val label:String,val x:Float,val y:Float,val width:Float=.29f)
internal val cabinSeats=listOf(
    CabinSeat("ROW_1_LEFT","운전석",.18f,.35f),
    CabinSeat("ROW_1_RIGHT","동승석",.53f,.35f),
    CabinSeat("ROW_2_LEFT","2열 왼쪽",.16f,.56f,.23f),
    CabinSeat("ROW_2_CENTER","2열 가운데",.385f,.56f,.23f),
    CabinSeat("ROW_2_RIGHT","2열 오른쪽",.61f,.56f,.23f),
    CabinSeat("ROW_3_LEFT","3열 왼쪽",.20f,.76f),
    CabinSeat("ROW_3_RIGHT","3열 오른쪽",.51f,.76f)
)

@Composable internal fun CabinPlan(s:CampSession,temperatures:List<VehicleControl>,modifier:Modifier) {
    var selected by rememberSaveable { mutableStateOf("ROW_1_LEFT") }
    val seat=cabinSeats.first { it.area==selected }
    val matching=temperatures.filter { c->c.id.substringAfter(":").split("+").contains(selected) }
    Column(modifier.clip(RoundedCornerShape(28.dp)).background(CampSurface).padding(24.dp),
        verticalArrangement=Arrangement.spacedBy(16.dp)) {
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically) {
            Column(verticalArrangement=Arrangement.spacedBy(10.dp)) {
                Text("어느 자리를 편하게 할까요?",style=MaterialTheme.typography.titleLarge)
                Caption("좌석을 누르면 여기서 조절할 수 있어요")
            }
            Text("실내 ${s.reading.cabin.takeIf { s.fresh && s.reading.isFresh("실내 온도",s.now) }.fmt()}°C",
                color=CampMuted,style=MaterialTheme.typography.bodyMedium)
        }
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            if(maxWidth>=700.dp) {
                val height=(maxWidth*.80f).coerceIn(600.dp,820.dp)
                Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(24.dp)) {
                    SeatMap(selected,{selected=it},Modifier.weight(1.15f).height(height))
                    Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(16.dp)) {
                        SelectionControls(s,seat.label,matching)
                    }
                }
            } else Column(verticalArrangement=Arrangement.spacedBy(16.dp)) {
                SeatMap(selected,{selected=it},Modifier.fillMaxWidth().height(540.dp))
                SelectionControls(s,seat.label,matching)
            }
        }
    }
}
@Composable private fun SelectionControls(s:CampSession,label:String,controls:List<VehicleControl>) {
    Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(10.dp)) {
        Text("✓",color=CampAccent,style=MaterialTheme.typography.titleLarge)
        Text(label,style=MaterialTheme.typography.headlineSmall)
    }
    if(controls.isEmpty()) {
        Text("이 자리의 조절 기능은\n아직 연결되지 않았어요",style=MaterialTheme.typography.titleMedium)
        Caption("차량에서 지원하는 기능만 표시해요")
    } else controls.forEach { c->DirectControl(s,c,true) }
}
@Composable private fun SeatMap(selected:String,onSelect:(String)->Unit,modifier:Modifier) {
    BoxWithConstraints(modifier,contentAlignment=Alignment.Center) {
        val carHeight=minOf(maxHeight,maxWidth*2f)
        val carWidth=carHeight/2f
        Box(Modifier.width(carWidth).height(carHeight).selectableGroup()) {
            Image(painterResource(R.drawable.cabin_flat_shell),null,Modifier.fillMaxSize(),contentScale=ContentScale.FillBounds)
            cabinSeats.forEach { seat->
                val active=seat.area==selected
                Box(Modifier.offset(x=carWidth*seat.x,y=carHeight*seat.y)
                    .width((carWidth*seat.width).coerceAtLeast(56.dp)).height((carHeight*.16f).coerceAtLeast(56.dp))
                    .clip(RoundedCornerShape(18.dp))
                    .background(if(active)Color(0xFFDCEAFF) else Color.Transparent)
                    .border(if(active)2.dp else 0.dp,if(active)CampAccent else Color.Transparent,RoundedCornerShape(18.dp))
                    .selectable(active,role=Role.RadioButton,onClick={onSelect(seat.area)})
                    .semantics { contentDescription=seat.label },
                    contentAlignment=Alignment.Center) {
                    Image(painterResource(R.drawable.cabin_flat_seat),null,
                        Modifier.fillMaxSize().padding(if(active)6.dp else 3.dp),
                        contentScale=ContentScale.FillBounds,
                        colorFilter=if(active)ColorFilter.tint(Color(0xFFADCFFF),BlendMode.Modulate) else null)
                    if(active) Text("✓",Modifier.align(Alignment.BottomCenter).background(CampAccent,RoundedCornerShape(8.dp)).padding(horizontal=8.dp),
                        color=Color.White,style=MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
}
