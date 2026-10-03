package com.example.emergencywatch

import com.example.emergencywatch.domain.telemetry.TelemetrySampler
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

class TelemetrySamplerTest {

    @Test
    fun fiftyCallbacksWithinInterval_persistOnlyOnce() {
        val clock = AtomicLong(1_000L)
        val sampler = TelemetrySampler { clock.get() }
        val intervalMs = 5_000L

        var accepted = 0
        repeat(50) {
            if (sampler.tryAcquire(intervalMs)) accepted++
            clock.addAndGet(20L) // ~50 Hz over 1 second — still inside 5s window
        }

        assertEquals(1, accepted)
    }

    @Test
    fun callbacksInsideInterval_areDiscardedForPersistence() {
        val clock = AtomicLong(0L)
        val sampler = TelemetrySampler { clock.get() }

        assertTrue(sampler.tryAcquire(5_000L))
        clock.set(4_999L)
        assertFalse(sampler.tryAcquire(5_000L))
        clock.set(5_000L)
        assertTrue(sampler.tryAcquire(5_000L))
    }

    @Test
    fun callbackAfterInterval_createsNextSlot() {
        val clock = AtomicLong(0L)
        val sampler = TelemetrySampler { clock.get() }

        assertTrue(sampler.tryAcquire(5_000L))
        clock.set(5_000L)
        assertTrue(sampler.tryAcquire(5_000L))
        clock.set(10_000L)
        assertTrue(sampler.tryAcquire(5_000L))
    }

    @Test
    fun changingSampleInterval_changesPersistenceRate() {
        val clock = AtomicLong(0L)
        val sampler = TelemetrySampler { clock.get() }

        // 2s interval → accept at 0 and 2000
        assertTrue(sampler.tryAcquire(2_000L))
        clock.set(1_999L)
        assertFalse(sampler.tryAcquire(2_000L))
        clock.set(2_000L)
        assertTrue(sampler.tryAcquire(2_000L))

        sampler.reset()
        clock.set(0L)

        // 10s interval → only one accept across 9s of callbacks
        var accepted = 0
        repeat(10) {
            if (sampler.tryAcquire(10_000L)) accepted++
            clock.addAndGet(1_000L)
        }
        assertEquals(1, accepted)
    }

    @Test
    fun threadSafeUnderConcurrentCallbacks() {
        val clock = AtomicLong(0L)
        val sampler = TelemetrySampler { clock.get() }
        val accepted = AtomicInteger(0)
        val threads = 8
        val latch = CountDownLatch(threads)
        val pool = Executors.newFixedThreadPool(threads)

        repeat(threads) {
            pool.execute {
                try {
                    repeat(100) {
                        if (sampler.tryAcquire(5_000L)) accepted.incrementAndGet()
                    }
                } finally {
                    latch.countDown()
                }
            }
        }

        assertTrue(latch.await(5, TimeUnit.SECONDS))
        pool.shutdownNow()
        assertEquals(1, accepted.get())
    }
}
