package com.widgeon.app.ai

import android.content.Context
import android.content.Intent
import com.widgeon.app.data.DesignRepository
import com.widgeon.app.data.WidgetDesign
import com.widgeon.app.notify.Notifier
import com.widgeon.app.service.GenerationKeeperService
import com.widgeon.app.widgeon
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Stages shown in the generating overlay / notification. */
enum class GenStage(val label: String) {
    CONNECTING("Connecting to your AI provider…"),
    DESIGNING("Designing layout & typography…"),
    COMPOSING("Composing colors & spacing…"),
    RENDERING("Rendering the widget…"),
}

sealed class GenState {
    data object Idle : GenState()
    data class Running(val stage: GenStage, val progress: Float) : GenState()
    data class Success(val design: WidgetDesign, val isNew: Boolean) : GenState()
    data class Failed(val error: Throwable) : GenState()
}

/**
 * Owns a single generation at a time. Progress is smoothed for a premium feel
 * with real stages aligned underneath. Survives activity recreation; the keeper
 * service keeps the process foregrounded while the user is away.
 */
class GenerationEngine(private val context: Context) {

    private val scope get() = context.widgeon.appScope
    private val repo get() = context.widgeon.designRepo

    private val _state = MutableStateFlow<GenState>(GenState.Idle)
    val state: StateFlow<GenState> = _state

    /** Conversation (create messages + prior refine turns) for follow-up edits. */
    private val _history = MutableStateFlow<List<AiMessage>>(emptyList())
    val history: StateFlow<List<AiMessage>> = _history

    private var job: Job? = null

    val isRunning: Boolean get() = _state.value is GenState.Running

    fun cancel() {
        job?.cancel()
        job = null
        _state.value = GenState.Idle
        Notifier.cancelProgress(context)
        runCatching { context.stopService(Intent(context, GenerationKeeperService::class.java)) }
    }

    /** UI acknowledges a terminal state (success consumed / failure dismissed). */
    fun consume() {
        if (_state.value !is GenState.Running) _state.value = GenState.Idle
    }

    fun create(request: CreateRequest) {
        if (isRunning) return
        runGeneration { setStage ->
            val provider = AiClients.fromPrefs(context)
            setStage(GenStage.CONNECTING, 0.06f)
            val messages = PromptBuilder.createMessages(request)
            _history.value = messages
            val raw = withContext(Dispatchers.IO) { provider.complete(messages, temperature = 0.8) }
            setStage(GenStage.RENDERING, 0.9f)
            val now = System.currentTimeMillis()
            val design = DesignJsonParser.parse(raw, request.prompt).copy(
                id = DesignRepository.newId(),
                // The user's grid selection is authoritative — AI sizes vary
                cols = request.cols.coerceIn(1, 5),
                rows = request.rows.coerceIn(1, 5),
                createdAt = now,
                updatedAt = now,
                version = 1,
            )
            repo.upsert(design)
            _history.value = messages + AiMessage.assistant(raw)
            design
        }
    }

    fun refine(design: WidgetDesign, instruction: String) {
        if (isRunning) return
        runGeneration { setStage ->
            val provider = AiClients.fromPrefs(context)
            setStage(GenStage.CONNECTING, 0.06f)
            val messages = PromptBuilder.refineMessages(design, instruction, _history.value)
            val raw = withContext(Dispatchers.IO) { provider.complete(messages, temperature = 0.5) }
            setStage(GenStage.RENDERING, 0.9f)
            val parsed = DesignJsonParser.parse(raw, design.prompt)
            val updated = parsed.copy(
                id = design.id,
                // Keep the established grid size; follow-up prompts shouldn't
                // silently move the widget to a different slot
                cols = design.cols,
                rows = design.rows,
                prompt = design.prompt,
                createdAt = design.createdAt,
                updatedAt = System.currentTimeMillis(),
                version = design.version + 1,
            )
            repo.upsert(updated)
            _history.value = _history.value +
                AiMessage.user("CHANGE REQUEST: ${instruction.trim()}") +
                AiMessage.assistant(raw)
            updated
        }
    }

    /**
     * Shared runner: animated progress + keeper service + result notifications,
     * then [work] performs the actual provider call and returns the design.
     */
    private fun runGeneration(work: suspend (setStage: (GenStage, Float) -> Unit) -> WidgetDesign) {
        _state.value = GenState.Running(GenStage.CONNECTING, 0.04f)

        // Keep the process foregrounded while the user may leave the app
        if (Notifier.canPost(context)) {
            runCatching {
                context.startForegroundService(Intent(context, GenerationKeeperService::class.java))
            }
        }

        job = scope.launch {
            try {
                var currentStage = GenStage.CONNECTING
                val setStage: (GenStage, Float) -> Unit = { stage, p ->
                    currentStage = stage
                    _state.value = GenState.Running(stage, p)
                }
                coroutineScope {
                    val progressJob = launch {
                        var p = 0.04f
                        var target = 0.22f
                        while (isActive && p < 0.86f) {
                            p += (target - p) * 0.045f + 0.0012f
                            if (p >= target) {
                                target = when (target) {
                                    0.22f -> { currentStage = GenStage.DESIGNING; 0.52f }
                                    0.52f -> { currentStage = GenStage.COMPOSING; 0.82f }
                                    else -> 0.87f
                                }
                            }
                            _state.value = GenState.Running(currentStage, p.coerceIn(0f, 0.88f))
                            delay(64)
                        }
                    }
                    val design = work(setStage)
                    progressJob.cancel()
                    _state.value = GenState.Running(GenStage.RENDERING, 1f)
                    delay(240) // let the bar touch 100% for a satisfying finish
                    Notifier.success(context, design.id, design.name)
                    _state.value = GenState.Success(design, isNew = true)
                }
            } catch (c: kotlinx.coroutines.CancellationException) {
                _state.value = GenState.Idle
            } catch (t: Throwable) {
                Notifier.failure(context, t.message ?: "Something went wrong.")
                _state.value = GenState.Failed(t)
            } finally {
                runCatching { context.stopService(Intent(context, GenerationKeeperService::class.java)) }
            }
        }
    }
}
