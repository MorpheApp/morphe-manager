# Patcher core

Everything about a patching run that does not need Android. The app runs it in the `:patcher`
process of `PatcherService`, started by `PatcherProcesses` for every run, on one of two VMs:

- JVM, the default: an embedded HotSpot with a heap of a third of the RAM (1.5-3 GB, at most
  1 GB in a 32-bit process). Shipped for arm64-v8a and x86_64 as 25, and for armeabi-v7a as 17.
- ART, the runtime of the process itself, within the heap Android gives the app. Used when the
  setting is off, on 32-bit x86, and when the JVM fails to start.

Both serve the run with `serveRun` in `HostMain`, which differs only in how it loads the patches.
The app talks to the host over pipes it hands the service, one JSON line each: `HostCommand` to
preload bundles and start the run, and `HostEvent` for what the run reports.

## Pitfalls

- **Heap pointer tagging**: HotSpot keeps flags in the top bits of pointers, and bionic aborts on
  free. `jvm_launcher.cpp` turns tagging off for the `:patcher` process.
- **Dumpable**: a release build starts `:patcher` as not dumpable, which makes `/proc/self/io`
  unreadable and leaves the I/O graph empty. The launcher marks the process dumpable again.
- **32-bit address space**: the heap is reserved in one piece, out of the 4G the process has.
  `PatcherService` sizes the heap to the largest block still free, less room for a code cache
  capped at 64M, and leaves the run to ART below 512M.
- **Linker namespace**: the app's namespace does not search the runtime's `lib/`, so
  `JvmInstallation.PRELOAD` loads the libraries that others link against by full path.
- **`ManagerApplication.onCreate` runs in `:patcher` too**, and returns right away there.
  Otherwise, Firebase crashes the process.
- **One JVM per process**: HotSpot cannot be stopped or started again. The process ends with the
  run, and every run gets a fresh one, started ahead while the user picks what to patch.
