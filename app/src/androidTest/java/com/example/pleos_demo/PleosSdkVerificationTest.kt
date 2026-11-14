package com.example.pleos_demo

import android.os.Build
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.time.LocalDateTime

/** Explicit emulator-only read/write/read-back/restore verification; never runs on a physical car. */
class PleosSdkVerificationTest {
    @Test fun emulatorCapabilitiesReadBackAndRestore()=runBlocking {
        assumeTrue("Only the Pleos emulator may execute this test",Build.HARDWARE=="ranchu" || Build.FINGERPRINT.contains("generic"))
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val name="test_sdk_${System.nanoTime()}"
        val source=PleosVehicleSource(context)
        val s=CampSession(context,source,name)
        val report=StringBuilder("Pleos SDK 2.3.2 emulator verification\n")
        try {
            // Reconcile only this test's persisted journals; never touch the user's camp_on_v2 session.
            File(context.applicationInfo.dataDir,"shared_prefs").listFiles().orEmpty()
                .filter { it.name.startsWith("test_sdk_") && it.name.endsWith(".xml") && it.nameWithoutExtension!=name }
                .sortedBy { it.name }.forEach { file->
                    val previous=CampSession(context,source,file.nameWithoutExtension)
                    previous.refresh()
                    if(previous.written.isNotEmpty()) {
                        previous.endAndRestore()
                        report.appendLine("Previous test recovery: remaining=${previous.written.toMap()}; results=${previous.results.toMap()}")
                    }
                    val originalVerified=previous.written.keys.all { id->
                        val actual=source.readControl(id);val initial=previous.initial[id]
                        actual!=null && initial!=null && kotlin.math.abs(actual-initial)<0.01
                    }
                    if(previous.written.isEmpty() || originalVerified) {
                        report.appendLine("Previous test original values re-read and verified; journal cleared")
                        context.getSharedPreferences(file.nameWithoutExtension,0).edit().clear().commit()
                    }
                }
            s.edit("end",LocalDateTime.now().plusHours(2).format(CampSession.format))
            s.refresh();s.refreshParking()
            report.appendLine("Connected=${s.connected}; parked=${s.parkingState.parked}; unrestricted=${s.parkingState.unrestricted}")
            report.appendLine("Read errors=${s.reading.errors}")
            assertTrue("Pleos must provide connected readings",s.connected)
            val controls=s.reading.controls.toList()
            assertTrue("Capabilities must not be empty",controls.isNotEmpty())
            val power=controls.firstOrNull { it.kind==ControlKind.POWER }
            if(power!=null && s.current[power.id]==0.0 && s.canAdjust(power)) {
                s.applyDirect(power,1.0)
                assertEquals("HVAC requires power for fan verification",1.0,source.readControl(power.id)?:-1.0,0.01)
                report.appendLine("HVAC enabled explicitly for verification; original power retained for restoration")
            }
            var verified=0
            for(c in controls) {
                s.refresh()
                val before=s.current[c.id]
                if(before==null || !s.canAdjust(c)) {
                    report.appendLine("UNVERIFIED ${c.id}: value=$before writable=${c.writable}; parked=${s.parkingState.parked}; unrestricted=${s.parkingState.unrestricted}; fresh=${s.fresh}")
                    android.util.Log.i("PleosVerification",report.lines().last { it.isNotBlank() })
                    continue
                }
                val candidate=if(c.choices.isNotEmpty())c.choices.keys.firstOrNull { it!=before }
                    else (before+c.step).takeIf { it<=c.max } ?: (before-c.step).takeIf { it>=c.min }
                if(candidate==null){report.appendLine("READ ONLY ${c.id}=$before");continue}
                try {
                    s.applyDirect(c,candidate)
                    val observed=source.readControl(c.id)
                    report.appendLine("WRITE ${c.id}: $before -> $candidate; observed=$observed; result=${s.results[c.id]}")
                    if(observed!=null && kotlin.math.abs(observed-candidate)<0.01)verified++
                } finally {
                    s.refresh()
                    s.applyDirect(c,before)
                    report.appendLine("RESTORE ${c.id}: expected=$before observed=${source.readControl(c.id)}")
                }
            }
            report.appendLine("Verified changed values=$verified / capabilities=${controls.size}")
            assertTrue("At least one real SDK write must round trip",verified>0)
        } finally {
            // The session journal retains unresolved restoration instead of silently discarding it.
            s.refresh();s.endAndRestore()
            report.appendLine("Unresolved restoration=${s.written.toMap()}; results=${s.results.toMap()}")
            File(context.filesDir,"pleos-sdk-verification.txt").writeText(report.toString())
            source.close()
            if(s.written.isEmpty())context.getSharedPreferences(name,0).edit().clear().commit()
        }
    }
}
