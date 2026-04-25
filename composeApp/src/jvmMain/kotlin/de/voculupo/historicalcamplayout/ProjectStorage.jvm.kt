package de.voculupo.historicalcamplayout

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import java.awt.BasicStroke
import java.awt.Color
import java.awt.FileDialog
import java.awt.Font
import java.awt.Frame
import java.awt.RenderingHints
import java.awt.geom.Path2D
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import kotlin.math.max
import kotlin.math.min

private const val PROJECT_FILE = "historical_camp_project.json"
private const val PROJECT_EXCHANGE_FILE = "historical_camp_project_exchange.json"
private const val INVENTORY_FILE = "historical_camp_inventory.json"
private const val PNG_FILE = "historical_camp_project_export.png"

actual object ProjectStorageProvider {
    actual fun create(): ProjectStorage = JvmProjectStorage()
}

private class JvmProjectStorage : ProjectStorage {
    private val baseDir: File = File(System.getProperty("user.home"), "Lagerplaner").also { it.mkdirs() }

    override fun saveProject(project: PlannerProject) {
        File(baseDir, PROJECT_FILE).writeText(PlannerJson.json.encodeToString(project))
    }

    override fun loadProject(): PlannerProject? {
        val file = File(baseDir, PROJECT_FILE)
        if (!file.exists()) return null
        return runCatching { PlannerJson.json.decodeFromString<PlannerProject>(file.readText()) }.getOrNull()
    }

    override fun exportProjectFile(project: PlannerProject) {
        val target =
            chooseSaveFile(
                title = "Projektdatei exportieren",
                defaultName = PROJECT_EXCHANGE_FILE,
            ) ?: File(baseDir, PROJECT_EXCHANGE_FILE)
        target.writeText(PlannerJson.json.encodeToString(project))
    }

    override fun importProjectFile(): PlannerProject? {
        val file =
            chooseOpenFile(
                title = "Projektdatei importieren",
                defaultName = PROJECT_EXCHANGE_FILE,
            ) ?: File(baseDir, PROJECT_EXCHANGE_FILE)
        if (!file.exists()) return null
        return runCatching { PlannerJson.json.decodeFromString<PlannerProject>(file.readText()) }.getOrNull()
    }

    override fun saveInventory(presets: List<TentPreset>) {
        File(baseDir, INVENTORY_FILE).writeText(PlannerJson.json.encodeToString(presets))
    }

    override fun loadInventory(): List<TentPreset>? {
        val file = File(baseDir, INVENTORY_FILE)
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
        val image = BufferedImage(widthPx, heightPx, BufferedImage.TYPE_INT_ARGB)
        val g = image.createGraphics()
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)

        g.color = Color(0xEA, 0xE1, 0xCF)
        g.fillRect(0, 0, widthPx, heightPx)

        val spacingPx = (project.gridMeters * ppm).coerceAtLeast(8f)
        g.color = Color(0, 0, 0, 26)
        var x = ((-meterBounds.minX * ppm) % spacingPx + spacingPx) % spacingPx
        while (x <= widthPx) {
            g.drawLine(x.toInt(), 0, x.toInt(), heightPx)
            x += spacingPx
        }
        var y = ((-meterBounds.minY * ppm) % spacingPx + spacingPx) % spacingPx
        while (y <= heightPx) {
            g.drawLine(0, y.toInt(), widthPx, y.toInt())
            y += spacingPx
        }

        val parcelLeft = ((0f - meterBounds.minX) * ppm).toInt()
        val parcelTop = ((0f - meterBounds.minY) * ppm).toInt()
        val parcelW = (project.parcel.widthMeters * ppm).toInt()
        val parcelH = (project.parcel.heightMeters * ppm).toInt()
        g.color = Color(0x4A, 0x5A, 0x4A, 17)
        g.fillRect(parcelLeft, parcelTop, parcelW, parcelH)
        g.color = Color(0x4A, 0x5A, 0x4A)
        g.stroke = BasicStroke(3f)
        g.drawRect(parcelLeft, parcelTop, parcelW, parcelH)

        val oldStroke = g.stroke
        val oldFont = g.font
        g.color = Color(0x4A, 0x5A, 0x4A, 170)
        g.stroke = BasicStroke(2f)
        val topGuideY = max(12f, parcelTop - 20f).toInt()
        val leftGuideX = max(12f, parcelLeft - 20f).toInt()
        g.drawLine(parcelLeft, topGuideY, parcelLeft + parcelW, topGuideY)
        g.drawLine(parcelLeft, topGuideY - 6, parcelLeft, topGuideY + 6)
        g.drawLine(parcelLeft + parcelW, topGuideY - 6, parcelLeft + parcelW, topGuideY + 6)
        g.drawLine(leftGuideX, parcelTop, leftGuideX, parcelTop + parcelH)
        g.drawLine(leftGuideX - 6, parcelTop, leftGuideX + 6, parcelTop)
        g.drawLine(leftGuideX - 6, parcelTop + parcelH, leftGuideX + 6, parcelTop + parcelH)