- **A process of its own**: a large run, such as TikTok, can take more memory than a weak device
  spares, and the system then ends the process that holds it. Apart from the app, that is the
  run alone, and the app reports it with the reason the system recorded. On ART the heap of the
  run would otherwise be the app's, and running out of it would throw on the UI thread as well.
  A crash of the JVM or `System.exit` in a patch ends only that process too, and the JVM's report
  (`patcher-jvm-error-<pid>.log` in the app's temporary files) goes into the log of the run.
- **A full heap**: neither VM gives up on a heap that is nearly full right away, but collects
  for a few minutes first (the JVM until a full collection frees nothing an allocation can use,
  ART once a collection frees under 1%). The run then fails with `HeapExhaustedException`.
- **A run that hangs**: once a run has not moved on for a minute, the host logs the stacks of
  all its threads and the heap in use (`watchForStalls` in `HostMain`), up to five times. The
  log also has the VM the host runs on (`Host VM:`), the process and the JVM options that differ
  between devices and builds (`Patcher process:`, `Patcher JVM:`), and how long the patches took
  to load, ahead of the run or in it. All of it is in the log the user copies.
- **Cancel**: the app ends the process, which is also what it does with one started ahead that
  no run took.
- **Priority**: only the binding with `BIND_IMPORTANT` keeps `:patcher` off the slow background
  cores.
- **Stdio**: the stdout and stderr of an app's process lead to `/dev/null` or logcat, so the
  host replaces `System.out` and `System.err`. What patches print goes into the log of the run
  as `[STDIO]` warnings.

## The runtime in the APK

`buildSrc/PrepareEmbeddedJvmTask` turns the OpenJDK Android builds of
[AngelAuraMC](https://github.com/AngelAuraMC/angelauramc-openjdk-build) into `jvm/<abi>.zip.xz`.
Each is pinned by SHA-256 in `app/build.gradle.kts`.

- A 64-bit process runs 25, whose compact object headers leave the largest runs room for about
  8% more than 17 does (TikTok with Hushfeed fits in a heap of a third of 3.5 GB on 25 alone).
- A 32-bit ARM process runs 17. On 25 the run never gets past loading the patches: one thread
  loops in compiled code and the VM never reaches a safepoint again. C1 alone does it as well,
  `-Xint` does not but takes minutes for what 17 does in seconds, and the 25 of Termux loops the
  same way, so it is the JIT of 25 on that port rather than a build of it.
- The host jar is built for 17 and shrunk against it, so it runs on either.

- Runtimes and the host jar are zips of stored entries, compressed whole with xz, which takes
  them to less than half of what deflate does (`XzArchive.kt`).
- The image (`lib/modules`) is left out of the runtimes. The ABIs link the same classes but for
  a few generated per platform (`UnixConstants`, `Architecture`, ...), so `jvm/modules.xz` ships
  the image of the first ABI of `embeddedJvms`, and every ABI whose image differs gets
  `jvm/<abi>.modules.delta.xz` (`ImageDelta.kt`), about 90K in place of 7M. The image of 17 for
  armeabi-v7a shares nothing with that of 25, so its delta is about the size of the image. `JvmImageDelta`
  rebuilds the image on the device, byte for byte, checked by size and CRC-32.
- The host jar is shrunk with ProGuard (`ShrinkJarTask`, `host-rules.pro`), which keeps the
  patcher, smali and Kotlin whole, as the app's R8 rules do, and follows the rules the
  libraries ship in `META-INF/proguard`. Kotlin reflection is left out: neither the patcher
  nor the patches use more of it than the standard library has. A new library a patch calls
  into has to be kept in `host-rules.pro` as well as in the app's rules.
- The host jar leaves out the Android frameworks of ARSCLib (`frameworks/android/*.apk`), which
  the APK carries for patching inside the app. `JvmInstallation` copies them from the APK into
  the host jar as it unpacks it, and the run falls back to the app if there are none.
- Every build ships the server VM (`lib/server`) but the 32-bit ARM one, which ships the client
  VM (`lib/client`) with C1 alone.

- The build's own classes are relinked (`jimage extract`, `jmod`, `jlink`), not swapped for
  another JDK's, because the Android build patches some of them.
- `java.base` is packed last with `--hash-modules`, as the hashes it carries no longer match.
  The pattern avoids `*` and `?`, which the JDK launchers on Windows expand as file names.
- The JDK that links has to be the same feature release as the build, so both 17 and 25 are
  toolchains of the build.
- The `legal/` notices of every linked module go into the archive, as the GPL requires them to
  travel with the binaries. The license itself is listed in `app/aboutlibraries`, since
  AboutLibraries only finds Gradle dependencies on its own.

To change the JDK or add an ABI, edit `embeddedJvms`. `EMBEDDED_JVM_ID` changes with it, and
the app unpacks the runtime again.

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
  `-Xlog:gc*`, goes to logcat with the tag `EmbeddedJvm`, each piece of a line as a line of its
  own. For readable output give `-Xlog` a file with an absolute path, such as
  `-Xlog:gc*:file=/data/user/0/app.morphe.manager.debug/files/gc.log`.
- **GC**: `-Xlog:gc+init` shows the collector the VM chose. G1 (the default) paused for about 3%
  of a YouTube run. ParallelGC paused for 5-8% and SerialGC for more, as both fall back to full
  collections once the 450-650M kept alive fills the old space. A 32-bit process still gets
  SerialGC, the collector with the fewest threads and the least to go wrong on that port.
- **JIT**: the app passes `-XX:TieredStopAtLevel=1` in a 64-bit process (a 32-bit JVM only has C1
  and fails to start with the flag), so only C1 compiles. YouTube runs started at
  the same temperature were about 7% faster with it, almost all of it in the patches.
  `-XX:TieredStopAtLevel=4` in `jvm-options.txt` turns C2 back on. Let the phone cool between
  runs, as back to back runs got up to 40% slower, more than any flag changes. Read the
  temperature from the `Current temperatures from HAL` part of `adb shell dumpsys thermalservice`,
  as the part above it is cached.
- **Object headers**: the app passes `-XX:+UseCompactObjectHeaders` in a 64-bit process (17,
  which a 32-bit process runs, does not have the flag), which shrinks every object header from
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
