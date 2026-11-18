package com.userhunter.app.data

import android.content.Context
import com.userhunter.app.checker.CheckRateLimiter
import com.userhunter.app.checker.TikTokUsernameChecker
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

class AppGraph private constructor(context: Context) {
    val database: UserHunterDatabase = UserHunterDatabase.get(context)
    val dao: UserHunterDao = database.dao()
    val settings = AppSettingsRepository(context)
    val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .retryOnConnectionFailure(true)
        .build()
    val rateLimiter = CheckRateLimiter()
    val checker = TikTokUsernameChecker(httpClient, rateLimiter)

    companion object {
        @Volatile private var instance: AppGraph? = null
        fun get(context: Context): AppGraph = instance ?: synchronized(this) {
            instance ?: AppGraph(context.applicationContext).also { instance = it }
        }
    }
}
