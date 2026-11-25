package org.nuruplace.member.feature.listen

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Collections
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PlanAudioSimulationTest {
    @Test fun playsTwoPlanParagraphsUsingTheRealMediaPlayer() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val requested = Collections.synchronizedList(mutableListOf<Int>())
        val source = PlanNarration("4c5fbfa1-d7e7-4d8f-8a89-a2970a95c904", 1, "word", "a".repeat(64))
        instrumentation.runOnMainSync {
            ReadAloud.bind(context)
            ReadAloud.testAudioLoader = { _, index -> requested.add(index); silentWave() }
        }
        try {
            waitUntil { ReadAloud.state.value.status == ReadAloud.Status.READY }
            instrumentation.runOnMainSync { ReadAloud.play(context, "simulation", listOf("One", "Two"), narration = source, isPlan = true) }
            waitUntil { requested.size == 2 && ReadAloud.state.value.status == ReadAloud.Status.READY }
            assertEquals(listOf(0, 1), requested.toList())
            assertTrue(ReadAloud.state.value.key == null)
        } finally {
            instrumentation.runOnMainSync { ReadAloud.stop(); ReadAloud.testAudioLoader = null; ReadAloud.unbind(null) }
        }
    }

    private fun waitUntil(condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + 20_000
        while (!condition() && System.currentTimeMillis() < deadline) Thread.sleep(100)
        assertTrue("Timed out waiting for player", condition())
    }

    private fun silentWave(): ByteArray {
        val samples = 1600
        val out = ByteBuffer.allocate(44 + samples * 2).order(ByteOrder.LITTLE_ENDIAN)
        out.put("RIFF".toByteArray()); out.putInt(36 + samples * 2); out.put("WAVEfmt ".toByteArray()); out.putInt(16)
        out.putShort(1); out.putShort(1); out.putInt(8000); out.putInt(16000); out.putShort(2); out.putShort(16)
        out.put("data".toByteArray()); out.putInt(samples * 2)
        return out.array()
    }
}
