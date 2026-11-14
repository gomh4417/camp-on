package com.example.pleos_demo

import androidx.compose.foundation.background
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

internal val CampInk=Color(0xFF182435)
internal val CampMuted=Color(0xFF7B8798)
internal val CampAccent=Color(0xFF2468EE)
internal val CampBackground=Color(0xFFF4F7FC)
internal val CampSurface=Color(0xFFFFFFFF)
internal val CampLine=Color(0xFFE2E7EE)
internal val CampWarning=Color(0xFF92510A)
@OptIn(androidx.compose.ui.text.ExperimentalTextApi::class)
private val CampFont=FontFamily(
    Font(R.font.asta_sans,FontWeight.Normal,variationSettings=androidx.compose.ui.text.font.FontVariation.Settings(androidx.compose.ui.text.font.FontVariation.weight(400))),
    Font(R.font.asta_sans,FontWeight.Medium,variationSettings=androidx.compose.ui.text.font.FontVariation.Settings(androidx.compose.ui.text.font.FontVariation.weight(500))),
    Font(R.font.asta_sans,FontWeight.ExtraBold,variationSettings=androidx.compose.ui.text.font.FontVariation.Settings(androidx.compose.ui.text.font.FontVariation.weight(800))))
private fun type(size:Int,weight:FontWeight=FontWeight.Normal)=TextStyle(
    fontFamily=CampFont,fontSize=size.sp,lineHeight=(size*1.4).sp,fontWeight=weight)
private val CampTypography=Typography(
    displayLarge=type(76,FontWeight.ExtraBold),displayMedium=type(56,FontWeight.ExtraBold),displaySmall=type(44,FontWeight.ExtraBold),
    headlineLarge=type(36,FontWeight.ExtraBold),headlineMedium=type(30,FontWeight.ExtraBold),headlineSmall=type(26,FontWeight.ExtraBold),
    titleLarge=type(24,FontWeight.Medium),titleMedium=type(20,FontWeight.Medium),titleSmall=type(18,FontWeight.Medium),
    bodyLarge=type(20),bodyMedium=type(18),bodySmall=type(16),
    labelLarge=type(18,FontWeight.Medium),labelMedium=type(16,FontWeight.Medium),labelSmall=type(14,FontWeight.Medium))
@Composable internal fun CampTheme(content:@Composable ()->Unit) {
    MaterialTheme(typography=CampTypography,colorScheme=lightColorScheme(
        primary=CampAccent,onPrimary=Color.White,primaryContainer=Color(0xFFEAF2FF),onPrimaryContainer=CampInk,
        secondary=CampAccent,onSecondary=CampBackground,secondaryContainer=Color(0xFFEAF2FF),onSecondaryContainer=CampInk,
        background=CampBackground,onBackground=CampInk,
        surface=CampSurface,onSurface=CampInk,surfaceVariant=Color(0xFFF0F3F7),onSurfaceVariant=CampMuted,
        outline=CampLine,error=CampWarning,onError=CampBackground),content=content)
}
@Composable internal fun Panel(modifier:Modifier=Modifier,emphasis:Boolean=false,content:@Composable ColumnScope.()->Unit) {
    Column(modifier.background(CampSurface,RoundedCornerShape(24.dp))
        .padding(24.dp),verticalArrangement=Arrangement.spacedBy(16.dp),content=content)
}
@Composable internal fun Caption(text:String) { Text(text,color=CampMuted,style=MaterialTheme.typography.bodySmall) }
@Composable internal fun PrimaryAction(text:String,enabled:Boolean=true,modifier:Modifier=Modifier,action:()->Unit) {
    Button(action,modifier.heightIn(min=72.dp),enabled=enabled,shape=RoundedCornerShape(20.dp),
        contentPadding=PaddingValues(horizontal=24.dp,vertical=14.dp)) { Text(text) }
}
@Composable internal fun SecondaryAction(text:String,enabled:Boolean=true,modifier:Modifier=Modifier,action:()->Unit) {
    FilledTonalButton(action,modifier.heightIn(min=72.dp),enabled=enabled,shape=RoundedCornerShape(16.dp),
        colors=ButtonDefaults.filledTonalButtonColors(containerColor=Color(0xFFF0F1F3),contentColor=CampInk)) { Text(text) }
}
