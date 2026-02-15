package app.morphe.manager.ui.model

import android.os.Parcelable
import androidx.annotation.StringRes
import app.morphe.manager.R
import kotlinx.parcelize.Parcelize

enum class StepCategory(@StringRes val displayName: Int) {
    PREPARING(R.string.patcher_step_group_preparing),
    PATCHING(R.string.patcher_step_group_patching),
    SAVING(R.string.patcher_step_group_saving)
}

enum class StepId {
    DOWNLOAD_APK,
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

enum class ProgressKey {
    DOWNLOAD
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
    val message: String? = null,
    val progressKey: ProgressKey? = null
) : Parcelable
