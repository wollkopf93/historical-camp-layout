package de.voculupo.historicalcamplayout

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toAwtImage
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import java.awt.FileDialog
import java.awt.Frame
import java.io.File
import javax.imageio.ImageIO

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

    override fun exportProjectPng(image: ImageBitmap) {
        val target = chooseSaveFile(title = "PNG exportieren", defaultName = PNG_FILE) ?: return
        ImageIO.write(image.toAwtImage(), "png", target)
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
