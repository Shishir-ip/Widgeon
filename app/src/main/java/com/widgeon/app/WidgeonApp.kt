package com.widgeon.app

import android.app.Application
import android.content.Context
import com.widgeon.app.ai.GenerationEngine
import com.widgeon.app.data.DesignRepository
import com.widgeon.app.data.Prefs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class WidgeonApp : Application() {

    lateinit var appScope: CoroutineScope
        private set

    val prefs: Prefs by lazy { Prefs(this) }
    val designRepo: DesignRepository by lazy { DesignRepository(this) }
    val engine: GenerationEngine by lazy { GenerationEngine(this) }

    override fun onCreate() {
        super.onCreate()
        appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    }

    companion object {
        fun get(context: Context): WidgeonApp = context.applicationContext as WidgeonApp
    }
}

/** Convenience accessors for app-wide singletons. */
val Context.widgeon: WidgeonApp get() = WidgeonApp.get(this)
val Context.prefs: Prefs get() = WidgeonApp.get(this).prefs
val Context.designRepo: DesignRepository get() = WidgeonApp.get(this).designRepo
