package com.userhunter.app.checker

import com.userhunter.app.core.AppConfig
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.random.Random
import kotlin.math.max
import kotlin.math.min

/** One shared limiter applies across all worker coroutines. */
class CheckRateLimiter {
    private val mutex = Mutex()
    @Volatile private var requestsPerSecond: Double = 2.0
    private var lastReservedNanos = 0L
    private var blockedUntilNanos = 0L
    private var blockStreak = 0

    fun configure(value: Double) {
        requestsPerSecond = value.coerceIn(0.2, 2.0)
    }

    suspend fun awaitPermit() {
        while (true) {
            val waitMillis = mutex.withLock {
                val now = System.nanoTime()
                val intervalNanos = (1_000_000_000.0 / requestsPerSecond).toLong()
                val nextRateSlot = if (lastReservedNanos == 0L) now else lastReservedNanos + intervalNanos
                val scheduled = max(now, max(nextRateSlot, blockedUntilNanos)) +
                    Random.nextLong(20, 140) * 1_000_000L
                lastReservedNanos = scheduled
                ((scheduled - now).coerceAtLeast(0L) / 1_000_000L)
            }
            if (waitMillis > 0) delay(waitMillis)

            // A block may have been registered while this coroutine was waiting for its slot.
            val stillBlocked = mutex.withLock { System.nanoTime() < blockedUntilNanos }
            if (!stillBlocked) return
        }
    }

    /** Registers global exponential backoff after a 403/429/captcha response. */
    suspend fun registerBlock(): Long = mutex.withLock {
        blockStreak = (blockStreak + 1).coerceAtMost(10)
        val seconds = min(2.0.powSafe(blockStreak - 1), AppConfig.MAX_BACKOFF_SECONDS.toDouble()).toLong().coerceAtLeast(1L)
        val until = System.nanoTime() + seconds * 1_000_000_000L
        blockedUntilNanos = max(blockedUntilNanos, until)
        seconds
    }

    suspend fun registerSuccess() = mutex.withLock {
        blockStreak = 0
    }

    private fun Double.powSafe(exponent: Int): Double {
        var result = 1.0
        repeat(exponent.coerceAtLeast(0)) { result *= this }
        return result
    }
}
