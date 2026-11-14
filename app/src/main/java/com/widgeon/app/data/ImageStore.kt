package com.widgeon.app.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import android.util.LruCache

/**
 * Photos the user attaches to a design. Files live app-private in
 * widget_images/, are downscaled to ≤1080px and referenced by file name
 * inside WidgetDesign.images.
 */
object ImageStore {

    private const val MAX_SIDE = 1080

    private val cache = object : LruCache<String, Bitmap>(24) {}

    fun dir(context: Context): File = File(context.filesDir, "widget_images").apply { mkdirs() }

    /** Saves picked images (downscaled) and returns their file names. */
    suspend fun saveAll(context: Context, uris: List<Uri>): List<String> = withContext(Dispatchers.IO) {
        val saved = mutableListOf<String>()
        uris.forEachIndexed { i, uri ->
            runCatching {
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
                if (bounds.outWidth <= 0) return@runCatching

                var sample = 1
                var side = maxOf(bounds.outWidth, bounds.outHeight)
                while (side / 2 >= MAX_SIDE) { sample *= 2; side /= 2 }

                val opts = BitmapFactory.Options().apply { inSampleSize = sample }
                val bmp = context.contentResolver.openInputStream(uri)?.use {
                    BitmapFactory.decodeStream(it, null, opts)
                } ?: return@runCatching

                val name = "img_${System.currentTimeMillis()}_$i.jpg"
                val out = File(dir(context), name)
                out.outputStream().use { fos ->
                    bmp.compress(Bitmap.CompressFormat.JPEG, 88, fos)
                }
                saved.add(name)
            }
        }
        saved
    }

    fun load(context: Context, name: String): Bitmap? {
        if (name.isBlank() || !Regex("^[A-Za-z0-9._-]+$").matches(name)) return null
        cache.get(name)?.let { return it }
        val f = File(dir(context), name)
        if (!f.exists()) return null
        val bmp = BitmapFactory.decodeFile(f.absolutePath) ?: return null
        cache.put(name, bmp)
        return bmp
    }

    fun loader(context: Context): (String) -> Bitmap? = { name -> load(context, name) }

    fun delete(context: Context, name: String) {
        cache.remove(name)
        File(dir(context), name).delete()
    }
}
