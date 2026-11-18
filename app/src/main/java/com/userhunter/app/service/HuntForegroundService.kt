package com.userhunter.app.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Build
import android.os.IBinder
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.userhunter.app.R
import com.userhunter.app.core.CheckState
import com.userhunter.app.core.AppConfig
import com.userhunter.app.core.CheckResult
import com.userhunter.app.core.GenerationMode
import com.userhunter.app.core.UsernameValidation
import com.userhunter.app.core.UsernameValidator
import com.userhunter.app.data.AppGraph
import com.userhunter.app.data.CheckedCacheEntity
import com.userhunter.app.data.HuntResultEntity
import com.userhunter.app.data.HuntSessionEntity
import com.userhunter.app.generator.CandidateGenerator
import com.userhunter.app.generator.UsernameAnalyzer
import java.math.BigInteger
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import java.util.LinkedHashMap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.withContext

class HuntForegroundService : Service() {
    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(serviceJob + Dispatchers.IO)
    private lateinit var graph: AppGraph
    private lateinit var connectivityManager: ConnectivityManager
    private var currentRunJob: Job? = null
    private val pauseSignal = MutableStateFlow(false)
    private val controlMutex = Mutex()

    @Volatile private var stopRequested = false
    @Volatile private var paused = false
    @Volatile private var manualPause = false
    @Volatile private var offlinePause = false
    @Volatile private var blockedPause = false
    @Volatile private var pauseReason: String? = null

    private var lastNotificationUpdate = 0L
    private var cachedSettingsAutoResume = true
    private var networkCallbackRegistered = false

    override fun onCreate() {
        super.onCreate()
        graph = AppGraph.get(this)
        connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        createNotificationChannel()
        registerNetworkMonitor()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForegroundCompat(buildNotification())
        when (intent?.action) {
            ACTION_PAUSE -> serviceScope.launch { pauseManually() }
            ACTION_RESUME -> serviceScope.launch { resumeManually() }
            ACTION_STOP -> serviceScope.launch { stopHunt() }
            ACTION_START, null -> serviceScope.launch {
                if (currentRunJob?.isActive != true) startRunFromSavedSession()
            }
            else -> serviceScope.launch {
                if (currentRunJob?.isActive != true) startRunFromSavedSession()
            }
        }
        return START_STICKY
    }

    private fun startForegroundCompat(notification: Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceCompat.startForeground(
                this,
                NOTIFICATION_ID,
                notification,
                android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun registerNetworkMonitor() {
        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onLost(network: Network) {
                serviceScope.launch {
                    if (!isOnline()) pauseForOffline()
                }
            }

            override fun onAvailable(network: Network) {
                serviceScope.launch {
                    if (isOnline()) resumeAfterOffline()
                }
            }

            override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
                val validated = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                    capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
                if (validated) serviceScope.launch {
                    if (isOnline()) resumeAfterOffline()
                }
            }
        }
        runCatching {
            connectivityManager.registerNetworkCallback(request, callback)
            activeNetworkCallback = callback
            networkCallbackRegistered = true
        }
    }

    private var activeNetworkCallback: ConnectivityManager.NetworkCallback? = null

    private fun isOnline(): Boolean {
        val network = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }

    private suspend fun pauseManually() = controlMutex.withLock {
        manualPause = true
        paused = true
        pauseReason = "Paused by user"
        pauseSignal.value = true
        persistStatus("PAUSED", pauseReason)
        updateNotification()
    }

    private suspend fun resumeManually() = controlMutex.withLock {
        manualPause = false
        blockedPause = false
        if (!isOnline()) {
            offlinePause = true
            paused = true
            pauseReason = OFFLINE_PAUSE_REASON
        } else {
            offlinePause = false
            paused = false
            pauseReason = null
        }
        pauseSignal.value = paused
        persistStatus(if (paused) "PAUSED" else "RUNNING", pauseReason)
        if (currentRunJob?.isActive != true && !paused) startRunFromSavedSession()
        updateNotification()
    }

