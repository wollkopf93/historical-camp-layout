package de.voculupo.historicalcamplayout

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

interface ProjectStorage {
    fun saveProject(project: PlannerProject)
    fun loadProject(): PlannerProject?
    fun exportProjectFile(project: PlannerProject)
    fun importProjectFile(): PlannerProject?
    fun saveInventory(presets: List<TentPreset>)
    fun loadInventory(): List<TentPreset>?
    fun exportProjectPng(project: PlannerProject, presets: List<TentPreset>)
}

expect object ProjectStorageProvider {
    fun create(): ProjectStorage
}

object PlannerJson {
    val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
    }
}

fun PlannerProject.toJson(): String = PlannerJson.json.encodeToString(this)

