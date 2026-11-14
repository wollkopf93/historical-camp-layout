package de.voculupo.historicalcamplayout

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.provider.MediaStore
import androidx.compose.ui.geometry.Offset
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

    override fun exportProjectPng(
        project: PlannerProject,
        presets: List<TentPreset>,
    ) {
        val scene = buildPngExportScene(project, presets)
        val widthPx = scene.widthPx
        val heightPx = scene.heightPx
        val image = Bitmap.createBitmap(widthPx, heightPx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(image)

        val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
        val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
        val titlePaint =
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.FILL
                color = android.graphics.Color.rgb(0x1F, 0x1F, 0x1F)
                textSize = 36f
                isFakeBoldText = true
                setShadowLayer(4f, 0f, 1f, android.graphics.Color.argb(90, 255, 255, 255))
            }
        val labelPaint =
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.FILL
                color = android.graphics.Color.rgb(0x22, 0x22, 0x22)
                textSize = 18f
                isFakeBoldText = true
                setShadowLayer(3f, 0f, 1f, android.graphics.Color.argb(120, 255, 255, 255))
            }

        fillPaint.color = android.graphics.Color.argb(255, 0xEA, 0xE1, 0xCF)
        canvas.drawRect(0f, 0f, widthPx.toFloat(), heightPx.toFloat(), fillPaint)

        // Header with project title to make exports identifiable in gallery/file manager.
        val headerHeight = 64f
        fillPaint.color = android.graphics.Color.argb(185, 255, 255, 255)
        canvas.drawRect(0f, 0f, widthPx.toFloat(), headerHeight, fillPaint)
        strokePaint.color = android.graphics.Color.argb(48, 0, 0, 0)
        strokePaint.strokeWidth = 1.5f
        canvas.drawLine(0f, headerHeight, widthPx.toFloat(), headerHeight, strokePaint)
        val projectTitle = project.name.ifBlank { "Projekt" }
        canvas.drawText(projectTitle, 20f, 42f, titlePaint)

        strokePaint.color = android.graphics.Color.argb(26, 0, 0, 0)
        strokePaint.strokeWidth = 1f
        scene.gridXs.forEach { x -> canvas.drawLine(x, 0f, x, heightPx.toFloat(), strokePaint) }
        scene.gridYs.forEach { y -> canvas.drawLine(0f, y, widthPx.toFloat(), y, strokePaint) }

        val parcelLeft = scene.parcel.left
        val parcelTop = scene.parcel.top
        val parcelW = scene.parcel.width
        val parcelH = scene.parcel.height
        fillPaint.color = android.graphics.Color.argb(17, 0x4A, 0x5A, 0x4A)
        canvas.drawRect(parcelLeft, parcelTop, parcelLeft + parcelW, parcelTop + parcelH, fillPaint)
        strokePaint.color = android.graphics.Color.rgb(0x4A, 0x5A, 0x4A)
        strokePaint.strokeWidth = 3f
        canvas.drawRect(parcelLeft, parcelTop, parcelLeft + parcelW, parcelTop + parcelH, strokePaint)

        val guideStroke =
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                color = android.graphics.Color.argb(170, 0x4A, 0x5A, 0x4A)
                strokeWidth = 2f
            }
        val guideLabelPaint =
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.FILL
                color = android.graphics.Color.rgb(0x2F, 0x26, 0x1A)
                textSize = 22f
                isFakeBoldText = true
            }
        val guideLabelBg =
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.FILL
                color = android.graphics.Color.argb(225, 0xF2, 0xEA, 0xDB)
            }
        val topGuideY = (parcelTop - 20f).coerceAtLeast(12f)
        val leftGuideX = (parcelLeft - 20f).coerceAtLeast(12f)
        canvas.drawLine(parcelLeft, topGuideY, parcelLeft + parcelW, topGuideY, guideStroke)
        canvas.drawLine(parcelLeft, topGuideY - 6f, parcelLeft, topGuideY + 6f, guideStroke)
        canvas.drawLine(parcelLeft + parcelW, topGuideY - 6f, parcelLeft + parcelW, topGuideY + 6f, guideStroke)

        val widthLabel = "${"%.1f".format(project.parcel.widthMeters)} m"
        val widthLabelW = guideLabelPaint.measureText(widthLabel)
        val widthBaselineY = topGuideY - 9f
        val widthX = parcelLeft + parcelW / 2f - widthLabelW / 2f
        val widthBg =
            android.graphics.RectF(
                widthX - 7f,
                widthBaselineY - guideLabelPaint.textSize,
                widthX + widthLabelW + 7f,
                widthBaselineY + 6f,
            )
        canvas.drawRoundRect(widthBg, 6f, 6f, guideLabelBg)
        canvas.drawText(widthLabel, widthX, widthBaselineY, guideLabelPaint)

        canvas.drawLine(leftGuideX, parcelTop, leftGuideX, parcelTop + parcelH, guideStroke)
        canvas.drawLine(leftGuideX - 6f, parcelTop, leftGuideX + 6f, parcelTop, guideStroke)
        canvas.drawLine(leftGuideX - 6f, parcelTop + parcelH, leftGuideX + 6f, parcelTop + parcelH, guideStroke)

        val heightLabel = "${"%.1f".format(project.parcel.heightMeters)} m"
        val heightLabelW = guideLabelPaint.measureText(heightLabel)
        val heightBaselineY = parcelTop + parcelH / 2f + guideLabelPaint.textSize / 3f
        val heightX = leftGuideX + 10f
        val heightBg =
            android.graphics.RectF(
                heightX - 7f,
                heightBaselineY - guideLabelPaint.textSize,
                heightX + heightLabelW + 7f,
                heightBaselineY + 6f,
            )
        canvas.drawRoundRect(heightBg, 6f, 6f, guideLabelBg)
        canvas.drawText(heightLabel, heightX, heightBaselineY, guideLabelPaint)

        scene.zones.forEach { zone ->
            fillPaint.color = android.graphics.Color.argb(90, 170, 0, 0)
            canvas.drawPath(zone.toAndroidPath(), fillPaint)
        }

        scene.assets.forEach { asset ->
            val bodyPath = asset.outline.toAndroidPath()
            fillPaint.color = android.graphics.Color.argb(205, 0x6D, 0x5B, 0x44)
            canvas.drawPath(bodyPath, fillPaint)
            strokePaint.color = android.graphics.Color.rgb(0x3E, 0x3E, 0x3E)
            strokePaint.strokeWidth = 2f
            canvas.drawPath(bodyPath, strokePaint)

            strokePaint.color = android.graphics.Color.rgb(0x44, 0x44, 0x44)
            strokePaint.strokeWidth = 2f
            asset.ropes.forEach { (start, end) ->
                canvas.drawLine(start.x, start.y, end.x, end.y, strokePaint)
                fillPaint.color = android.graphics.Color.rgb(0x7A, 0x1F, 0x1F)
                canvas.drawCircle(end.x, end.y, 3f, fillPaint)
            }

            if (asset.label.isNotBlank()) {
                canvas.drawText(asset.label, asset.center.x + 8f, asset.center.y - 8f, labelPaint)
            }
        }

        writeDownloadsPng(PNG_FILE, image)
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

private fun List<Offset>.toAndroidPath(): Path =
    Path().apply {
        // Qualified: a bare isEmpty() here resolves to Path.isEmpty(), which is true for a new Path.
        if (this@toAndroidPath.isEmpty()) return@apply
        moveTo(first().x, first().y)
        for (i in 1 until size) lineTo(this@toAndroidPath[i].x, this@toAndroidPath[i].y)
        close()
    }
