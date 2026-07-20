package com.example.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import java.io.File

/**
 * Lightweight on-device persistence: app state as one JSON file in filesDir,
 * scan/chat photos as JPEG files. Also holds simple preferences (API key,
 * the user's vehicle for the AI Mechanic).
 */
class PartOutRepository(context: Context) {

    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences("partout_prefs", Context.MODE_PRIVATE)

    private val moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()
    private val stateAdapter = moshi.adapter(PersistedState::class.java)

    private val stateFile = File(appContext.filesDir, "partout_state.json")
    private val imagesDir = File(appContext.filesDir, "scan_images").apply { mkdirs() }

    // --- Preferences ---

    var apiKey: String
        get() = prefs.getString(PREF_API_KEY, "") ?: ""
        set(value) {
            prefs.edit().putString(PREF_API_KEY, value.trim()).apply()
        }

    var mechanicVehicle: String
        get() = prefs.getString(PREF_VEHICLE, "") ?: ""
        set(value) {
            prefs.edit().putString(PREF_VEHICLE, value).apply()
        }

    // --- Image storage ---

    fun saveImage(id: String, bitmap: Bitmap): String? {
        return try {
            val file = File(imagesDir, "$id.jpg")
            val scaled = scaleDown(bitmap, MAX_STORED_DIMENSION)
            file.outputStream().use { out ->
                scaled.compress(Bitmap.CompressFormat.JPEG, 85, out)
            }
            if (scaled !== bitmap) scaled.recycle()
            file.absolutePath
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun loadImage(path: String?): Bitmap? {
        if (path.isNullOrBlank()) return null
        return try {
            val file = File(path)
            if (!file.exists()) return null
            // Read bounds first so large files can be sampled down while decoding.
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(path, bounds)
            var sampleSize = 1
            while (maxOf(bounds.outWidth, bounds.outHeight) / (sampleSize * 2) >= MAX_LOADED_DIMENSION) {
                sampleSize *= 2
            }
            BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = sampleSize })
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun scaleDown(bitmap: Bitmap, maxDim: Int): Bitmap {
        if (bitmap.width <= maxDim && bitmap.height <= maxDim) return bitmap
        val scale = maxDim.toFloat() / maxOf(bitmap.width, bitmap.height)
        return Bitmap.createScaledBitmap(
            bitmap,
            (bitmap.width * scale).toInt().coerceAtLeast(1),
            (bitmap.height * scale).toInt().coerceAtLeast(1),
            true
        )
    }

    // --- State storage ---

    fun saveState(state: PersistedState) {
        try {
            val tmp = File(appContext.filesDir, "partout_state.json.tmp")
            tmp.writeText(stateAdapter.toJson(state))
            if (!tmp.renameTo(stateFile)) {
                stateFile.writeText(stateAdapter.toJson(state))
                tmp.delete()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun loadState(): PersistedState {
        return try {
            if (stateFile.exists()) {
                stateAdapter.fromJson(stateFile.readText()) ?: PersistedState()
            } else {
                PersistedState()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            PersistedState()
        }
    }

    companion object {
        private const val PREF_API_KEY = "gemini_api_key"
        private const val PREF_VEHICLE = "mechanic_vehicle"
        private const val MAX_STORED_DIMENSION = 1600
        private const val MAX_LOADED_DIMENSION = 1024
    }
}
