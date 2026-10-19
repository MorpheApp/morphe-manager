# Patcher core

Everything about a patching run that does not need Android. The app runs it in one of two places:

- `JvmRuntime`, the default: an embedded HotSpot 21 in the `:patcher` process of
  `PatcherService`, with a heap of a third of the RAM (1-3 GB). Shipped for arm64-v8a and x86_64.
- `CoroutineRuntime`: inside the app, on ART. Used when the setting is off, on 32-bit devices,
  and when the JVM fails to start.

Both run `PatchPipeline`. The JVM reports back through `HostEvent`, one JSON line per event on a
pipe.

## Pitfalls

- **Heap pointer tagging**: HotSpot keeps flags in the top bits of pointers, and bionic aborts on
  free. `jvm_launcher.cpp` turns tagging off for the `:patcher` process.
- **Linker namespace**: the app's namespace does not search the runtime's `lib/`, so
  `JvmInstallation.PRELOAD` loads the libraries that others link against by full path.
- **`ManagerApplication.onCreate` runs in `:patcher` too**, and returns right away there.
  Otherwise, Firebase crashes the process.
- **One JVM per process**: HotSpot cannot be stopped or started again. The service kills its
  process on unbind, and every run gets a fresh one.
- **Priority**: only the binding with `BIND_IMPORTANT` keeps `:patcher` off the slow background
  cores.

## The runtime in the APK

`buildSrc/PrepareEmbeddedJvmTask` turns the OpenJDK 21 Android build of
[AngelAuraMC](https://github.com/AngelAuraMC/angelauramc-openjdk-build) into `jvm/<abi>.zip`
(about 18 MB). The build is pinned by SHA-256 in `app/build.gradle.kts`.

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
