package com.widgeon.app.service

import android.app.Service
import android.content.Intent
import android.os.IBinder
import com.widgeon.app.widgeon
import com.widgeon.app.ai.GenStage
import com.widgeon.app.ai.GenState
import com.widgeon.app.notify.Notifier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Lightweight foreground service: its only job is to keep the process alive and
 * mirror generation progress into a notification while the user is away from
 * the app. All real work lives in GenerationEngine.
 */
class GenerationKeeperService : Service() {

    private var scope: CoroutineScope? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        Notifier.ensureChannels(this)
        startForeground(Notifier.ID_PROGRESS, Notifier.buildProgress(this, GenStage.CONNECTING.label, 4))
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate).also { s ->
            s.launch {
                widgeon.engine.state.collect { state ->
                    when (state) {
                        is GenState.Running -> {
                            Notifier.progress(this@GenerationKeeperService, state.stage.label, (state.progress * 100).toInt())
                        }
                        else -> stopSelf()
                    }
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onDestroy() {
        scope?.cancel()
        scope = null
        super.onDestroy()
    }
}
