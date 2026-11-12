package de.voculupo.historicalcamplayout

/** Test double for [ProjectStorage]: keeps the "saved" project in memory so a second ViewModel can reload it. */
class InMemoryStorage : ProjectStorage {
    @Volatile var saved: PlannerProject? = null
    var saveCount = 0

    override fun saveProject(project: PlannerProject) {
        saved = project
        saveCount++
    }

    override fun loadProject(): PlannerProject? = saved

    override fun exportProjectFile(project: PlannerProject) = Unit

    override fun importProjectFile(): PlannerProject? = null

    override fun saveInventory(presets: List<TentPreset>) = Unit

    override fun loadInventory(): List<TentPreset>? = null

    override fun exportProjectPng(
        project: PlannerProject,
        presets: List<TentPreset>,
    ) = Unit
}
