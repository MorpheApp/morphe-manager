/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 *
 * Original hard forked code:
 * https://github.com/Jman-Github/Universal-ReVanced-Manager/blob/597b3173a004f5a9aae54326046dd7fd4c5b7777/app/src/main/aidl/app/revanced/manager/patcher/runtime/process/IPatcherEvents.aidl
 *
 * See the included NOTICE file for GPLv3 Section 7 terms that apply to Morphe contributions.
 */

package app.morphe.manager.patcher.runtime.process;

// Interface for sending events back to the main app process
oneway interface IPatcherEvents {
    void log(String level, String msg);
    void patchSucceeded(String patchName);
    // Sent right before the run fails on it, so the manager can name the patch to deselect
    void patchFailed(String patchName);
    void progress(String name, String state, String msg);
    // Resolved in the main process so the label uses the app locale, not the system locale
    void splitProgress(String eventType, String apkName);
    // The heap limit ART granted the process, in mebibytes. Sent once, before any patch loads
    void heapLimit(int megabytes);
    // The patching process has ended. The exceptionStackTrace is null if it finished successfully
    void finished(String exceptionStackTrace);
}
