package de.voculupo.historicalcamplayout

import androidx.compose.ui.graphics.ImageBitmap
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

interface ProjectStorage {
    fun saveProject(project: PlannerProject)

    fun loadProject(): PlannerProject?

    fun exportProjectFile(project: PlannerProject)

    fun importProjectFile(): PlannerProject?

    fun saveInventory(presets: List<TentPreset>)

    fun loadInventory(): List<TentPreset>?

    /** Saves an image the planner canvas already rendered (see renderCampPng). */
    fun exportProjectPng(image: ImageBitmap)
}

expect object ProjectStorageProvider {
    fun create(): ProjectStorage
}

object PlannerJson {
    val json =
        Json {
            prettyPrint = true
            ignoreUnknownKeys = true
        }
}

fun PlannerProject.toJson(): String = PlannerJson.json.encodeToString(this)
