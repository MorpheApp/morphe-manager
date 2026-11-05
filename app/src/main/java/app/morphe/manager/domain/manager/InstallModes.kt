/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.domain.manager

import androidx.annotation.StringRes
import app.morphe.manager.R

/** What a rooted device patches an app for, which cannot be changed once it is patched. */
enum class RootPatchMode(@param:StringRes val titleRes: Int, @param:StringRes val descriptionRes: Int) {
    STANDARD(R.string.root_pre_patch_installer_standard_title, R.string.root_pre_patch_installer_standard_description),
    MOUNT(R.string.root_pre_patch_installer_mount_title, R.string.root_pre_patch_installer_mount_description),
    ASK(R.string.settings_patch_mode_ask, R.string.settings_patch_mode_ask_description)
}

/** What happens to a patched app once patching is done. */
enum class AfterPatching(@param:StringRes val titleRes: Int, @param:StringRes val descriptionRes: Int) {
    INSTALL(R.string.settings_after_patching_install, R.string.settings_auto_install_with_shizuku_description),
    ASK_INSTALLER(R.string.settings_after_patching_ask, R.string.settings_after_patching_ask_description),
    MANUAL(R.string.settings_after_patching_manual, R.string.settings_after_patching_manual_description)
}
