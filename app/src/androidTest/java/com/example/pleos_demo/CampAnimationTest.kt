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
}
