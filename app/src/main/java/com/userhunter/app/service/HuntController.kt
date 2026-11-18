package com.userhunter.app.service

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import com.userhunter.app.core.GenerationRequest
import com.userhunter.app.data.AppGraph
import com.userhunter.app.data.HuntSessionEntity
import kotlinx.coroutines.flow.Flow

class HuntController(context: Context) {
    private val appContext = context.applicationContext
    private val graph = AppGraph.get(appContext)

    val session: Flow<HuntSessionEntity?> = graph.dao.observeSession()

    suspend fun start(request: GenerationRequest, wordListUri: String = "") {
        val now = System.currentTimeMillis()
        graph.dao.saveSession(
            HuntSessionEntity(
                requestJson = HuntRequestCodec.encode(request, wordListUri),
                nextIndex = "0",
                status = "RUNNING",
                checked = 0,
                found = 0,
                taken = 0,
                blocked = 0,
                errors = 0,
                startedAt = now,
                updatedAt = now
            )
        )
        send(HuntForegroundService.ACTION_START)
    }

    fun pause() = send(HuntForegroundService.ACTION_PAUSE)
    fun resume() = send(HuntForegroundService.ACTION_RESUME)
    fun stop() = send(HuntForegroundService.ACTION_STOP)

    private fun send(action: String) {
        val intent = Intent(appContext, HuntForegroundService::class.java).setAction(action)
        ContextCompat.startForegroundService(appContext, intent)
    }
}
