package de.voculupo.historicalcamplayout

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.provider.MediaStore
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import java.io.File

private const val PROJECT_FILE = "historical_camp_project.json"
private const val PROJECT_EXCHANGE_FILE = "historical_camp_project_exchange.json"
private const val INVENTORY_FILE = "historical_camp_inventory.json"
private const val PNG_FILE = "historical_camp_project_export.png"

object AndroidContextHolder {
    var appContext: Context? = null
}

actual object ProjectStorageProvider {
    actual fun create(): ProjectStorage = AndroidProjectStorage()
}

private class AndroidProjectStorage : ProjectStorage {
    private fun baseDir(): File? = AndroidContextHolder.appContext?.filesDir

    private fun resolver() = AndroidContextHolder.appContext?.contentResolver

    override fun saveProject(project: PlannerProject) {
        val dir = baseDir() ?: return
        SafeFiles.write(File(dir, PROJECT_FILE), PlannerJson.json.encodeToString(project))
    }

    override fun loadProject(): PlannerProject? {
        val dir = baseDir() ?: return null
        return SafeFiles.read(File(dir, PROJECT_FILE)) { PlannerJson.json.decodeFromString<PlannerProject>(it) }
    }

    override fun exportProjectFile(project: PlannerProject) {
        writeDownloadsTextFile(
            fileName = PROJECT_EXCHANGE_FILE,
            mimeType = "application/json",
            content = PlannerJson.json.encodeToString(project),
        )
    }

    override fun importProjectFile(): PlannerProject? {
        val text = readDownloadsTextFile(PROJECT_EXCHANGE_FILE) ?: return null
        return runCatching { PlannerJson.json.decodeFromString<PlannerProject>(text) }.getOrNull()
    }

    override fun saveInventory(presets: List<TentPreset>) {
        val dir = baseDir() ?: return
        SafeFiles.write(File(dir, INVENTORY_FILE), PlannerJson.json.encodeToString(presets))
    }

    override fun loadInventory(): List<TentPreset>? {
        val dir = baseDir() ?: return null
        return SafeFiles.read(File(dir, INVENTORY_FILE)) { PlannerJson.json.decodeFromString<List<TentPreset>>(it) }
    }

    override fun exportProjectPng(image: ImageBitmap) {
        writeDownloadsPng(PNG_FILE, image.asAndroidBitmap())
    }

    private fun writeDownloadsTextFile(
        fileName: String,
        mimeType: String,
        content: String,
    ) {
        val resolver = resolver() ?: return
        findDownloadsFileUri(fileName)?.let { existing -> resolver.delete(existing, null, null) }
        val values =
            ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
                put(MediaStore.MediaColumns.RELATIVE_PATH, "Download/")
            }
        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: return
        resolver.openOutputStream(uri)?.bufferedWriter()?.use { it.write(content) }
    }

    private fun readDownloadsTextFile(fileName: String): String? {
        val resolver = resolver() ?: return null
        val uri = findDownloadsFileUri(fileName) ?: return null
        return resolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
    }

    private fun writeDownloadsPng(
        fileName: String,
        bitmap: Bitmap,
    ) {
        val resolver = resolver() ?: return
        findDownloadsFileUri(fileName)?.let { existing -> resolver.delete(existing, null, null) }
        val values =
            ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                put(MediaStore.MediaColumns.MIME_TYPE, "image/png")
                put(MediaStore.MediaColumns.RELATIVE_PATH, "Download/")
            }
        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: return
        resolver.openOutputStream(uri)?.use { stream ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
        }
    }

    private fun findDownloadsFileUri(fileName: String): android.net.Uri? {
        val resolver = resolver() ?: return null
        val projection = arrayOf(MediaStore.MediaColumns._ID)
        val selection = "${MediaStore.MediaColumns.DISPLAY_NAME} = ?"
        val args = arrayOf(fileName)
        resolver
            .query(
                MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                projection,
                selection,
                args,
                null,
            )?.use { cursor ->
                if (!cursor.moveToFirst()) return null
                val id = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID))
                return android.net.Uri.withAppendedPath(MediaStore.Downloads.EXTERNAL_CONTENT_URI, id.toString())
            }
        return null
    }
}
