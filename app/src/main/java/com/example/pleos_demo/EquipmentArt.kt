package com.example.pleos_demo

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

/** Shared flat artwork: the exact same appliance silhouette in registration, selection and execution. */
@Composable internal fun EquipmentArt(name:String,modifier:Modifier=Modifier) {
    Canvas(modifier.size(112.dp).semantics { contentDescription="$name 그림" }) {
        scale(size.width/160f,size.height/120f,pivot=Offset.Zero) {
            val ink=Color(0xFF526681);val blue=Color(0xFFBFD6FF);val light=Color(0xFFEAF2FF)
            fun box(x:Float,y:Float,w:Float,h:Float,color:Color,r:Float=10f) =
                drawRoundRect(color,Offset(x,y),Size(w,h),CornerRadius(r,r))
            when {
                name.contains("전자") && (name.contains("레인지") || name.contains("렌지")) -> {
                    box(10f,25f,140f,80f,ink);box(15f,30f,130f,70f,light)
                    box(23f,39f,88f,51f,blue);box(31f,46f,70f,35f,Color.White)
                    drawCircle(ink,7f,Offset(128f,51f));box(121f,69f,14f,5f,ink)
                    box(25f,103f,18f,6f,ink);box(117f,103f,18f,6f,ink)
                }
                name.contains("에어") -> {
                    box(35f,14f,90f,96f,ink,22f);box(40f,19f,80f,86f,light,18f)
                    box(58f,29f,44f,18f,ink);drawCircle(blue,4f,Offset(80f,38f))
                    box(45f,58f,70f,40f,blue);box(70f,61f,20f,25f,ink,6f)
                }
                name.contains("포트") || name.contains("주전자") -> {
                    drawCircle(ink,23f,Offset(112f,64f),style=Stroke(9f))
                    box(38f,33f,67f,67f,blue,20f);box(49f,22f,45f,12f,ink)
                    box(29f,99f,88f,9f,ink);drawLine(blue,Offset(43f,45f),Offset(22f,37f),13f)
                    box(60f,48f,12f,35f,Color.White,6f)
                }
                name.contains("프로젝") || name.contains("빔") -> {
                    box(21f,38f,118f,60f,light);drawCircle(ink,23f,Offset(103f,68f))
                    drawCircle(blue,16f,Offset(103f,68f));drawCircle(Color.White,6f,Offset(98f,63f))
                    repeat(3) { box(34f,53f+it*10,30f,4f,ink,2f) }
                }
                name.contains("장판") || name.contains("매트") -> {
                    box(19f,23f,112f,78f,blue,15f)
                    repeat(5) { box(28f+it*20,32f,8f,60f,light,4f) }
                    drawLine(ink,Offset(130f,75f),Offset(145f,75f),4f)
                    box(139f,66f,15f,29f,ink,5f)
                }
                name.contains("인덕션") -> {
                    box(15f,25f,130f,80f,ink)
                    drawCircle(blue,28f,Offset(66f,64f),style=Stroke(5f))
                    drawCircle(blue,18f,Offset(66f,64f),style=Stroke(3f))
                    drawCircle(Color.White,5f,Offset(122f,83f))
                }
                name.contains("조명") || name.contains("랜턴") -> {
                    drawCircle(ink,17f,Offset(80f,29f),style=Stroke(5f))
                    box(47f,40f,66f,55f,blue,14f);box(57f,48f,46f,36f,light,8f)
                    box(40f,91f,80f,13f,ink);box(48f,34f,64f,10f,ink)
                    drawLine(Color.White,Offset(80f,52f),Offset(80f,82f),8f)
                }
                name.contains("냉장") -> {
                    box(43f,12f,74f,99f,light);box(48f,17f,64f,89f,blue)
                    drawLine(ink,Offset(48f,48f),Offset(112f,48f),3f)
                    box(99f,27f,5f,12f,ink,2f);box(99f,58f,5f,23f,ink,2f)
                }
                else -> {
                    box(47f,42f,66f,47f,blue,15f)
                    drawLine(ink,Offset(64f,42f),Offset(64f,23f),7f)
                    drawLine(ink,Offset(96f,42f),Offset(96f,23f),7f)
                    drawLine(ink,Offset(80f,89f),Offset(80f,112f),7f)
                }
            }
        }
    }
}
