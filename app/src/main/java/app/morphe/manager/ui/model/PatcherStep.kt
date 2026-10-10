/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 *
 * Original hard forked code:
 * https://github.com/Jman-Github/Universal-ReVanced-Manager/blob/597b3173a004f5a9aae54326046dd7fd4c5b7777/app/src/main/java/app/revanced/manager/ui/model/PatcherStep.kt
 *
 * See the included NOTICE file for GPLv3 Section 7 terms that apply to Morphe contributions.
 */

package app.morphe.manager.ui.model

import android.os.Parcelable
import androidx.annotation.StringRes
import app.morphe.manager.R
import kotlinx.parcelize.Parcelize

enum class StepCategory(@param:StringRes val displayName: Int) {
    PREPARING(R.string.patcher_step_group_preparing),
    PATCHING(R.string.patching),
    SAVING(R.string.patcher_step_group_saving)
}

enum class StepId {
    LOAD_PATCHES,
    PREPARE_SPLIT_APK,
    READ_APK,
    EXECUTE_PATCHES,
    WRITE_PATCHED_APK,
    SIGN_PATCHED_APK
}

enum class State {
    WAITING, RUNNING, FAILED, COMPLETED
}

interface StepProgressProvider {
    val downloadProgress: Pair<Long, Long?>?
}

@Parcelize
data class Step(
    val id: StepId,
    val name: String,
    val category: StepCategory,
    /** [0, 1] Percentage of the total operation */
    val progressPercentage : Double,
    val state: State = State.WAITING,
    val message: String? = null
) : Parcelable
