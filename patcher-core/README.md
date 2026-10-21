# Patcher core

Everything about a patching run that does not need Android. The app runs it in one of two places:

- `JvmRuntime`, the default: an embedded HotSpot 25 inside the app's process, started by
  `PatcherJvm`, with a heap of a third of the RAM (1-3 GB, at most 1 GB in a 32-bit process).
  Shipped for arm64-v8a, armeabi-v7a and x86_64.
- `CoroutineRuntime`: inside the app, on ART. Used when the setting is off, on 32-bit x86, and
  when the JVM fails to start.

Both run `PatchPipeline`. The app talks to the JVM over pipes, one JSON line each: `HostCommand`
to preload bundles, start a run or cancel it, and `HostEvent` for what the run reports.

## Pitfalls

- **Heap pointer tagging**: HotSpot keeps flags in the top bits of pointers, and bionic aborts on
  free. `jvm_launcher.cpp` turns tagging off for the process as it starts the JVM.
- **Dumpable**: a release build starts the app as not dumpable, which makes `/proc/self/io`
  unreadable and leaves the I/O graph empty. The launcher marks the process dumpable again.
- **32-bit address space**: the heap is reserved in one piece, and the app's process has
  already spent much of its 4G. `PatcherJvm` sizes the heap to the largest block still free,
  less room for a code cache capped at 64M, and leaves the run to ART below 512M.
- **Linker namespace**: the app's namespace does not search the runtime's `lib/`, so
  `JvmInstallation.PRELOAD` loads the libraries that others link against by full path.
- **One JVM per process**: HotSpot cannot be stopped or started again. It starts with the first
  run the user sets up and serves every run after it. A JVM that fails to start leaves the
  runs of that process to ART.
- **No process of its own**: some vendors stop an app's extra processes regardless of their
  priority, so the JVM shares the process of the app and its foreground service.
- **Memory between runs**: the host collects in full after every run and when preloaded bundles
  are dropped, which is when G1 shrinks the heap back.
- **Cancel**: a run cannot be killed, so `HostCommand.Cancel` cancels its coroutines. It ends at
  the next suspension point, and the next run waits for it.
- **Exit**: `System.exit` in the JVM ends the app, as does a crash of the JVM. The crash report
  goes to `files/patcher-jvm-error.log` and into the log of the next run.

## The runtime in the APK

`buildSrc/PrepareEmbeddedJvmTask` turns the OpenJDK 25 Android build of
[AngelAuraMC](https://github.com/AngelAuraMC/angelauramc-openjdk-build) into `jvm/<abi>.zip.xz`.
The build is pinned by SHA-256 in `app/build.gradle.kts`.

- Runtimes and the host jar are zips of stored entries, compressed whole with xz, which takes
  them to less than half of what deflate does (`XzArchive.kt`).
- The image (`lib/modules`) is left out of the runtimes. The ABIs link the same classes but for
  a few generated per platform (`UnixConstants`, `Architecture`, ...), so `jvm/modules.xz` ships
  the image of the first ABI of `embeddedJvms`, and every ABI whose image differs gets
  `jvm/<abi>.modules.delta.xz` (`ImageDelta.kt`), about 90K in place of 7M. `JvmImageDelta`
  rebuilds the image on the device, byte for byte, checked by size and CRC-32.
- The host jar is shrunk with ProGuard (`ShrinkJarTask`, `host-rules.pro`), which keeps the
  patcher, smali and Kotlin whole, as the app's R8 rules do, and follows the rules the
  libraries ship in `META-INF/proguard`. Kotlin reflection is left out: neither the patcher
  nor the patches use more of it than the standard library has. A new library a patch calls
  into has to be kept in `host-rules.pro` as well as in the app's rules.
- The host jar leaves out the Android frameworks of ARSCLib (`frameworks/android/*.apk`), which
  the APK carries for patching inside the app. `JvmInstallation` copies them from the APK into
  the host jar as it unpacks it, and the run falls back to the app if there are none.
- Every build ships the server VM (`lib/server`), the 32-bit ARM one included.

- The build's own classes are relinked (`jimage extract`, `jmod`, `jlink`), not swapped for
  another JDK's, because the Android build patches some of them.
- `java.base` is packed last with `--hash-modules`, as the hashes it carries no longer match.
  The pattern avoids `*` and `?`, which the JDK launchers on Windows expand as file names.
- The JDK that links has to be the same feature release, 25.
- The `legal/` notices of every linked module go into the archive, as the GPL requires them to
  travel with the binaries. The license itself is listed in `app/aboutlibraries`, since
  AboutLibraries only finds Gradle dependencies on its own.

To change the JDK or add an ABI, edit `embeddedJvms`. `EMBEDDED_JVM_ID` changes with it, and
the app unpacks the runtime again.

A new event of a run has to be added to `PatchingListener`, `HostEvent`, `HostMain`,
`PatcherJvm.relay` and `ProgressReporter`.

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
- **Object headers**: the app passes `-XX:+UseCompactObjectHeaders` in a 64-bit process (a 32-bit
  JVM does not have the flag), which shrinks every object header from 12 to 8 bytes. Runs of a
  split YouTube bundle were about 4% faster with it, mostly in compiling the dex files and
  encoding the resources. `-XX:-UseCompactObjectHeaders` in `jvm-options.txt` turns it off.
- **CDS**: the runtime ships no archive. One can be made in three runs: one with
  `-XX:DumpLoadedClassList=<dir>/classes.lst`, one with `-Xshare:dump
  -XX:SharedClassListFile=<dir>/classes.lst -XX:SharedArchiveFile=<dir>/app.jsa` (it exits
  after the dump, which ends the app too), then runs with `-XX:SharedArchiveFile=<dir>/app.jsa`. The 47M archive of
  6300 classes made a run about 2% faster, less than runs differ by on their own.
- **CPU profile**: JFR needs `jdk.jfr` in `embeddedJvmModules` and `lib/jfr` in `embeddedJvmFiles`,
  then `-XX:StartFlightRecording=settings=profile` with
  `-XX:FlightRecorderOptions=repository=/data/user/0/app.morphe.manager.debug/files/jfr-repo`.
  The JVM exits only with the app, which ends without a dump at exit, so the chunk in the
  repository is never finished. `jfr` and Mission Control refuse it, but
  `jdk.jfr.consumer.EventStream.openRepository` reads what was flushed, which is all but the last
  second.
