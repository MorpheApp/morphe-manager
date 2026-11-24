# Patcher core

Everything about a patching run that does not need Android. The app runs it in the `:patcher`
process of `PatcherService`, started by `PatcherProcesses` for every run, on one of two VMs:

- JVM, the default: an embedded HotSpot with a heap of a third of the RAM (2-3 GB, at most 40% of it below 5 GB; half of it
  within 1-1.5 GB in a 32-bit process), which starts at 512 MB. BellSoft's Liberica 25 for Linux on every
  ABI, which runs as a child process of `:patcher`.
- ART, the runtime of the process itself, within the heap Android gives the app. Used when the
  setting is off, on 32-bit x86, and when the JVM fails to start.

Both serve the run with `serveRun` in `HostMain`, which differs only in how it loads the patches.
The app talks to the host over pipes it hands the service, one JSON line each: `HostCommand` to
preload bundles and start the run, and `HostEvent` for what the run reports.

## Pitfalls

- **A JVM for the GNU C library**: the runtime is built for Linux, which bionic cannot load, so
  `PatcherService` starts it as a child process (`spawnProcess` in `jvm_launcher.cpp`) with
  glibc's loader: `libglibc_loader.so --library-path glibc:lib:lib/server bin/java ...`. An app
  targeting API 29 or later may not execute files of its data directory, so the loader ships as
  a native library (`PrepareGlibcTask`, `useLegacyPackaging`), and maps the launcher, the JVM and
  the rest of glibc from `no_backup/jvm`, which mapping still allows. The child keeps the pipes
  of the run under the same numbers, its output goes to logcat as `EmbeddedJvm`, and it is killed
  once the thread that started it ends (`PR_SET_PDEATHSIG`). That thread waits for it, reports an
  end the host could not, such as a kill by the system, and ends `:patcher` with the exit status
  of the JVM, so the process still ends with the run. `run-as` runs as `runas_app`, which may
  execute from the data directory, so it cannot show what the app itself may do.
