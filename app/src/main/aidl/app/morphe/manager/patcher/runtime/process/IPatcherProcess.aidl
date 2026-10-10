/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 *
 * Original hard forked code:
 * https://github.com/Jman-Github/Universal-ReVanced-Manager/blob/597b3173a004f5a9aae54326046dd7fd4c5b7777/app/src/main/aidl/app/revanced/manager/patcher/runtime/process/IPatcherProcess.aidl
 *
 * See the included NOTICE file for GPLv3 Section 7 terms that apply to Morphe contributions.
 */

// IPatcherProcess.aidl
package app.morphe.manager.patcher.runtime.process;

import app.morphe.manager.patcher.runtime.process.Parameters;
import app.morphe.manager.patcher.runtime.process.IPatcherEvents;

interface IPatcherProcess {
    // Returns BuildConfig.BUILD_ID, which is used to ensure the main app and runner process are running the same code.
    long buildId();
    // Makes the patcher process exit with code 0
    oneway void exit();
    // Starts patching.
    oneway void start(in Parameters parameters, IPatcherEvents events);
}