# Patcher core

Everything about a patching run that does not need Android. The app runs it in one of two places:

- `JvmRuntime`, the default: an embedded HotSpot 25 in the `:patcher` process of
  `PatcherService`, with a heap of a third of the RAM (1-3 GB, at most 1 GB in a 32-bit process).
  Shipped for arm64-v8a, armeabi-v7a and x86_64.
- `CoroutineRuntime`: inside the app, on ART. Used when the setting is off, on 32-bit x86, and
  when the JVM fails to start.

Both run `PatchPipeline`. The JVM reports back through `HostEvent`, one JSON line per event on a
pipe.

## Pitfalls

- **Heap pointer tagging**: HotSpot keeps flags in the top bits of pointers, and bionic aborts on
  free. `jvm_launcher.cpp` turns tagging off for the `:patcher` process.
- **Dumpable**: a release build starts `:patcher` as not dumpable, which makes `/proc/self/io`
  unreadable and leaves the I/O graph empty. The launcher marks the process dumpable again.
- **Linker namespace**: the app's namespace does not search the runtime's `lib/`, so
  `JvmInstallation.PRELOAD` loads the libraries that others link against by full path.
- **`ManagerApplication.onCreate` runs in `:patcher` too**, and returns right away there.
  Otherwise, Firebase crashes the process.
- **One JVM per process**: HotSpot cannot be stopped or started again. The service kills its
  process on unbind, and every run gets a fresh one.
- **Priority**: only the binding with `BIND_IMPORTANT` keeps `:patcher` off the slow background
  cores.

## The runtime in the APK

`buildSrc/PrepareEmbeddedJvmTask` turns the OpenJDK 25 Android build of
[AngelAuraMC](https://github.com/AngelAuraMC/angelauramc-openjdk-build) into `jvm/<abi>.zip.xz`.
The build is pinned by SHA-256 in `app/build.gradle.kts`.

- Runtimes and the host jar are zips of stored entries, compressed whole with xz, which takes
  them to less than half of what deflate does (`XzArchive.kt`).
- The 32-bit ARM build only ships the client VM (`lib/client`), the others the server VM.

- The build's own classes are relinked (`jimage extract`, `jmod`, `jlink`), not swapped for
  another JDK's, because the Android build patches some of them.
- `java.base` is packed last with `--hash-modules`, as the hashes it carries no longer match.
  The pattern avoids `*` and `?`, which the JDK launchers on Windows expand as file names.
- The JDK that links has to be the same feature release, 21.
- The `legal/` notices of every linked module go into the archive, as the GPL requires them to
  travel with the binaries. The license itself is listed in `app/aboutlibraries`, since
  AboutLibraries only finds Gradle dependencies on its own.

To change the JDK or add an ABI, edit `embeddedJvms`. `EMBEDDED_JVM_ID` changes with it, and
the app unpacks the runtime again.

A new event of a run has to be added to `PatchingListener`, `HostEvent`, `HostMain`,
`JvmRuntime.relay` and `ProgressReporter`.

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
  collections once the 450-650M kept alive fills the old space.
- **JIT**: the app passes `-XX:TieredStopAtLevel=1`, so only C1 compiles. YouTube runs started at
  the same temperature were about 7% faster with it, almost all of it in the patches.
  `-XX:TieredStopAtLevel=4` in `jvm-options.txt` turns C2 back on. Let the phone cool between
  runs, as back to back runs got up to 40% slower, more than any flag changes. Read the
  temperature from the `Current temperatures from HAL` part of `adb shell dumpsys thermalservice`,
  as the part above it is cached.
- **CDS**: the runtime ships no archive. One can be made in three runs: one with
  `-XX:DumpLoadedClassList=<dir>/classes.lst`, one with `-Xshare:dump
  -XX:SharedClassListFile=<dir>/classes.lst -XX:SharedArchiveFile=<dir>/app.jsa` (it exits
  after the dump), then runs with `-XX:SharedArchiveFile=<dir>/app.jsa`. The 47M archive of
  6300 classes made a run about 2% faster, less than runs differ by on their own.
- **CPU profile**: JFR needs `jdk.jfr` in `embeddedJvmModules` and `lib/jfr` in `embeddedJvmFiles`,
  then `-XX:StartFlightRecording=settings=profile` with
  `-XX:FlightRecorderOptions=repository=/data/user/0/app.morphe.manager.debug/files/jfr-repo`.
  The process is ended as soon as the run reports its end, before a dump at exit gets written, so
  the chunk in the repository is never finished. `jfr` and Mission Control refuse it, but
  `jdk.jfr.consumer.EventStream.openRepository` reads what was flushed, which is all but the last
  second.
