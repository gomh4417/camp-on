package com.example.pleos_demo

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.unit.dp
import kotlin.math.cos
import kotlin.math.sin

@Composable internal fun ControlIllustration(c:VehicleControl,value:Double) {
    val fraction=((value-c.min)/(c.max-c.min).coerceAtLeast(.001)).coerceIn(0.0,1.0).toFloat()
    Canvas(Modifier.fillMaxWidth().height(130.dp)) {
        val center=Offset(size.width/2,size.height*.72f)
        when(c.kind) {
            ControlKind.LIGHT->{
                val glow=Color(0xFFFFC65A)
                drawCircle(glow.copy(alpha=.08f+fraction*.35f),size.height*.48f,Offset(size.width/2,size.height/2))
                drawCircle(glow.copy(alpha=.2f+fraction*.8f),size.height*.22f,Offset(size.width/2,size.height*.45f))
                drawLine(CampInk,Offset(size.width/2-15.dp.toPx(),size.height*.75f),
                    Offset(size.width/2+15.dp.toPx(),size.height*.75f),8.dp.toPx(),StrokeCap.Round)
            }
            ControlKind.SEAT_POSITION->{
                val recline=c.label.contains("등받이") || c.label.contains("리클")
                val angle=if(recline)(-105-fraction*45)*Math.PI/180 else -110*Math.PI/180
                val pivot=if(recline)center else center.copy(x=center.x+(fraction-.5f)*size.width*.3f)
                val top=Offset(pivot.x+cos(angle).toFloat()*size.height*.6f,pivot.y+sin(angle).toFloat()*size.height*.6f)
                drawLine(CampAccent,top,pivot,22.dp.toPx(),StrokeCap.Round)
                drawLine(CampAccent,pivot,Offset(pivot.x+size.height*.43f,pivot.y),22.dp.toPx(),StrokeCap.Round)
                drawLine(CampLine,Offset(size.width*.2f,size.height*.94f),Offset(size.width*.8f,size.height*.94f),5.dp.toPx(),StrokeCap.Round)
            }
            else->{
                val color=if(value<0)CampAccent else if(value>0)Color(0xFFE78056) else CampLine
                drawRoundRect(color.copy(alpha=.18f),Offset(size.width/2-42.dp.toPx(),8.dp.toPx()),
                    Size(84.dp.toPx(),size.height-16.dp.toPx()),androidx.compose.ui.geometry.CornerRadius(18.dp.toPx()))
                repeat(3) { i->
                    drawLine(color,Offset(size.width/2+(i-1)*20.dp.toPx(),size.height*.3f),
                        Offset(size.width/2+(i-1)*20.dp.toPx(),size.height*.75f),5.dp.toPx(),StrokeCap.Round)
                }
            }
        }
    }
    Text(when(c.kind) {
        ControlKind.LIGHT->"어둡게                          밝게"
        ControlKind.SEAT_TEMPERATURE->"시원하게                       따뜻하게"
        else->"최소 위치                       최대 위치"
    },style=MaterialTheme.typography.labelMedium,color=CampMuted)
}
