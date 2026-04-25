package de.voculupo.historicalcamplayout.data.storage

import de.voculupo.historicalcamplayout.PlannerJson
import de.voculupo.historicalcamplayout.PlannerProject
import de.voculupo.historicalcamplayout.ProjectStorageProvider

typealias ProjectStorage = de.voculupo.historicalcamplayout.ProjectStorage
typealias TentPreset = de.voculupo.historicalcamplayout.TentPreset

object StorageProvider {
    fun create(): ProjectStorage = ProjectStorageProvider.create()
}

fun PlannerProject.toJsonString(): String =
    PlannerJson.json.encodeToString(
        serializer =
            de.voculupo.historicalcamplayout.domain.model.PlannerProject
                .serializer(),
        value = this,
    )
