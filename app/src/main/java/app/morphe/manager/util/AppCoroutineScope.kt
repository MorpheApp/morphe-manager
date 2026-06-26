package app.morphe.manager.util

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/** Process-lifetime [CoroutineScope] for work that must outlive any ViewModel or Activity. */
class AppCoroutineScope :
    CoroutineScope by CoroutineScope(SupervisorJob() + Dispatchers.Default)
