package de.voculupo.historicalcamplayout

import de.voculupo.historicalcamplayout.domain.model.defaultPresets as domainDefaultPresets

typealias TentShapeType = de.voculupo.historicalcamplayout.domain.model.TentShapeType
typealias PlannerToolMode = de.voculupo.historicalcamplayout.domain.model.PlannerToolMode
typealias Vec2 = de.voculupo.historicalcamplayout.domain.model.Vec2
typealias Parcel = de.voculupo.historicalcamplayout.domain.model.Parcel
typealias RopeAnchor = de.voculupo.historicalcamplayout.domain.model.RopeAnchor
typealias AssetRope = de.voculupo.historicalcamplayout.domain.model.AssetRope
typealias HeightRange = de.voculupo.historicalcamplayout.domain.model.HeightRange
typealias TentPreset = de.voculupo.historicalcamplayout.domain.model.TentPreset
typealias PlacedAsset = de.voculupo.historicalcamplayout.domain.model.PlacedAsset
typealias BlockZone = de.voculupo.historicalcamplayout.domain.model.BlockZone
typealias PlannerProject = de.voculupo.historicalcamplayout.domain.model.PlannerProject
typealias PlannerUiState = de.voculupo.historicalcamplayout.domain.model.PlannerUiState

fun defaultPresets(): List<TentPreset> = domainDefaultPresets()
