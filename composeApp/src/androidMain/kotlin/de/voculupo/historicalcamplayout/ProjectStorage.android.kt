package de.voculupo.historicalcamplayout

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.provider.MediaStore
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import java.io.File
import kotlin.math.max
import kotlin.math.min

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
        File(dir, PROJECT_FILE).writeText(PlannerJson.json.encodeToString(project))
    }

    override fun loadProject(): PlannerProject? {
        val dir = baseDir() ?: return null
        val file = File(dir, PROJECT_FILE)
        if (!file.exists()) return null
        return runCatching { PlannerJson.json.decodeFromString<PlannerProject>(file.readText()) }.getOrNull()
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
        File(dir, INVENTORY_FILE).writeText(PlannerJson.json.encodeToString(presets))
    }

    override fun loadInventory(): List<TentPreset>? {
        val dir = baseDir() ?: return null
        val file = File(dir, INVENTORY_FILE)
        if (!file.exists()) return null
        return runCatching { PlannerJson.json.decodeFromString<List<TentPreset>>(file.readText()) }.getOrNull()
    }

    override fun exportProjectPng(
        project: PlannerProject,
        presets: List<TentPreset>,
    ) {
        val presetsById = presets.associateBy { it.id }
        val meterBounds = exportBoundsMeters(project, presetsById, paddingMeters = 1.0f)
        val ppm = project.pixelsPerMeter.coerceAtLeast(20f)
        val widthPx = ((meterBounds.maxX - meterBounds.minX) * ppm).toInt().coerceIn(800, 6000)
        val heightPx = ((meterBounds.maxY - meterBounds.minY) * ppm).toInt().coerceIn(600, 6000)
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

        val spacingPx = (project.gridMeters * ppm).coerceAtLeast(8f)
        strokePaint.color = android.graphics.Color.argb(26, 0, 0, 0)
        strokePaint.strokeWidth = 1f
        var x = ((-meterBounds.minX * ppm) % spacingPx + spacingPx) % spacingPx
        while (x <= widthPx) {
            canvas.drawLine(x, 0f, x, heightPx.toFloat(), strokePaint)
            x += spacingPx
        }
        var y = ((-meterBounds.minY * ppm) % spacingPx + spacingPx) % spacingPx
        while (y <= heightPx) {
            canvas.drawLine(0f, y, widthPx.toFloat(), y, strokePaint)
            y += spacingPx
        }

        val parcelLeft = ((0f - meterBounds.minX) * ppm)
        val parcelTop = ((0f - meterBounds.minY) * ppm)
        val parcelW = project.parcel.widthMeters * ppm
        val parcelH = project.parcel.heightMeters * ppm
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

        project.blockZones.forEach { zone ->
            if (zone.points.size < 3) return@forEach
            val p = Path()
            val first = zone.points.first()
            p.moveTo((first.x - meterBounds.minX) * ppm, (first.y - meterBounds.minY) * ppm)
            for (i in 1 until zone.points.size) {
                val pt = zone.points[i]
                p.lineTo((pt.x - meterBounds.minX) * ppm, (pt.y - meterBounds.minY) * ppm)
            }
            p.close()
            fillPaint.color = android.graphics.Color.argb(90, 170, 0, 0)
            canvas.drawPath(p, fillPaint)
        }

        project.assets.forEach { asset ->
            val preset = presetsById[asset.presetId] ?: return@forEach
            val bodyPath = worldAssetPath(asset, preset, meterBounds, ppm)
            fillPaint.color = android.graphics.Color.argb(205, 0x6D, 0x5B, 0x44)
            canvas.drawPath(bodyPath, fillPaint)
            strokePaint.color = android.graphics.Color.rgb(0x3E, 0x3E, 0x3E)
            strokePaint.strokeWidth = 2f
            canvas.drawPath(bodyPath, strokePaint)

            if (asset.showRopes) {
                strokePaint.color = android.graphics.Color.rgb(0x44, 0x44, 0x44)
                strokePaint.strokeWidth = 2f
                GeometryEngine.ropeSegments(asset, preset).forEach { rope ->
                    val x1 = (rope.start.x - meterBounds.minX) * ppm
                    val y1 = (rope.start.y - meterBounds.minY) * ppm
                    val x2 = (rope.end.x - meterBounds.minX) * ppm
                    val y2 = (rope.end.y - meterBounds.minY) * ppm
                    canvas.drawLine(x1, y1, x2, y2, strokePaint)
                    fillPaint.color = android.graphics.Color.rgb(0x7A, 0x1F, 0x1F)
                    canvas.drawCircle(x2, y2, 3f, fillPaint)
                }
            }

            val exportLabel = asset.label.ifBlank { preset.name }
            if (exportLabel.isNotBlank()) {
                val textX = (asset.position.x - meterBounds.minX) * ppm + 8f
                val textY = (asset.position.y - meterBounds.minY) * ppm - 8f
                canvas.drawText(exportLabel, textX, textY, labelPaint)
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

private data class BoundsMeters(
    val minX: Float,
    val minY: Float,
    val maxX: Float,
    val maxY: Float,
)

private fun exportBoundsMeters(
    project: PlannerProject,
    presetsById: Map<String, TentPreset>,
    paddingMeters: Float,
): BoundsMeters {
    var minX = 0f
    var minY = 0f
    var maxX = project.parcel.widthMeters
    var maxY = project.parcel.heightMeters

    project.assets.forEach { asset ->
        val preset = presetsById[asset.presetId] ?: return@forEach
        val body = GeometryEngine.assetBodyAabb(asset, preset)
        minX = min(minX, body.left)
        minY = min(minY, body.top)
        maxX = max(maxX, body.right)
        maxY = max(maxY, body.bottom)
        GeometryEngine.ropeSegments(asset, preset).forEach { rope ->
            minX = min(minX, min(rope.start.x, rope.end.x))
            minY = min(minY, min(rope.start.y, rope.end.y))
            maxX = max(maxX, max(rope.start.x, rope.end.x))
            maxY = max(maxY, max(rope.start.y, rope.end.y))
        }
    }
    return BoundsMeters(
        minX = minX - paddingMeters,
        minY = minY - paddingMeters,
        maxX = maxX + paddingMeters,
        maxY = maxY + paddingMeters,
    )
}

private fun worldAssetPath(
    asset: PlacedAsset,
    preset: TentPreset,
    bounds: BoundsMeters,
    pixelsPerMeter: Float,
): Path {
    val points =
        if (preset.polygonPoints.isNotEmpty()) {
            preset.polygonPoints
        } else {
            GeometryHelper.generatePoints(preset.shape, preset.widthMeters, preset.heightMeters).ifEmpty {
                val hw = preset.widthMeters / 2f
                val hh = preset.heightMeters / 2f
                listOf(Vec2(-hw, -hh), Vec2(hw, -hh), Vec2(hw, hh), Vec2(-hw, hh))
            }
        }
    val path = Path()
    if (points.isEmpty()) return path
    val firstWorld = GeometryEngine.worldPoint(asset, points.first())
    path.moveTo(
        (firstWorld.x - bounds.minX) * pixelsPerMeter,
        (firstWorld.y - bounds.minY) * pixelsPerMeter,
    )
    for (i in 1 until points.size) {
        val world = GeometryEngine.worldPoint(asset, points[i])
        path.lineTo(
            (world.x - bounds.minX) * pixelsPerMeter,
            (world.y - bounds.minY) * pixelsPerMeter,
        )
    }
    path.close()
    return path
}