        g.font = Font("SansSerif", Font.BOLD, 16)
        val fm = g.fontMetrics
        val widthLabel = "${"%.1f".format(project.parcel.widthMeters)} m"
        val widthLabelW = fm.stringWidth(widthLabel)
        val widthTextX = parcelLeft + parcelW / 2 - widthLabelW / 2
        val widthTextY = topGuideY - 10
        g.color = Color(0xF2, 0xEA, 0xDB, 225)
        g.fillRoundRect(widthTextX - 7, widthTextY - fm.ascent, widthLabelW + 14, fm.height + 4, 8, 8)
        g.color = Color(0x2F, 0x26, 0x1A)
        g.drawString(widthLabel, widthTextX, widthTextY)

        val heightLabel = "${"%.1f".format(project.parcel.heightMeters)} m"
        val heightLabelW = fm.stringWidth(heightLabel)
        val heightTextX = leftGuideX + 10
        val heightTextY = parcelTop + parcelH / 2 + fm.ascent / 2
        g.color = Color(0xF2, 0xEA, 0xDB, 225)
        g.fillRoundRect(heightTextX - 7, heightTextY - fm.ascent, heightLabelW + 14, fm.height + 4, 8, 8)
        g.color = Color(0x2F, 0x26, 0x1A)
        g.drawString(heightLabel, heightTextX, heightTextY)
        g.stroke = oldStroke
        g.font = oldFont

        project.blockZones.forEach { zone ->
            if (zone.points.size < 3) return@forEach
            val p = Path2D.Float()
            val first = zone.points.first()
            p.moveTo((first.x - meterBounds.minX) * ppm.toDouble(), (first.y - meterBounds.minY) * ppm.toDouble())
            for (i in 1 until zone.points.size) {
                val pt = zone.points[i]
                p.lineTo((pt.x - meterBounds.minX) * ppm.toDouble(), (pt.y - meterBounds.minY) * ppm.toDouble())
            }
            p.closePath()
            g.color = Color(170, 0, 0, 90)
            g.fill(p)
        }

        project.assets.forEach { asset ->
            val preset = presetsById[asset.presetId] ?: return@forEach
            val bodyPath = worldAssetPath(asset, preset, meterBounds, ppm)
            g.color = Color(0x6D, 0x5B, 0x44, 205)
            g.fill(bodyPath)
            g.color = Color(0x3E, 0x3E, 0x3E)
            g.stroke = BasicStroke(2f)
            g.draw(bodyPath)

            if (asset.showRopes) {
                g.color = Color(0x44, 0x44, 0x44)
                g.stroke = BasicStroke(2f)
                GeometryEngine.ropeSegments(asset, preset).forEach { rope ->
                    val x1 = ((rope.start.x - meterBounds.minX) * ppm).toInt()
                    val y1 = ((rope.start.y - meterBounds.minY) * ppm).toInt()
                    val x2 = ((rope.end.x - meterBounds.minX) * ppm).toInt()
                    val y2 = ((rope.end.y - meterBounds.minY) * ppm).toInt()
                    g.drawLine(x1, y1, x2, y2)
                    g.color = Color(0x7A, 0x1F, 0x1F)
                    g.fillOval(x2 - 3, y2 - 3, 6, 6)
                    g.color = Color(0x44, 0x44, 0x44)
                }
            }
        }

        g.dispose()
        val target =
            chooseSaveFile(
                title = "PNG exportieren",
                defaultName = PNG_FILE,
            ) ?: File(baseDir, PNG_FILE)
        ImageIO.write(image, "png", target)
    }
}

private fun chooseSaveFile(
    title: String,
    defaultName: String,
): File? {
    val dialog =
        FileDialog(null as Frame?, title, FileDialog.SAVE).apply {
            directory = File(System.getProperty("user.home"), "Lagerplaner").absolutePath
            file = defaultName
            isVisible = true
        }
    val chosen = dialog.file ?: return null
    return File(dialog.directory ?: "", chosen)
}

private fun chooseOpenFile(
    title: String,
    defaultName: String,
): File? {
    val dialog =
        FileDialog(null as Frame?, title, FileDialog.LOAD).apply {
            directory = File(System.getProperty("user.home"), "Lagerplaner").absolutePath
            file = defaultName
            isVisible = true
        }
    val chosen = dialog.file ?: return null
    return File(dialog.directory ?: "", chosen)
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
): Path2D.Float {
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
    val path = Path2D.Float()
    if (points.isEmpty()) return path
    val firstWorld = GeometryEngine.worldPoint(asset, points.first())
    path.moveTo(
        ((firstWorld.x - bounds.minX) * pixelsPerMeter).toDouble(),
        ((firstWorld.y - bounds.minY) * pixelsPerMeter).toDouble(),
    )
    for (i in 1 until points.size) {
        val world = GeometryEngine.worldPoint(asset, points[i])
        path.lineTo(
            ((world.x - bounds.minX) * pixelsPerMeter).toDouble(),
            ((world.y - bounds.minY) * pixelsPerMeter).toDouble(),
        )
    }
    path.closePath()
    return path
}