    private suspend fun pauseForOffline() = controlMutex.withLock {
        if (manualPause || blockedPause || isOnline()) return
        val settings = graph.settings.settings.first()
        cachedSettingsAutoResume = settings.autoResumeWhenOnline
        offlinePause = true
        paused = true
        pauseReason = OFFLINE_PAUSE_REASON
        pauseSignal.value = true
        persistStatus("PAUSED", pauseReason)
        updateNotification()
    }

    private suspend fun resumeAfterOffline() = controlMutex.withLock {
        if (!offlinePause || manualPause || blockedPause) return
        val latestSettings = graph.settings.settings.first()
        cachedSettingsAutoResume = latestSettings.autoResumeWhenOnline
        if (!cachedSettingsAutoResume) return
        offlinePause = false
        paused = false
        pauseReason = null
        pauseSignal.value = false
        persistStatus("RUNNING", null)
        if (currentRunJob?.isActive != true) startRunFromSavedSession()
        updateNotification()
    }

    private suspend fun stopHunt() {
        stopRequested = true
        paused = false
        pauseSignal.value = false
        currentRunJob?.cancelAndJoin()
        currentRunJob = null
        val current = graph.dao.getSession()
        if (current != null) graph.dao.saveSession(
            current.copy(status = "STOPPED", updatedAt = System.currentTimeMillis(), pauseReason = null)
        )
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private suspend fun startRunFromSavedSession() {
        var session = graph.dao.getSession() ?: return
        sessionSnapshot = session
        if (session.status == "STOPPED" || session.status == "COMPLETED") return
        stopRequested = false
        val settings = graph.settings.settings.first()
        cachedSettingsAutoResume = settings.autoResumeWhenOnline
        if (session.status == "PAUSED") {
            val wasOfflinePause = session.pauseReason == OFFLINE_PAUSE_REASON
            if (!wasOfflinePause) {
                paused = true
                pauseSignal.value = true
                pauseReason = session.pauseReason ?: "Paused"
                return
            }
            offlinePause = true
            paused = true
            pauseSignal.value = true
            pauseReason = OFFLINE_PAUSE_REASON
            if (!isOnline() || !settings.autoResumeWhenOnline) {
                updateNotification()
                return
            }
            offlinePause = false
            paused = false
            pauseSignal.value = false
            persistStatus("RUNNING", null)
            session = graph.dao.getSession() ?: session.copy(status = "RUNNING", pauseReason = null)
            sessionSnapshot = session
        }
        if (!isOnline()) {
            paused = true
            offlinePause = true
            pauseReason = OFFLINE_PAUSE_REASON
            pauseSignal.value = true
            persistStatus("PAUSED", pauseReason)
            updateNotification()
            return
        }
        paused = false
        pauseSignal.value = false
        currentRunJob = serviceScope.launch { runSession(session) }
    }

    private suspend fun runSession(initialSession: HuntSessionEntity) {
        val envelope = runCatching { HuntRequestCodec.decode(initialSession.requestJson) }.getOrNull()
        if (envelope == null) {
            persistStatus("PAUSED", "Saved hunt settings could not be restored")
            return
        }
        val settings = graph.settings.settings.first()
        cachedSettingsAutoResume = settings.autoResumeWhenOnline
        graph.rateLimiter.configure(settings.requestsPerSecond)
        graph.checker.configureTimeouts(settings.connectTimeoutSeconds, settings.readTimeoutSeconds)

        coroutineScope {
        val request = envelope.generation
        val generator = CandidateGenerator()
        var cursor = runCatching { BigInteger(initialSession.nextIndex) }.getOrDefault(BigInteger.ZERO)
        val startCursor = cursor
        val total = generator.totalCount(request)
        val concurrency = settings.concurrency.coerceIn(1, 3)
        val queue = Channel<CandidateTask>(capacity = concurrency * 2)
        val semaphore = Semaphore(concurrency)
        val workers = List(concurrency) {
            launch {
                for (task in queue) {
                    if (stopRequested) break
                    awaitUnpaused()
                    if (stopRequested) break
                    try {
                        semaphore.withPermit {
                            awaitUnpaused()
                            if (!stopRequested) processTask(task, settings.recheckHours)
                        }
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (error: Exception) {
                        runCatching {
                            recordOutcome(
                                task.index,
                                CheckResult(
                                    username = task.username,
                                    state = CheckState.UNKNOWN,
                                    detail = error.message ?: "Unexpected worker error."
                                )
                            )
                        }
                    }
                }
            }
        }

        val producer = launch {
            try {
                if (request.mode == GenerationMode.WORD_LIST) {
                    if (envelope.wordListUri.isBlank()) {
                        pauseReason = "Select a .txt word list before starting this mode"
                        pauseAsConfigurationError()
                    } else {
                        val reader = WordListReader(contentResolver, envelope.wordListUri)
                        reader.streamFrom(startCursor) { index, line ->
                            awaitUnpaused()
                            if (stopRequested) return@streamFrom false
                            val valid = UsernameValidator.validate(line)
                            if (valid is UsernameValidation.Valid) {
                                queue.send(CandidateTask(index, valid.normalized))
                            } else {
                                markCompleted(index)
                            }
                            true
                        }
                    }
                } else {
                    while (cursor < total && !stopRequested) {
                        awaitUnpaused()
                        if (stopRequested) break
                        val name = generator.candidateAt(request, cursor)
                        if (name == null) markCompleted(cursor)
                        else queue.send(CandidateTask(cursor, name))
                        cursor += BigInteger.ONE
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                pauseReason = error.message ?: "Could not read the candidate source"
                pauseAsConfigurationError()
            } finally {
                queue.close()
            }
        }

        try {
            producer.join()
            workers.joinAll()
            flushPending()
            if (!stopRequested && !paused) {
                val finalSession = graph.dao.getSession()
                if (finalSession != null && request.mode != GenerationMode.RANDOM && request.mode != GenerationMode.WORD_LIST &&
                    BigInteger(finalSession.nextIndex) >= total
                ) {
                    graph.dao.saveSession(finalSession.copy(status = "COMPLETED", updatedAt = System.currentTimeMillis()))
                    ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
                    stopSelf()
                } else if (request.mode == GenerationMode.WORD_LIST) {
                    graph.dao.getSession()?.let {
                        graph.dao.saveSession(it.copy(status = "COMPLETED", updatedAt = System.currentTimeMillis()))
                    }
                    ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
                    stopSelf()
                }
            }
        } catch (cancelled: CancellationException) {
            withContext(NonCancellable) { flushPending() }
            throw cancelled
        }
        }
    }

    private data class CandidateTask(val index: BigInteger, val username: String)

    private val recordMutex = Mutex()
    private val completedIndices = sortedSetOf<BigInteger>()
    private val inFlightNames = ConcurrentHashMap.newKeySet<String>()
    private val recentNameLock = Any()
    private val recentlyProcessedNames = object : LinkedHashMap<String, Unit>(512, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Unit>?): Boolean = size > 2_048
    }
    private val pendingChecks = mutableListOf<CheckedCacheEntity>()
    private val pendingResults = mutableListOf<HuntResultEntity>()
    private var sessionSnapshot: HuntSessionEntity? = null
    private var checkedSinceSpeedSample = 0L
    private var speedSampleTime = 0L
    private var measuredSpeed = 0.0
    private var skippedSinceFlush = 0
    private val consecutiveBlocks = AtomicInteger(0)

    private suspend fun processTask(task: CandidateTask, recheckHours: Int) {
        val claimed = synchronized(recentNameLock) {
            if (task.username in recentlyProcessedNames || !inFlightNames.add(task.username)) false else true
        }
        if (!claimed) {
            markCompleted(task.index)
            return
        }
        var outcome: CheckResult? = null
        try {
            val cutoff = System.currentTimeMillis() - recheckHours.coerceIn(1, 720) * 3_600_000L
            if (graph.dao.findRecentCheck(task.username, cutoff) != null) {
                markCompleted(task.index)
                return
            }
            val result = graph.checker.check(task.username)
            outcome = result
            recordOutcome(task.index, result)
        } finally {
            synchronized(recentNameLock) {
                inFlightNames.remove(task.username)
                recentlyProcessedNames[task.username] = Unit
            }
        }
        if (outcome?.state == CheckState.BLOCKED) {
            val blockCount = consecutiveBlocks.incrementAndGet()
            if (blockCount >= AppConfig.BLOCKS_BEFORE_AUTO_PAUSE) {
                controlMutex.withLock {
                    blockedPause = true
                    paused = true
                    pauseReason = "Repeated TikTok restrictions. Tap Resume to continue."
                    pauseSignal.value = true
                    persistStatus("PAUSED", pauseReason)
                    updateNotification()
                }
            }
        } else if (outcome != null) {
            consecutiveBlocks.set(0)
        }
    }

    private suspend fun recordOutcome(index: BigInteger, result: CheckResult) {
        recordMutex.withLock {
            val now = System.currentTimeMillis()
            pendingChecks += CheckedCacheEntity(
                username = result.username,
                state = result.state.name,
                checkedAt = result.checkedAt,
                httpStatus = result.httpStatus,
                detail = result.detail
            )
            if (result.state == CheckState.AVAILABLE_LIKELY) {
                val candidate = UsernameAnalyzer.analyze(result.username)
                pendingResults += HuntResultEntity(
                    username = result.username,
                    rarity = candidate.rarity,
                    tagsCsv = candidate.tags.joinToString(","),
                    length = result.username.length,
                    discoveredAt = now,
                    lastCheckedAt = result.checkedAt
                )
            }
            val old = sessionSnapshot ?: graph.dao.getSession()
            if (old != null) {
                val checked = old.checked + 1
                val found = old.found + if (result.state == CheckState.AVAILABLE_LIKELY) 1 else 0
                val taken = old.taken + if (result.state == CheckState.TAKEN) 1 else 0
                val blocked = old.blocked + if (result.state == CheckState.BLOCKED) 1 else 0
                val errors = old.errors + if (result.state == CheckState.UNKNOWN || result.state == CheckState.INVALID) 1 else 0
                val nextCursor = cursorAfterComplete(old.nextIndex, index)
                updateSpeed(checked)
                sessionSnapshot = old.copy(
                    nextIndex = nextCursor.toString(),
                    checked = checked,
                    found = found,
                    taken = taken,
                    blocked = blocked,
                    errors = errors,
                    updatedAt = now,
                    requestsPerSecond = measuredSpeed
                )
            }
            if (pendingChecks.size >= WRITE_BATCH_SIZE || (sessionSnapshot?.checked ?: 0L) % 5L == 0L) {
                flushPendingLocked()
            }
            updateNotificationIfDue()
        }
    }

    private suspend fun markCompleted(index: BigInteger) {
        recordMutex.withLock {
            val old = sessionSnapshot ?: graph.dao.getSession() ?: return
            val next = cursorAfterComplete(old.nextIndex, index)
            sessionSnapshot = old.copy(nextIndex = next.toString(), updatedAt = System.currentTimeMillis())
            skippedSinceFlush += 1
            if (skippedSinceFlush >= WRITE_BATCH_SIZE) flushPendingLocked()
        }
    }

    private fun cursorAfterComplete(saved: String, index: BigInteger): BigInteger {
        val base = runCatching { BigInteger(saved) }.getOrDefault(BigInteger.ZERO)
        completedIndices += index
        var next = base
        while (completedIndices.remove(next)) next += BigInteger.ONE
        return next
    }

    private fun updateSpeed(checked: Long) {
        val now = SystemClock.elapsedRealtime()
        if (speedSampleTime == 0L) {
            speedSampleTime = now
            checkedSinceSpeedSample = checked
            measuredSpeed = 0.0
            return
        }
        val elapsed = (now - speedSampleTime).coerceAtLeast(1L) / 1000.0
        measuredSpeed = ((checked - checkedSinceSpeedSample) / elapsed).coerceAtLeast(0.0)
        if (elapsed >= 5.0) {
            speedSampleTime = now
            checkedSinceSpeedSample = checked
        }
    }

    private suspend fun flushPending() = recordMutex.withLock { flushPendingLocked() }

    private suspend fun flushPendingLocked() {
        val snapshot = sessionSnapshot
        if (pendingChecks.isNotEmpty() || pendingResults.isNotEmpty()) {
            graph.dao.recordBatch(pendingChecks.toList(), pendingResults.toList())
            pendingChecks.clear()
            pendingResults.clear()
        }
        skippedSinceFlush = 0
        if (snapshot != null) {
            graph.dao.saveSession(snapshot.copy(updatedAt = System.currentTimeMillis()))
        }
    }

    private suspend fun persistStatus(status: String, reason: String?) {
        recordMutex.withLock {
            val old = sessionSnapshot ?: graph.dao.getSession() ?: return
            sessionSnapshot = old.copy(status = status, pauseReason = reason, updatedAt = System.currentTimeMillis())
            graph.dao.saveSession(sessionSnapshot!!)
        }
    }

    private suspend fun pauseAsConfigurationError() {
        paused = true
        pauseSignal.value = true
        persistStatus("PAUSED", pauseReason)
        updateNotification()
    }

    private suspend fun awaitUnpaused() {
        while (paused && !stopRequested) {
            pauseSignal.first { !it || stopRequested }
        }
    }

    private suspend fun updateNotificationIfDue() {
        val now = SystemClock.elapsedRealtime()
        if (now - lastNotificationUpdate >= 1_000L) updateNotification()
    }

    private suspend fun updateNotification() {
        val session = sessionSnapshot ?: graph.dao.getSession()
        val notification = buildNotification(session)
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID, notification)
        lastNotificationUpdate = SystemClock.elapsedRealtime()
    }

    private fun buildNotification(session: HuntSessionEntity? = null): Notification {
        val isPaused = paused || session?.status == "PAUSED"
        val status = when {
            isPaused -> pauseReason ?: session?.pauseReason ?: "Paused"
            session?.status == "COMPLETED" -> "Completed"
            else -> "Hunting"
        }
        val checked = session?.checked ?: 0L
        val found = session?.found ?: 0L
        val speedValue = session?.requestsPerSecond ?: measuredSpeed
        val speed = "%.2f/s".format(speedValue)
        val action = if (isPaused) ACTION_RESUME else ACTION_PAUSE
        val actionLabel = if (isPaused) getString(R.string.resume) else getString(R.string.pause)
        val actionIcon = if (isPaused) android.R.drawable.ic_media_play else android.R.drawable.ic_media_pause

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(getString(R.string.app_name))
            .setContentText("$status • $checked checked • $found found • $speed")
            .setStyle(NotificationCompat.BigTextStyle().bigText("$status\n$checked checked • $found found • $speed"))
            .setOnlyAlertOnce(true)
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .addAction(actionIcon, actionLabel, actionPendingIntent(action))
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, getString(R.string.stop), actionPendingIntent(ACTION_STOP))
            .build()
    }

    private fun actionPendingIntent(action: String): PendingIntent {
        val intent = Intent(this, HuntForegroundService::class.java).setAction(action)
        val requestCode = action.hashCode()
        return PendingIntent.getService(
            this,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.hunt_notification_channel),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.hunt_notification_description)
                setShowBadge(false)
            }
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    override fun onTimeout(startId: Int) = handleForegroundServiceTimeout(startId)

