package com.userhunter.app.checker

import com.userhunter.app.core.CheckState
import kotlinx.coroutines.runBlocking
import java.io.IOException
import java.util.concurrent.atomic.AtomicInteger
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UsernameCheckerValidationTest {
    @Test
    fun invalidUsernameIsRejectedBeforeNetwork() = runBlocking {
        val networkCalls = AtomicInteger(0)
        val client = OkHttpClient.Builder()
            .addInterceptor {
                networkCalls.incrementAndGet()
                throw IOException("The test must not reach the network.")
            }
            .build()
        val checker = TikTokUsernameChecker(client, CheckRateLimiter())
        val result = checker.check("contains spaces")
        assertEquals(CheckState.INVALID, result.state)
        assertTrue("Invalid usernames must not create a network request", networkCalls.get() == 0)
    }
}
