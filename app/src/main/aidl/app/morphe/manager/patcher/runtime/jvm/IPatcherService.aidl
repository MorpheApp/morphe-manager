/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.patcher.runtime.jvm;

import app.morphe.manager.patcher.runtime.jvm.JvmLaunch;

interface IPatcherService {
    // Starts the JVM on the request written to requestFile and returns the pid of the process it
    // runs in. The run reports on events, which closes once the process is gone
    int start(in JvmLaunch launch, String requestFile, in ParcelFileDescriptor events);
}
