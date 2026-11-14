package com.example.pleos_demo

import android.animation.ValueAnimator
import android.graphics.ImageDecoder
import android.graphics.drawable.Animatable2
import android.graphics.drawable.AnimatedImageDrawable
import android.graphics.drawable.Drawable
import android.widget.ImageView
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.ZoneId

internal enum class CampDayPhase(val loop:String,val transition:String) {
    DAWN("01_dawn_loop.gif","01_dawn_to_day.gif"),
    DAY("02_day_loop.gif","02_day_to_dusk.gif"),
    DUSK("03_dusk_loop.gif","03_dusk_to_night.gif"),
    NIGHT("04_night_loop.gif","04_night_to_dawn.gif");
    companion object {
        fun atHour(hour:Int):CampDayPhase {
            require(hour in 0..23)
            return when(hour) { in 5..10->DAWN;in 11..15->DAY;in 16..18->DUSK;else->NIGHT }
        }
        fun at(time:Long,zone:ZoneId=ZoneId.systemDefault())=atHour(Instant.ofEpochMilli(time).atZone(zone).hour)
    }
}
internal fun sceneTransition(from:CampDayPhase,to:CampDayPhase)=
    from.transition.takeIf { (from.ordinal+1)%4==to.ordinal }

/** App clock drives the scene. Only consecutive, foreground phase changes play a transition. */
@Composable internal fun CampScene(now:Long,still:Boolean,modifier:Modifier=Modifier) {
    val context=LocalContext.current
    val lifecycle=LocalLifecycleOwner.current.lifecycle
    var foreground by remember { mutableStateOf(lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) }
    var previous by remember { mutableStateOf(CampDayPhase.at(now)) }
    var transition by remember { mutableStateOf<String?>(null) }
    var decoded by remember { mutableStateOf<Pair<String,Drawable>?>(null) }
    val drawable=decoded?.second
    var failed by remember { mutableStateOf(false) }
    val phase=CampDayPhase.at(now)
    val animate=foreground && !still && ValueAnimator.areAnimatorsEnabled()
    DisposableEffect(lifecycle) {
        val observer=LifecycleEventObserver { _,_ -> foreground=lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED) }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(phase,animate) {
        transition=if(animate && previous!=phase)sceneTransition(previous,phase) else null
        previous=phase
    }
    val asset=if(transition!=null && animate)"camping/transitions/$transition" else "camping/loops/${phase.loop}"
    val onEnd by rememberUpdatedState { transition=null }
    LaunchedEffect(asset,animate) {
        (drawable as? AnimatedImageDrawable)?.stop()
        failed=false
        decoded=try {
            asset to withContext(Dispatchers.IO) {
                ImageDecoder.decodeDrawable(ImageDecoder.createSource(context.assets,asset)) { decoder,info,_ ->
                    val width=minOf(info.size.width,1200)
                    decoder.setTargetSize(width,width*info.size.height/info.size.width)
                }
            }
        } catch(e:kotlinx.coroutines.CancellationException) { throw e }
        catch(_:Exception) { failed=true;null }
    }
    DisposableEffect(decoded,animate,asset) {
        val decodedAsset=decoded?.first
        val animated=drawable as? AnimatedImageDrawable
        val callback=object:Animatable2.AnimationCallback() {
            override fun onAnimationEnd(drawable:Drawable?) { if(decodedAsset==asset && decodedAsset?.contains("/transitions/")==true)onEnd() }
        }
        if(animated!=null) {
            animated.repeatCount=if(decodedAsset?.contains("/transitions/")==true)0 else AnimatedImageDrawable.REPEAT_INFINITE
            animated.registerAnimationCallback(callback)
            if(animate && decodedAsset==asset)animated.start() else animated.stop()
        }
        onDispose { animated?.unregisterAnimationCallback(callback);animated?.stop() }
    }
    AndroidView(factory={ImageView(it).apply { scaleType=ImageView.ScaleType.CENTER_CROP }},
        update={view->if(view.drawable!==drawable)view.setImageDrawable(drawable)
            view.contentDescription=if(failed)"캠핑 장면을 불러오지 못했어요" else "캠핑장 · ${phase.name}"},
        modifier=modifier)
}
