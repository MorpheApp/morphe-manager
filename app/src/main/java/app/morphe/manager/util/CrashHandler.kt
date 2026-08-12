package app.morphe.manager.util

import android.app.Application
import android.os.Build
import android.os.Process
import android.widget.Toast
import java.io.File
import app.morphe.manager.ui.activity.CrashReportActivity
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.atomic.AtomicBoolean

object CrashHandler {
    private val timestampFormat = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.ROOT)
    private val handlingCrash = AtomicBoolean(false)
    private const val CRASH_REPORT_PROCESS_SUFFIX = ":crashreport"
    private const val EXTRA_CRASH_FILE = "crash_file"

    fun install(app: Application) {
        if (isCrashReportProcess(app)) return

        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            // Never re-enter crash handling in the same process.
            if (!handlingCrash.compareAndSet(false, true)) {
                terminateProcess()
                return@setDefaultUncaughtExceptionHandler
            }

            try {
                reportCrash(app, thread, throwable)
            } catch (_: Throwable) {
                // best-effort
            }

            // Terminate directly after handoff to avoid any restart loops.
            terminateProcess()
        }
    }

    private fun reportCrash(app: Application, thread: Thread, throwable: Throwable) {
        val sw = StringWriter()
        throwable.printStackTrace(PrintWriter(sw))
        val trace = sw.toString()

        // You could send the crash report to your server here if you want for telemetry or something.

        val fileDir = File(app.filesDir, "crash_reports").apply { if (!exists()) mkdirs() }
        val name = "crash_${timestampFormat.format(Date())}.log"
        val file = File(fileDir, name)
        file.writeText(buildString {
            appendLine("Thread: ${thread.name} (id=${thread.id})")
            appendLine("Time: ${Date()}")
            appendLine()
            appendLine(trace)
        })

        try {
            val intent = android.content.Intent(app, CrashReportActivity::class.java).apply {
                putExtra(EXTRA_CRASH_FILE, file.absolutePath)
                addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                addFlags(android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }
            app.startActivity(intent)

            // Pause a moment to allow the crash report to be presented before exit
            Thread.sleep(500)
        } catch (_: Throwable) {
            try {
                Toast.makeText(app, "Morphe encountered an error and saved a crash report.", Toast.LENGTH_LONG).show()
            } catch (_: Throwable) {
            }
        }
    }

    private fun isCrashReportProcess(app: Application): Boolean {
        val processName = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            Application.getProcessName()
        } else {
            null
        }
        return processName?.endsWith(CRASH_REPORT_PROCESS_SUFFIX) == true
    }

    private fun terminateProcess() {
        try {
            Process.killProcess(Process.myPid())
        } catch (_: Throwable) {
        }
        try {
            System.exit(2)
        } catch (_: Throwable) {
        }
    }
}
