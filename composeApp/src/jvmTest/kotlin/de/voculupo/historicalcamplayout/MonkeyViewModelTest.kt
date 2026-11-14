package de.voculupo.historicalcamplayout

import de.voculupo.historicalcamplayout.features.planner.state.PlannerViewModel
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Monkey test: random sequences of everything a user can do through the ViewModel, with random (also
 * silly) arguments. After every step the state must stay sane; at the end undo must walk back to the
 * start, redo forward to the end, and a restart must restore what was saved. Failures name the seed
 * and the steps so a run can be replayed.
 */
class MonkeyViewModelTest {
    private val presetIds = defaultPresets().map { it.id }

    private class Run(
        val seed: Int,
    ) {
        val rnd = Random(seed)
        val log = mutableListOf<String>()
    }

    private fun Run.float(
        from: Float,
        to: Float,
    ): Float =
        when (rnd.nextInt(20)) {
            0 -> 0f

            1 -> -1f * rnd.nextFloat() * 1000f

            // silly negatives
            2 -> 1e6f

            else -> from + rnd.nextFloat() * (to - from)
        }

    private fun Run.point(vm: PlannerViewModel): Vec2 {
        val p = vm.uiState.value.project.parcel
        return Vec2(float(-2f, p.widthMeters + 2f), float(-2f, p.heightMeters + 2f))
    }

    private fun Run.anyAssetIds(vm: PlannerViewModel): Set<String> {
        val ids =
            vm.uiState.value.project.assets
                .map { it.id }
        if (ids.isEmpty()) return emptySet()
        return ids.shuffled(rnd).take(1 + rnd.nextInt(ids.size)).toSet()
    }

    private fun Run.randomRequest(vm: PlannerViewModel): AutoSetupRequest {
        val tents = presetIds.filterNot { it.startsWith("sun_sail_") }
        val sails = presetIds.filter { it.startsWith("sun_sail_") }
        return AutoSetupRequest(
            parcel = Parcel(10f + rnd.nextInt(40), 8f + rnd.nextInt(30)),
            tentPresetIds = List(rnd.nextInt(8)) { tents.random(rnd) },
            sailPresetIds = List(rnd.nextInt(3)) { sails.random(rnd) },
            visitorSide = VisitorSide.entries.random(rnd),
            shape = RingShape.entries.random(rnd),
            joinSails = rnd.nextBoolean(),
            replaceExisting = rnd.nextBoolean(),
            freeAreas =
                if (rnd.nextInt(3) ==
                    0
                ) {
                    listOf(FreeArea(FreeAreaKind.entries.random(rnd), "Fläche", 2f + rnd.nextInt(6)))
                } else {
                    emptyList()
                },
            spacing = vm.uiState.value.project.spacing,
            optimize = rnd.nextInt(3) == 0, // keeps the run fast
        )
    }

