package com.widgeon.app.data

import android.content.Context
import com.widgeon.app.prefs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.io.File

/**
 * File-backed store for all widget designs (designs.json in filesDir).
 * Small scale, so a single JSON document + in-memory cache is plenty.
 */
class DesignRepository(private val context: Context) {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = true
        prettyPrint = false
    }

    private val mutex = Mutex()
    private val file: File get() = File(context.filesDir, "designs.json")
    private var cache: MutableList<WidgetDesign>? = null

    suspend fun list(): List<WidgetDesign> = withContext(Dispatchers.IO) {
        mutex.withLock { load().sortedByDescending { it.updatedAt } }
    }

    suspend fun get(id: String): WidgetDesign? = withContext(Dispatchers.IO) {
        mutex.withLock { load().firstOrNull { it.id == id } }
    }

    suspend fun upsert(design: WidgetDesign): WidgetDesign = withContext(Dispatchers.IO) {
        mutex.withLock {
            val stamped = design.copy(updatedAt = System.currentTimeMillis())
            val all = load().toMutableList()
            val idx = all.indexOfFirst { it.id == stamped.id }
            if (idx >= 0) all[idx] = stamped else all.add(stamped)
            persist(all)
            stamped
        }
    }

    suspend fun delete(id: String) = withContext(Dispatchers.IO) {
        mutex.withLock {
            val all = load().toMutableList()
            all.removeAll { it.id == id }
            persist(all)
        }
        context.prefs.clearBindingsFor(id)
    }

    suspend fun deleteAll() = withContext(Dispatchers.IO) {
        mutex.withLock { persist(emptyList()) }
        context.prefs.allBindings().keys.forEach { context.prefs.unbindWidget(it) }
    }

    suspend fun count(): Int = withContext(Dispatchers.IO) { mutex.withLock { load().size } }

    private fun load(): MutableList<WidgetDesign> {
        cache?.let { return it }
        return try {
            if (file.exists()) {
                val list = json.decodeFromString(ListSerializer(WidgetDesign.serializer()), file.readText())
                cache = list.toMutableList()
                cache!!
            } else {
                cache = mutableListOf()
                cache!!
            }
        } catch (t: Throwable) {
            cache = mutableListOf()
            cache!!
        }
    }

    private fun persist(all: List<WidgetDesign>) {
        try {
            file.parentFile?.mkdirs()
            file.writeText(json.encodeToString(ListSerializer(WidgetDesign.serializer()), all))
            cache = all.toMutableList()
        } catch (_: Throwable) {
            // Storage failure: keep memory state, next save retries.
        }
    }

    companion object {
        fun newId(): String =
            "w" + System.currentTimeMillis().toString(36) + (100..999).random().toString(36)
    }
}
