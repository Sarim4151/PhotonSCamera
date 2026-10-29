package com.hinnka.mycamera.video
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicInteger
class RecordingSessionGateTest {
    @Test fun reservationSurvivesPreparationAndFinalization() {
        val gate = RecordingSessionGate()
        val ticket = gate.reserve()!!
        assertNull(gate.reserve())
        assertTrue(gate.beginFinish(ticket))
        assertFalse(gate.beginFinish(ticket))
        assertNull(gate.reserve())
        gate.complete(ticket)
        assertNotNull(gate.reserve())
    }
    @Test fun staleCompletionCannotReleaseNewRecording() {
        val gate = RecordingSessionGate()
        val first = gate.reserve()!!
        gate.complete(first)
        val second = gate.reserve()!!
        gate.complete(first)
        assertFalse(gate.beginFinish(first))
        assertNull(gate.reserve())
        assertTrue(gate.beginFinish(second))
    }
    @Test fun simultaneousStartsHaveExactlyOneOwner() {
        val gate = RecordingSessionGate()
        val ready = CountDownLatch(16)
        val start = CountDownLatch(1)
        val owners = AtomicInteger()
        val threads = List(16) {
            Thread {
                ready.countDown()
                start.await()
                if (gate.reserve() != null) owners.incrementAndGet()
            }.apply { start() }
        }
        ready.await()
        start.countDown()
        threads.forEach { it.join() }
        assertEquals(1, owners.get())
    }
    @Test fun simultaneousStopsHaveExactlyOneFinalizer() {
        val gate = RecordingSessionGate()
        val ticket = gate.reserve()!!
        val ready = CountDownLatch(16)
        val start = CountDownLatch(1)
        val owners = AtomicInteger()
        val threads = List(16) {
            Thread {
                ready.countDown()
                start.await()
                if (gate.beginFinish(ticket)) owners.incrementAndGet()
            }.apply { start() }
        }
        ready.await()
        start.countDown()
        threads.forEach { it.join() }
        assertEquals(1, owners.get())
        assertNull(gate.reserve())
    }
}