    override fun onTimeout(startId: Int, fgsType: Int) = handleForegroundServiceTimeout(startId)

    private fun handleForegroundServiceTimeout(startId: Int) {
        serviceScope.launch {
            paused = true
            pauseReason = "Android paused this long-running data sync. Reopen the app to resume."
            pauseSignal.value = true
            persistStatus("PAUSED", pauseReason)
            updateNotification()
            ServiceCompat.stopForeground(this@HuntForegroundService, ServiceCompat.STOP_FOREGROUND_REMOVE)
            stopSelf(startId)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        if (networkCallbackRegistered) {
            runCatching { activeNetworkCallback?.let(connectivityManager::unregisterNetworkCallback) }
        }
        serviceScope.cancel()
        super.onDestroy()
    }

    companion object {
        const val ACTION_START = "com.userhunter.app.action.START"
        const val ACTION_PAUSE = "com.userhunter.app.action.PAUSE"
        const val ACTION_RESUME = "com.userhunter.app.action.RESUME"
        const val ACTION_STOP = "com.userhunter.app.action.STOP"

        private const val CHANNEL_ID = "user_hunter_hunt"
        private const val OFFLINE_PAUSE_REASON = "Waiting for an internet connection"
        private const val NOTIFICATION_ID = 4001
        private const val WRITE_BATCH_SIZE = 20
    }
}