    /** One random user action; returns its description for the replay log. */
    private fun Run.step(vm: PlannerViewModel): String {
        val s = vm.uiState.value
        return when (rnd.nextInt(34)) {
            0, 1, 2 -> {
                val id = presetIds.random(rnd)
                val p = point(vm)
                vm.placePresetAt(id, p)
                "place $id at $p"
            }

            3 -> {
                vm.selectPreset(presetIds.random(rnd))
                val p = point(vm)
                vm.placeAssetAt(p)
                "placeAssetAt $p"
            }

            4, 5 -> {
                val sel = anyAssetIds(vm)
                vm.setSelection(sel)
                "select $sel"
            }

            6 -> {
                val id =
                    s.project.assets
                        .randomOrNull(rnd)
                        ?.id
                vm.toggleSelection(id)
                "toggle $id"
            }

            7, 8 -> {
                val d = Vec2(float(-5f, 5f), float(-5f, 5f))
                repeat(1 + rnd.nextInt(5)) { vm.dragSelection(d) }
                vm.finishInteractionWithGridSnap()
                "drag $d"
            }

            9 -> {
                val d = float(-360f, 360f)
                vm.rotateSelected(d)
                "rotateBy $d"
            }

            10 -> {
                val d = float(0f, 360f)
                vm.setSelectedRotation(d)
                vm.commitHistory()
                "rotation $d"
            }

            11 -> {
                val label = listOf("", "Küche", " ", "x".repeat(200), "Zelt\nneu").random(rnd)
                vm.setSelectedLabel(label)
                "label '${label.take(10)}'"
            }

            12 -> {
                vm.toggleLockSelected()
                "lock"
            }

            13 -> {
                vm.toggleRopesForSelected()
                "toggle ropes"
            }

            14 -> {
                vm.addRopeToSelected()
                "add rope"
            }

            15 -> {
                vm.removeRopeFromSelected()
                "remove rope"
            }

            16 -> {
                vm.restoreDefaultRopesForSelected()
                "restore ropes"
            }

            17 -> {
                val i = listOf(null, -1, 0, 1, 3, 99).random(rnd)
                vm.selectRopeIndex(i)
                val v = float(-10f, 10f)
                when (rnd.nextInt(4)) {
                    0 -> vm.setSelectedRopeLengthScale(v)
                    1 -> vm.setSelectedRopeAngleOffset(v * 30f)
                    2 -> vm.setSelectedRopeAnchorX(v)
                    else -> vm.setSelectedRopeAnchorY(v)
                }
                vm.commitHistory()
                "rope $i value $v"
            }

            18 -> {
                val i = rnd.nextInt(-1, 6)
                val p = point(vm)
                if (rnd.nextBoolean()) vm.updateRopeStakeWorld(i, p) else vm.updateRopeAnchorWorld(i, p)
                vm.commitHistory()
                "rope drag $i to $p"
            }

            19 -> {
                if (rnd.nextBoolean()) vm.groupSelection() else vm.ungroupSelection()
                "group/ungroup"
            }

            20 -> {
                vm.deleteSelection()
                "delete"
            }

            21 -> {
                vm.duplicateSelection("Kopie")
                "duplicate"
            }

            22, 23 -> {
                vm.undo()
                "undo"
            }

            24 -> {
                vm.redo()
                "redo"
            }

            25 -> {
                val w = float(0f, 60f)
                val h = float(0f, 50f)
                vm.updateParcel(w, h)
                "parcel $w x $h"
            }

            26 -> {
                when (rnd.nextInt(4)) {
                    0 -> vm.zoomIn()
                    1 -> vm.zoomOut()
                    2 -> vm.resetZoom()
                    else -> vm.setZoomLive(float(1f, 500f))
                }
                "zoom"
            }

            27 -> {
                vm.setTool(PlannerToolMode.entries.random(rnd))
                val p = point(vm)
                if (rnd.nextBoolean()) vm.onMeasureTap(p) else vm.onBlockZoneTap(p)
                "tool tap $p"
            }

            28 -> {
                vm.clearBlockZones()
                "clear zones"
            }

            29 -> {
                val req = randomRequest(vm)
                vm.autoSetup(req)
                "autoSetup ${req.shape} ${req.tentPresetIds.size} tents ${req.sailPresetIds.size} sails"
            }

            30 -> {
                val rules =
                    SpacingRules(
                        float(0f, 3f).coerceIn(0f, 10f),
                        float(0f, 3f).coerceIn(0f, 10f),
                        if (rnd.nextBoolean()) null else float(0f, 10f).coerceIn(0f, 50f),
                        float(0f, 3f).coerceIn(0f, 10f),
                        float(0f, 3f).coerceIn(0f, 10f),
                    )
                vm.setSpacing(rules)
                "spacing $rules"
            }

            31 -> {
                val type = s.presets.random(rnd)
                val edited =
                    when (rnd.nextInt(4)) {
                        0 -> type.copy(spacing = SpacingOverride(toTents = float(0f, 4f).coerceIn(0f, 10f)))
                        1 -> type.copy(ropeAnchors = type.ropeAnchors.map { it.copy(lengthMeters = float(0.3f, 4f).coerceIn(0.1f, 10f)) })
                        2 -> type.copy(defaultSafetyMarginMeters = float(0f, 2f).coerceIn(0f, 5f))
                        else -> type.copy(id = "custom_${rnd.nextInt(1000)}", name = "Eigen ${rnd.nextInt(100)}")
                    }
                vm.savePreset(edited)
                "savePreset ${edited.id}"
            }

            32 -> {
                vm.setProjectName(listOf("", "Lager", "x".repeat(300)).random(rnd))
                "rename project"
            }

            else -> {
                vm.resetProject()
                "reset"
            }
        }
    }

    // Zoom is a view setting; positions are rounded to 1 mm so float noise from re-clamping doesn't count.
    private fun PlannerProject.withoutZoom() =
        copy(
            pixelsPerMeter = 0f,
            assets =
                assets.map {
                    it.copy(
                        position = Vec2(kotlin.math.round(it.position.x * 1000f), kotlin.math.round(it.position.y * 1000f)),
                    )
                },
        )