- **Seccomp**: the child inherits the filter Android puts on an app, which ends the process with
  `SIGSYS` (exit status 159) on any system call bionic never makes. Every stock glibc makes
  `set_robust_list` on its start, newer ones `rseq`, `clone3`, `statx` and the calls of 64-bit
  time too, so the runtime takes glibc from termux-pacman, which builds it for Android and
  answers those as a kernel without them would. Its 32-bit ARM build makes `ftruncate` in place
  of `ftruncate64` though, which Android allows there alone, so the app takes its glibc from
  [MorpheApp/morphe-jvm](https://github.com/MorpheApp/morphe-jvm), termux-pacman's recipes with
  that changed, which also keeps the packages termux-pacman replaces. None of this shows from a shell,
  which has no filter: a blocked call is found with a preloaded `SIGSYS` handler that logs
  `si_syscall`.
- **`ManagerApplication.onCreate` runs in `:patcher` too**, and returns right away there.
  Otherwise, Firebase crashes the process.
- **One JVM per process**: HotSpot cannot be stopped or started again. The process ends with the
  run, and every run gets a fresh one, started ahead while the user picks what to patch.
- **A process of its own**: a large run, such as TikTok, can take more memory than a weak device
  spares. The system does not know of the JVM, a child of `:patcher`, so it frees memory by ending
  `:patcher` and, while on screen, the app first. The heap is kept to a share of the RAM for that,
  and a run that the end of the app cut short is reported once the app is back. On ART the heap of the
  run would otherwise be the app's, and running out of it would throw on the UI thread as well.
  A crash of the JVM or `System.exit` in a patch ends only that process too, and the JVM's report
  (`patcher-jvm-error-<pid>.log` in the app's temporary files) goes into the log of the run.
- **A full heap**: neither VM gives up on a heap that is nearly full right away, but collects
  for a few minutes first (the JVM until a full collection frees nothing an allocation can use,
  ART once a collection frees under 1%). The run then fails with `HeapExhaustedException`. On
  the JVM the host does not wait for that: once collecting took over 60% of the last two
  minutes, `watchForGcOverhead` sends a verdict encoded at its start and halts the process, as
  the heap has no room left for anything else by then.
- **The patch at fault**: the patcher reports a patch only once it is done, so the host also
  sends `PatchExecuting` right before each one runs. The app (`RunningPatchWatch`) logs a patch
  that runs 30 seconds, again each time it runs twice as long, and names the running patch when
  the heap runs out or the process ends. It times this in the app, whose heap is not the full one.
- **A run that hangs**: once a run has not moved on for five minutes, the host logs the stacks of
  all its threads and the heap in use (`watchForStalls` in `HostMain`), up to five times. The
  log also has the VM the host runs on (`Host VM:`), the process and the JVM options that differ
  between devices and builds (`Patcher process:`, `Patcher JVM:`), and how long the patches took
  to load, ahead of the run or in it. All of it is in the log the user copies.
- **Cancel**: the app ends the process, which is also what it does with one started ahead that
  no run took.
- **Priority**: only the binding with `BIND_IMPORTANT` keeps `:patcher` off the slow background
  cores.
- **Stdio**: the host replaces `System.out` and `System.err`, so what patches print goes into the
  log of the run as `[STDIO]` warnings, and the output of the JVM itself to logcat.

## The runtime in the APK

`buildSrc/PrepareEmbeddedJvmTask` turns [Liberica](https://github.com/bell-sw/Liberica) for Linux
into `jvm/<abi>.zip.xz` for every ABI of `embeddedJvms`, with the GNU C library of
[termux-pacman](https://github.com/termux-pacman/glibc-packages) that `PrepareGlibcTask` takes from
its packages. Each is pinned by SHA-256 in `app/build.gradle.kts`. termux-pacman keeps only the
latest build of a package, so the build fails on its URL once it moves on.

- A 64-bit process runs 25 with compact object headers, which leave the largest runs room for
  about 8% more than 17 did.
- Liberica rather than an Android build of OpenJDK: on 32-bit ARM every Android build of 25
  (AngelAuraMC's, Termux's 25.0.5 and 25.0.5.1) never gets past loading the patches, as one
  thread loops in compiled code and the VM never reaches a safepoint again. C1 alone does it as
  well, and `-Xint` does not but takes minutes for what 17 did in seconds. Liberica, the same 25
  on the same phones, does not loop, so it is the Android port rather than the JIT. With C2 it
  patched TikTok with Hushfeed on a Redmi 9 in 187 s where the client VM of 17 took 240 s, and
  compiled the dex files in 58 s where it took 69 s.
- The host jar is built for 17 and shrunk against it, so it runs on 25.

- Runtimes and the host jar are zips of stored entries, compressed whole with xz, which takes
  them to less than half of what deflate does (`XzArchive.kt`).
- The image (`lib/modules`) is left out of the runtimes. The ABIs link the same classes but for
  a few generated per platform (`UnixConstants`, `Architecture`, ...), so `jvm/modules.xz` ships
  the image of the first ABI of `embeddedJvms`, and every ABI whose image differs gets
  `jvm/<abi>.modules.delta.xz` (`ImageDelta.kt`), about 90K in place of 7M. `JvmImageDelta`
  rebuilds the image on the device, byte for byte,
  checked by size and CRC-32.
- The host jar is shrunk with ProGuard (`ShrinkJarTask`, `host-rules.pro`), which keeps the
  patcher, smali and Kotlin whole, as the app's R8 rules do, and follows the rules the
  libraries ship in `META-INF/proguard`. Kotlin reflection is left out: neither the patcher
  nor the patches use more of it than the standard library has. A new library a patch calls
  into has to be kept in `host-rules.pro` as well as in the app's rules.
- The host jar leaves out the Android frameworks of ARSCLib (`frameworks/android/*.apk`), which
  the APK carries for patching inside the app. `JvmInstallation` copies them from the APK into
  the host jar as it unpacks it, and the run falls back to the app if there are none.
- Every runtime ships the server VM (`lib/server`) alone, and leaves out the client VM.

- The build's own classes are relinked (`jimage extract`, `jmod`, `jlink`), not swapped for
  another JDK's, as a build may carry changes of its own in them.
- `java.base` is packed last with `--hash-modules`, as the hashes it carries no longer match.
  The pattern avoids `*` and `?`, which the JDK launchers on Windows expand as file names.
- The JDK that links has to be the same feature release as the build, so 25 is a toolchain of
  the build.
- The `legal/` notices of every linked module go into the archive, as the GPL requires them to
  travel with the binaries. The license itself is listed in `app/licenses/licenses.json`,
  since Licensee only finds Gradle dependencies on its own.

To change the JDK, glibc or an ABI, edit `libericaVersion` or `embeddedJvms`. `EMBEDDED_JVM_ID`
changes with it, and the app unpacks the runtime again.

A new event of a run has to be added to `PatchingListener`, `HostEvent`, `HostMain`,
`PatcherProcesses.PatcherProcess.relay` and `ProgressReporter`.

## Tuning and profiling

A debug build adds the options in `files/jvm-options.txt` of the app to the JVM, one per line
(`#` starts a comment). They come last, so they override the app's own, such as `-Xmx`. The
patch log lists them, and an option the JVM does not know keeps it from starting.

```sh
adb shell run-as app.morphe.manager.debug sh -c "'cat > files/jvm-options.txt'" < jvm-options.txt
adb exec-out run-as app.morphe.manager.debug cat files/gc.log > gc.log
```

- **Output**: what the JVM prints to its console, such as `-XX:+PrintCompilation` or a bare
  `-Xlog:gc*`, goes to logcat with the tag `EmbeddedJvm`, a line each. A file with an absolute
  path keeps it apart, such as
  `-Xlog:gc*:file=/data/user/0/app.morphe.manager.debug/files/gc.log`.
- **GC**: `-Xlog:gc+init` shows the collector the VM chose. G1 (the default) paused for about 3%
  of a YouTube run. ParallelGC paused for 5-8% and SerialGC for more, as both fall back to full
  collections once the 450-650M kept alive fills the old space. A 32-bit process still gets
  SerialGC, the collector with the fewest threads and the least to go wrong on that port.
- **AOT cache**: the JVM keeps a JDK 25 AOT cache in `cache/jvm-aot`, named after the
  runtime and the host jar. The first run records with `-XX:AOTMode=record`. Once it ends, the app
  waits for its JVM to write the configuration as it exits, then makes the cache in a JVM of its
  own with `-XX:AOTMode=create`. Later runs start with `-XX:AOTCache`. Each failed attempt
  adds its reason to a `.aotfailed` file, and after three runs go on without a cache; delete it
  to try again. Clearing the app's cache (in the app info, or the patcher workspace in the app's
  storage settings) deletes the cache and the failures with it, and the next run records again.
  The runtime and the host jar stay in `no_backup/jvm`: the cache is only valid for the files it
  was made with, so unpacking them again would void it anyway.
- **JIT**: without an AOT cache, the app passes `-XX:TieredStopAtLevel=1` in a 64-bit process, so
  only C1 compiles. A 32-bit process keeps C2, whose long runs gain the most from it.
- **Object headers**: the app passes `-XX:+UseCompactObjectHeaders` in a 64-bit process (a
  32-bit JVM does not have the flag), which shrinks every object header from
  12 to 8 bytes. Runs of a split YouTube bundle were about 4% faster with it, mostly in compiling
  the dex files and encoding the resources. `-XX:-UseCompactObjectHeaders` in `jvm-options.txt`
  turns it off.
- **CDS**: the runtime ships no archive. One can be made in three runs: one with
  `-XX:DumpLoadedClassList=<dir>/classes.lst`, one with `-Xshare:dump
  -XX:SharedClassListFile=<dir>/classes.lst -XX:SharedArchiveFile=<dir>/app.jsa` (it exits
  after the dump), then runs with `-XX:SharedArchiveFile=<dir>/app.jsa`. The 47M archive of
  6300 classes made a run about 2% faster, less than runs differ by on their own.
- **CPU profile**: JFR needs `jdk.jfr` in `embeddedJvmModules` and `lib/jfr` in `embeddedJvmFiles`,
  then `-XX:StartFlightRecording=settings=profile` with
  `-XX:FlightRecorderOptions=repository=/data/user/0/app.morphe.manager.debug/files/jfr-repo`.
  The process ends as soon as the run reports its end, before a dump at exit gets written, so
  the chunk in the repository is never finished. `jfr` and Mission Control refuse it, but
  `jdk.jfr.consumer.EventStream.openRepository` reads what was flushed, which is all but the last
  second.
