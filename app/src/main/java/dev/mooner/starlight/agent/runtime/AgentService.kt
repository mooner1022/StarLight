package dev.mooner.starlight.agent.runtime

import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import dev.mooner.starlight.agent.AgentHub
import dev.mooner.starlight.plugincore.logger.LoggerFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.sample
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

/**
 * Keeps the process in the foreground while the agent runs, so that a run goes on after the user leaves the app.
 * It does no work itself: the runs live in [AgentRuntime]. It stops once no run is left.
 */
class AgentService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var wakeLock: PowerManager.WakeLock? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        val hub = AgentHub.get(this)
        AgentNotifier.ensureChannels(this)
        startInForeground(hub)
        wakeLock = getSystemService(PowerManager::class.java)
            ?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "StarLight:agent")
            ?.apply { acquire(WAKE_LOCK_TIMEOUT_MILLIS) }

        @OptIn(FlowPreview::class)
        hub.runtime.live
            .sample(UPDATE_INTERVAL_MILLIS)
            .onEach { runs ->
                if (runs.isEmpty()) {
                    stopSelf()
                    return@onEach
                }
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                    ContextCompat.checkSelfPermission(this, android.Manifest.permission.POST_NOTIFICATIONS) == android.content.pm.PackageManager.PERMISSION_GRANTED)
                    NotificationManagerCompat.from(this).notify(AgentNotifier.PROGRESS_ID, progress(hub))
            }
            .launchIn(scope)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Every start has to show the notification again, or Android stops the app.
        startInForeground(AgentHub.get(this))
        if (AgentHub.get(this).runtime.live.value.isEmpty())
            stopSelf()
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        scope.cancel()
        wakeLock?.takeIf { it.isHeld }?.release()
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }

    private fun startInForeground(hub: AgentHub) {
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC else 0
        ServiceCompat.startForeground(this, AgentNotifier.PROGRESS_ID, progress(hub), type)
    }

    private fun progress(hub: AgentHub) =
        AgentNotifier.progress(this, hub.runtime.live.value.values) { id -> hub.store.get(id)?.title.orEmpty() }

    companion object {
        private const val UPDATE_INTERVAL_MILLIS = 800L
        private const val WAKE_LOCK_TIMEOUT_MILLIS = 3 * 60 * 60 * 1000L
        private val LOG = LoggerFactory.logger("AgentService")

        fun start(context: Context) {
            try {
                ContextCompat.startForegroundService(context, Intent(context, AgentService::class.java))
            } catch (e: Exception) {
                // Android 12+ refuses a foreground start from the background; the run still goes on while the
                // process lives.
                LOG.warn { "Couldn't start the agent service: $e" }
            }
        }
    }
}

/** Buttons of the agent's notifications. */
class AgentActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val runtime = AgentHub.get(context).runtime
        val conversation = intent.getStringExtra(EXTRA_CONVERSATION) ?: return
        val call = intent.getStringExtra(EXTRA_CALL)
        when (intent.action) {
            ACTION_ALLOW -> call?.let { runtime.resolveApproval(conversation, it, allow = true) }
            ACTION_DENY  -> call?.let { runtime.resolveApproval(conversation, it, allow = false) }
            ACTION_STOP  -> runtime.stop(conversation)
        }
    }

    companion object {
        const val ACTION_ALLOW = "dev.mooner.starlight.agent.ALLOW"
        const val ACTION_DENY = "dev.mooner.starlight.agent.DENY"
        const val ACTION_STOP = "dev.mooner.starlight.agent.STOP"
        const val EXTRA_CONVERSATION = "conversation"
        const val EXTRA_CALL = "call"
    }
}