    private fun runOnce(
        seed: Int,
        steps: Int,
    ): List<String> {
        val run = Run(seed)
        val storage = InMemoryStorage()
        val vm = PlannerViewModel(storage)
        val start = vm.uiState.value.project
        val problems = mutableListOf<String>()
        repeat(steps) { i ->
            val what =
                try {
                    run.step(vm)
                } catch (e: Throwable) {
                    problems += "step $i crashed: $e"
                    return problems.map { "seed $seed: $it\n    steps: ${run.log.takeLast(8)}" }
                }
            run.log += what
            saneStateProblems(vm.uiState.value).forEach { problems += "after step $i ($what): $it" }
            if (problems.isNotEmpty()) return problems.map { "seed $seed: $it\n    steps: ${run.log.takeLast(8)}" }
        }

        // Redo to the newest state first (the run may end right after an undo), then undo all the way back
        // (the run stays under the 100-step history) and redo to the end again.
        var guard = 0
        while (vm.uiState.value.canRedo && guard++ < 500) vm.redo()
        val end = vm.uiState.value.project
        guard = 0
        while (vm.uiState.value.canUndo && guard++ < 500) vm.undo()
        if (vm.uiState.value.project
                .withoutZoom() != start.withoutZoom()
        ) {
            problems += "undo all did not return to the start"
        }
        guard = 0
        while (vm.uiState.value.canRedo && guard++ < 500) vm.redo()
        if (vm.uiState.value.project
                .withoutZoom() != end.withoutZoom()
        ) {
            problems += "redo all did not return to the end"
        }
        saneStateProblems(vm.uiState.value).forEach { problems += "after undo/redo: $it" }

        // Restart restores what was saved.
        vm.saveProject()
        val reloaded = PlannerViewModel(storage).uiState.value.project
        val saved = vm.uiState.value.project
        if (reloaded.withoutZoom() != saved.withoutZoom()) {
            val moved =
                saved.assets.zip(reloaded.assets).filter { (a, b) -> a != b }.take(2).map { (a, b) ->
                    "${a.presetId} rot ${a.rotationDeg} ${a.position} -> ${b.position} on ${saved.parcel}"
                }
            problems += "restart changed the project: $moved"
        }
        return problems.map { "seed $seed: $it\n    steps: ${run.log.takeLast(8)}" }
    }

    @Test
    fun randomUserFlows_keepTheStateSane() {
        val failures = (1..SEEDS).flatMap { runOnce(it, STEPS) }
        assertTrue(failures.isEmpty(), "${failures.size} problems:\n" + failures.take(60).joinToString("\n"))
    }

    private companion object {
        const val SEEDS = 60
        const val STEPS = 45 // under the 100-step undo history, so undo-all must reach the start
    }
}

private const val MONKEY_MM = 0.001f

/** What must hold after every user action, whatever the user did. Shared by the ViewModel and UI monkeys. */
internal fun saneStateProblems(s: PlannerUiState): List<String> {
    val out = mutableListOf<String>()
    val ids = s.project.assets.map { it.id }
    if (ids.size != ids.toSet().size) out += "duplicate asset ids"
    s.project.assets.forEach { a ->
        val ropesFinite =
            a.ropes.all {
                it.lengthMeters.isFinite() && it.outwardAngleDeg.isFinite() && it.localX.isFinite() &&
                    it.localY.isFinite()
            }
        if (!a.position.x.isFinite() || !a.position.y.isFinite() || !a.rotationDeg.isFinite() || !ropesFinite) out += "non-finite ${a.id}"
        if (s.presets.none { it.id == a.presetId }) out += "asset ${a.id} has unknown type ${a.presetId}"
    }
    if (!s.selectedAssetIds.all { it in ids }) out += "selection ${s.selectedAssetIds - ids.toSet()} not in the project"
    val fresh = GeometryEngine.evaluate(s.project, s.presets)
    if (fresh.issues != s.warnings) out += "stale warnings: shown ${s.warnings.size}, actual ${fresh.issues.size}"
    if (fresh.hardCollisions != s.hardCollisionAssetIds) out += "stale hard collision marks"
    if (s.project.parcel.widthMeters <= 0f || s.project.parcel.heightMeters <= 0f) out += "parcel ${s.project.parcel}"
    // The app keeps every body inside the parcel (placing, dragging, resizing and loading clamp); 1 mm for float noise.
    val byId = s.presets.associateBy { it.id }
    s.project.assets.forEach { a ->
        val preset = byId[a.presetId] ?: return@forEach
        val b = GeometryEngine.assetBodyAabb(a, preset)
        val p = s.project.parcel
        val fits = b.width <= p.widthMeters + MONKEY_MM && b.height <= p.heightMeters + MONKEY_MM
        if (fits &&
            (b.left < -MONKEY_MM || b.top < -MONKEY_MM || b.right > p.widthMeters + MONKEY_MM || b.bottom > p.heightMeters + MONKEY_MM)
        ) {
            out += "${a.presetId} sticks out of the parcel: $b on $p"
        }
    }
    if (!s.project.pixelsPerMeter.isFinite() || s.project.pixelsPerMeter <= 0f) out += "zoom ${s.project.pixelsPerMeter}"
    return out
}
