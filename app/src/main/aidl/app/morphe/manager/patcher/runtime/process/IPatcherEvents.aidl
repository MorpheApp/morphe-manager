package app.morphe.manager.patcher.runtime.process;

// Interface for sending events back to the main app process
oneway interface IPatcherEvents {
    void log(String level, String msg);
    void patchSucceeded(String patchName);
    void progress(String name, String state, String msg);
    // Resolved in the main process so the label uses the app locale, not the system locale
    void splitProgress(String eventType, String apkName);
    // The patching process has ended. The exceptionStackTrace is null if it finished successfully
    void finished(String exceptionStackTrace);
}
