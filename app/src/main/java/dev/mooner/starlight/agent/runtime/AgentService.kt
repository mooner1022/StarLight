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
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.sample

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
        if (!startInForeground(hub))
            return
        wakeLock = getSystemService(PowerManager::class.java)
            ?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "StarLight:agent")
            ?.apply { acquire(WAKE_LOCK_TIMEOUT_MILLIS) }

        @OptIn(FlowPreview::class)
        combine(hub.runtime.live, hub.runtime.visible, hub.runtime.dismissed, hub.settings.version) { runs, _, _, _ -> runs }
            .sample(UPDATE_INTERVAL_MILLIS)
            .onEach { runs ->
                if (runs.isEmpty())
                    stopSelf()
            }
            .filter { it.isNotEmpty() }
            .map { AgentNotifier.progressKey(hub) }
            .distinctUntilChanged()
            .onEach {
                // Once swiped away, the runs go on without it; approvals and the end still notify on their own.
                if (hub.runtime.live.value.keys.all { id -> id in hub.runtime.dismissed.value })
                    return@onEach
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                    ContextCompat.checkSelfPermission(this, android.Manifest.permission.POST_NOTIFICATIONS) == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                    NotificationManagerCompat.from(this).notify(AgentNotifier.PROGRESS_ID, progress(hub))
                }
            }
            .launchIn(scope)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Every start has to show the notification again, or Android stops the app.
        if (startInForeground(AgentHub.get(this)) && AgentHub.get(this).runtime.live.value.isEmpty())
            stopSelf()
        return START_NOT_STICKY
    }

    /**
     * Android 15 ends a data sync service after six hours in a day, and the app if the service doesn't stop at once.
     * The runs stop with a notice and can be continued once the user is back.
     */
    override fun onTimeout(startId: Int, fgsType: Int) {
        stopSelf()
        AgentHub.get(this).runtime.stopAllForTimeLimit()
    }

    override fun onDestroy() {
        scope.cancel()
        wakeLock?.takeIf { it.isHeld }?.release()
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }

    /** False when Android refuses, as after the day's time for data sync is used up; the service then stops. */
    private fun startInForeground(hub: AgentHub): Boolean {
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC else 0
        return try {
            ServiceCompat.startForeground(this, AgentNotifier.PROGRESS_ID, progress(hub), type)
            true
        } catch (e: Exception) {
            LOG.warn { "Couldn't keep the agent in the foreground: $e" }
            stopSelf()
            false
        }
    }

    private fun progress(hub: AgentHub) =
        AgentNotifier.progress(this, hub)

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
        if (intent.action == ACTION_DISMISSED) {
            intent.getStringArrayExtra(EXTRA_CONVERSATIONS)?.let { runtime.progressDismissed(it.toList()) }
            return
        }
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
        /** The user swiped the progress notification away. */
        const val ACTION_DISMISSED = "dev.mooner.starlight.agent.DISMISSED"
        const val EXTRA_CONVERSATIONS = "conversations"
        const val EXTRA_CONVERSATION = "conversation"
        const val EXTRA_CALL = "call"
    }
}
