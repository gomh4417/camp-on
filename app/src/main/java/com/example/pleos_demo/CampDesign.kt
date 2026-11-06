package com.example.pleos_demo

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

internal val CampInk=Color(0xFFF4F2EE)
internal val CampMuted=Color(0xFFB7BCC4)
internal val CampAccent=Color(0xFFCEDDF3)
internal val CampBackground=Color(0xFF111419)
internal val CampSurface=Color(0xFF20242B)
internal val CampLine=Color(0xFF414751)
internal val CampWarning=Color(0xFFF3C18C)
private val CampFont=FontFamily(Font(R.font.pretendard_regular,FontWeight.Normal),
    Font(R.font.pretendard_medium,FontWeight.Medium),Font(R.font.pretendard_extrabold,FontWeight.ExtraBold))
private fun type(size:Int,weight:FontWeight=FontWeight.Normal)=TextStyle(
    fontFamily=CampFont,fontSize=size.sp,lineHeight=(size*1.4).sp,fontWeight=weight)
private val CampTypography=Typography(
    displayLarge=type(76,FontWeight.ExtraBold),displayMedium=type(56,FontWeight.ExtraBold),displaySmall=type(44,FontWeight.ExtraBold),
    headlineLarge=type(36,FontWeight.ExtraBold),headlineMedium=type(30,FontWeight.ExtraBold),headlineSmall=type(26,FontWeight.ExtraBold),
    titleLarge=type(24,FontWeight.Medium),titleMedium=type(20,FontWeight.Medium),titleSmall=type(18,FontWeight.Medium),
    bodyLarge=type(20),bodyMedium=type(18),bodySmall=type(16),
    labelLarge=type(18,FontWeight.Medium),labelMedium=type(16,FontWeight.Medium),labelSmall=type(14,FontWeight.Medium))
@Composable internal fun CampTheme(content:@Composable ()->Unit) {
    MaterialTheme(typography=CampTypography,colorScheme=darkColorScheme(
        primary=CampAccent,onPrimary=Color(0xFF192332),primaryContainer=Color(0xFF303F53),onPrimaryContainer=CampInk,
        secondary=CampAccent,onSecondary=CampBackground,background=CampBackground,onBackground=CampInk,
        surface=CampSurface,onSurface=CampInk,surfaceVariant=Color(0xFF2A3039),onSurfaceVariant=CampMuted,
        outline=CampLine,error=CampWarning,onError=CampBackground),content=content)
}
@Composable internal fun Panel(modifier:Modifier=Modifier,emphasis:Boolean=false,content:@Composable ColumnScope.()->Unit) {
    Column(modifier.background(if(emphasis) Color(0xFF29323F) else CampSurface,RoundedCornerShape(24.dp))
        .border(1.dp,if(emphasis) Color(0xFF536277) else CampLine.copy(alpha=.65f),RoundedCornerShape(24.dp))
        .padding(24.dp),verticalArrangement=Arrangement.spacedBy(18.dp),content=content)
}
@Composable internal fun Caption(text:String) { Text(text,color=CampMuted,style=MaterialTheme.typography.bodySmall) }
@Composable internal fun PrimaryAction(text:String,enabled:Boolean=true,modifier:Modifier=Modifier,action:()->Unit) {
    Button(action,modifier.heightIn(min=56.dp),enabled=enabled,shape=RoundedCornerShape(14.dp),
        contentPadding=PaddingValues(horizontal=24.dp,vertical=14.dp)) { Text(text) }
}
@Composable internal fun SecondaryAction(text:String,enabled:Boolean=true,modifier:Modifier=Modifier,action:()->Unit) {
    OutlinedButton(action,modifier.heightIn(min=56.dp),enabled=enabled,shape=RoundedCornerShape(14.dp)) { Text(text) }
}
