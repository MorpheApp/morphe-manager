/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.ui.screen.home

import app.morphe.manager.ui.model.HomeAppItem
import app.morphe.manager.ui.screen.shared.ScrollTarget
import app.morphe.manager.ui.screen.shared.buildIndexedScrollTargets
import app.morphe.manager.ui.screen.shared.buildScrollTargets

internal fun buildFlatHomeScrollTargets(items: List<HomeAppItem>): List<ScrollTarget> =
    buildIndexedScrollTargets(items) { item -> item.scrollLabelSource() }

/** Counts the category header rows so the targets line up with the rendered list. */
internal fun buildGroupedHomeScrollTargets(groups: List<HomeCategoryGroup>): List<ScrollTarget> =
    buildScrollTargets { emit ->
        var listIndex = 0
        groups.forEach { group ->
            listIndex += 1 // Header row
            if (group.collapsed) return@forEach
            group.items.forEach { item ->
                emit(listIndex, item.scrollLabelSource())
                listIndex += 1
            }
        }
    }

private fun HomeAppItem.scrollLabelSource(): String = displayName.ifBlank { packageName }
