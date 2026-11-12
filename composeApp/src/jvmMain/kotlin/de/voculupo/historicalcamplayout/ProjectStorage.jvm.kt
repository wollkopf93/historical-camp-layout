package de.voculupo.historicalcamplayout

import androidx.compose.ui.geometry.Offset
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
        SafeFiles.write(File(baseDir, PROJECT_FILE), PlannerJson.json.encodeToString(project))
    }

    override fun loadProject(): PlannerProject? =
        SafeFiles.read(File(baseDir, PROJECT_FILE)) { PlannerJson.json.decodeFromString<PlannerProject>(it) }

    override fun exportProjectFile(project: PlannerProject) {
        // A cancelled dialog means "don't export", not "write to a default location".
        val target = chooseSaveFile(title = "Projektdatei exportieren", defaultName = PROJECT_EXCHANGE_FILE) ?: return
        SafeFiles.write(target, PlannerJson.json.encodeToString(project))
    }

    override fun importProjectFile(): PlannerProject? {
        val file = chooseOpenFile(title = "Projektdatei importieren", defaultName = PROJECT_EXCHANGE_FILE) ?: return null
        if (!file.exists()) return null
        // Import must not move the user's chosen file aside, so it doesn't go through SafeFiles.read.
        return runCatching { PlannerJson.json.decodeFromString<PlannerProject>(file.readText()) }.getOrNull()
    }

    override fun saveInventory(presets: List<TentPreset>) {
        SafeFiles.write(File(baseDir, INVENTORY_FILE), PlannerJson.json.encodeToString(presets))
    }

    override fun loadInventory(): List<TentPreset>? =
        SafeFiles.read(File(baseDir, INVENTORY_FILE)) { PlannerJson.json.decodeFromString<List<TentPreset>>(it) }

    override fun exportProjectPng(
        project: PlannerProject,
        presets: List<TentPreset>,
    ) {
        val target = chooseSaveFile(title = "PNG exportieren", defaultName = PNG_FILE) ?: return
        ImageIO.write(renderPng(project, buildPngExportScene(project, presets)), "png", target)
    }
}

private fun renderPng(
    project: PlannerProject,
    scene: PngExportScene,
): BufferedImage {
    val widthPx = scene.widthPx
    val heightPx = scene.heightPx
    val image = BufferedImage(widthPx, heightPx, BufferedImage.TYPE_INT_ARGB)
    val g = image.createGraphics()
    g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)

    g.color = Color(0xEA, 0xE1, 0xCF)
    g.fillRect(0, 0, widthPx, heightPx)

    g.color = Color(0, 0, 0, 26)
    scene.gridXs.forEach { x -> g.drawLine(x.toInt(), 0, x.toInt(), heightPx) }
    scene.gridYs.forEach { y -> g.drawLine(0, y.toInt(), widthPx, y.toInt()) }

    val parcelLeft = scene.parcel.left.toInt()
    val parcelTop = scene.parcel.top.toInt()
    val parcelW = scene.parcel.width.toInt()
    val parcelH = scene.parcel.height.toInt()
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

    scene.zones.forEach { zone ->
        g.color = Color(170, 0, 0, 90)
        g.fill(zone.toPath2D())
    }

    scene.assets.forEach { asset ->
        val bodyPath = asset.outline.toPath2D()
        g.color = Color(0x6D, 0x5B, 0x44, 205)
        g.fill(bodyPath)
        g.color = Color(0x3E, 0x3E, 0x3E)
        g.stroke = BasicStroke(2f)
        g.draw(bodyPath)

        g.stroke = BasicStroke(2f)
        asset.ropes.forEach { (start, end) ->
            g.color = Color(0x44, 0x44, 0x44)
            g.drawLine(start.x.toInt(), start.y.toInt(), end.x.toInt(), end.y.toInt())
            g.color = Color(0x7A, 0x1F, 0x1F)
            g.fillOval(end.x.toInt() - 3, end.y.toInt() - 3, 6, 6)
        }
    }

    g.dispose()
    return image
}

private fun List<Offset>.toPath2D(): Path2D.Float =
    Path2D.Float().apply {
        if (isEmpty()) return@apply
        moveTo(first().x.toDouble(), first().y.toDouble())
        for (i in 1 until size) lineTo(this@toPath2D[i].x.toDouble(), this@toPath2D[i].y.toDouble())
        closePath()
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
