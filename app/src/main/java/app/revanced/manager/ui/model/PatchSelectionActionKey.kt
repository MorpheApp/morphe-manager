package app.revanced.manager.ui.model

import androidx.annotation.StringRes
import app.universal.revanced.manager.R

enum class PatchSelectionActionKey(val storageId: String, @StringRes val labelRes: Int) {
    UNDO("undo", R.string.patch_selection_button_label_undo_action),
    REDO("redo", R.string.patch_selection_button_label_redo_action),
    SELECT_BUNDLE("select_bundle", R.string.patch_selection_button_label_select_bundle),
    SELECT_ALL("select_all", R.string.patch_selection_button_label_select_all),
    DESELECT_BUNDLE("deselect_bundle", R.string.patch_selection_button_label_bundle),
    DESELECT_ALL("deselect_all", R.string.patch_selection_button_label_all),
    BUNDLE_DEFAULTS("bundle_defaults", R.string.patch_selection_button_label_reset_bundle),
    ALL_DEFAULTS("all_defaults", R.string.patch_selection_button_label_defaults),
    SAVE_PROFILE("save_profile", R.string.patch_profile_save_label);

    companion object {
        val DefaultOrder: List<PatchSelectionActionKey> = values().toList()

        fun fromStorageId(id: String): PatchSelectionActionKey? =
            values().firstOrNull { it.storageId == id }

        fun ensureComplete(order: List<PatchSelectionActionKey>): List<PatchSelectionActionKey> {
            if (order.isEmpty()) return DefaultOrder
            val missing = values().filterNot(order::contains)
            return (order + missing).take(values().size)
        }
    }
}
