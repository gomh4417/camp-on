package com.example.pleos_demo

import android.graphics.ImageDecoder
import android.graphics.drawable.Animatable2
import android.graphics.drawable.AnimatedImageDrawable
import android.graphics.drawable.Drawable
import android.widget.ImageView
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.size
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class CampAnimationTest {
    @get:Rule val compose=createComposeRule()
    @Test fun allTransitionsFinishOnceAndLoopsRepeat() {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        lateinit var view:ImageView
        compose.setContent { AndroidView(factory={ImageView(it).also { v->view=v }},modifier=Modifier.size(320.dp,180.dp)) }
        compose.waitForIdle()
        CampDayPhase.entries.forEach { phase->
            val transition=ImageDecoder.decodeDrawable(ImageDecoder.createSource(context.assets,"camping/transitions/${phase.transition}")) { decoder,_,_->decoder.setTargetSize(320,180) } as AnimatedImageDrawable
            val ended=CountDownLatch(1)
            val callback=object:Animatable2.AnimationCallback() {
                override fun onAnimationEnd(drawable:Drawable?) { ended.countDown() }
            }
            compose.runOnIdle {
                transition.repeatCount=0
                transition.registerAnimationCallback(callback)
                view.setImageDrawable(transition)
                transition.start()
            }
            assertTrue("${phase.transition} must finish once",ended.await(18,TimeUnit.SECONDS))
            compose.runOnIdle {
                assertFalse(transition.isRunning)
                transition.unregisterAnimationCallback(callback)
                transition.stop()
            }
            val loop=ImageDecoder.decodeDrawable(ImageDecoder.createSource(context.assets,"camping/loops/${phase.loop}")) { decoder,_,_->decoder.setTargetSize(320,180) } as AnimatedImageDrawable
            compose.runOnIdle {
                loop.repeatCount=AnimatedImageDrawable.REPEAT_INFINITE
                view.setImageDrawable(loop)
                loop.start()
                assertTrue(loop.isRunning)
                loop.stop()
                assertFalse(loop.isRunning)
            }
        }
    }

    @Test fun composableSwitchesTransitionToNewLoop() {
        val instrumentation=InstrumentationRegistry.getInstrumentation()
        val before=java.time.ZonedDateTime.of(2026,11,14,18,59,59,0,java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
        val clock=androidx.compose.runtime.mutableLongStateOf(before)
        compose.setContent { CampScene(clock.longValue,false,Modifier.size(640.dp,360.dp)) }
        fun findImage(v:android.view.View):ImageView? {
            if(v is ImageView)return v
            if(v is android.view.ViewGroup)for(i in 0 until v.childCount)findImage(v.getChildAt(i))?.let { return it }
            return null
        }
        fun current():AnimatedImageDrawable? {
            var found:AnimatedImageDrawable?=null
            instrumentation.runOnMainSync {
                val activity=androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry.getInstance()
                    .getActivitiesInStage(androidx.test.runner.lifecycle.Stage.RESUMED).firstOrNull()
                found=activity?.window?.decorView?.let { findImage(it) }?.drawable as? AnimatedImageDrawable
            }
            return found
        }
        compose.waitUntil(10000) { current()?.repeatCount==AnimatedImageDrawable.REPEAT_INFINITE }
        val dusk=current()
        compose.runOnIdle { clock.longValue=before+2000 }
        compose.waitUntil(10000) { current()?.let { it!==dusk && it.repeatCount==0 }==true }
        val transition=current()
        compose.waitUntil(16000) { current()?.let { it!==transition && it.repeatCount==AnimatedImageDrawable.REPEAT_INFINITE && it.isRunning }==true }
        assertNotSame(dusk,current())
    }
}
