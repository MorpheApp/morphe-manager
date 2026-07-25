# How patching works and how to tune it

Patching rewrites the app's bytecode and resources on your device. Nothing is uploaded, and
nothing is downloaded except the patches themselves. This guide explains what happens during
a run and which settings change how it behaves, which is what you reach for when patching is
slow or fails on a large app.

## What happens during a run

A patch run moves through three groups of steps, visible as text on the progress screen and
in full detail in the Expert mode log:

1. **Preparing** - Morphe loads the patches, merges the modules if your input was a split
   APK bundle, and reads the APK.
2. **Patching** - each selected patch is applied in turn. This is the long part, and the
   counter shows how far along it is.
3. **Saving** - the patched APK is written and then signed with your keystore, see
   [Backing up Morphe and your keystore](backup-and-keystore.md).

While this runs, Morphe keeps a **Patching in progress** notification so you can leave the
app and come back, and a notification arrives the moment it finishes. **Completion sound** in
**Settings → System** adds a distinct tone for success and failure.

The red button stops the run, with a confirmation. Nothing is installed or changed on your
device until you press Install afterwards.

## Keeping the run alive

Some devices stop background work aggressively. Before the first patch Morphe asks to be
excluded from battery optimization, see
[Allow background patching](patching-simple-mode.md#allow-background-patching). If you
declined and runs keep dying when you switch away, grant it from **Settings → System** or
keep Morphe in the foreground.

Patching also needs room: at least **1 GB of free storage** for the input, the output, and
temporary files. Morphe warns you before starting if you are below that.

## Process runtime

**Settings → Advanced → Patcher → Process runtime** decides where the patcher runs.

<p align="center">
  <img src="images/patching-process/01-process-runtime.jpg" width="320" alt="Process runtime dialog with the memory slider" />
</p>

With **Enable process runtime** on, patching happens in a separate process with its own
memory budget instead of inside the Morphe app process. That is more stable, and it lets you
raise the memory ceiling beyond what the app process would get.

It is on by default on devices that support it. Android 10 and older cannot use it, and
Morphe disables it there.

### The memory limit

The slider sets how much memory the patcher process may use, in steps of 128 MB:

- The **minimum is 512 MB**, and the maximum is **1280 MB**.
- The upper end of the slider adapts to your device, roughly a quarter of its total RAM,
  so a phone with less memory offers a lower ceiling.
- Morphe picks a sensible starting value on first launch, no need to touch it unless
  something goes wrong.

**Higher limits speed up patching on capable devices.** Below 640 MB Morphe warns you that
larger apps may fail to patch. If a run dies with an out-of-memory error, raise the limit
first, and if it is already at the top, switch the bytecode mode to Fast.

## Bytecode processing mode

**Settings → Advanced → Patcher → Bytecode processing mode** controls how the app's code is
processed. It affects speed, memory usage, and the size of the output APK.

| Mode | Trade-off |
| --- | --- |
| **Fast** (default) | Faster patching and lower memory usage, at the cost of a larger APK. Recommended on low-RAM or older devices |
| **Full** | Slower, replicates the legacy behaviour. Worth trying only if Fast causes problems |

## Making the output smaller

**Optimize for device architecture** drops the native libraries and split modules your device
does not need, which shrinks the result considerably. It is an Expert mode setting, see
[Optimize for device architecture](patching-expert-mode.md#optimize-for-device-architecture).

## When patching fails

Morphe shows **Patching failed** with the error, and a copy button that puts it on your
clipboard.

For anything you want to report, the Expert mode log is the useful part: it opens with a
summary of app version, patches version, APK size, patch count, split APK or not, runtime and
heap limit, your Android version and device, and free RAM and storage. The copy button at the
bottom of the log takes the whole thing. **Settings → System → Debug** additionally exports
full system logs.

| Symptom | What to try |
| --- | --- |
| Out of memory, or the run dies near the end | Raise the memory limit, keep **Process runtime** on, and use the **Fast** bytecode mode |
| Patching stops when you leave the app | Allow Morphe to run without battery restrictions |
| "Low disk space" warning or a corrupt output | Free storage, patching wants at least 1 GB |
| Fails right after "Merging split APK" | Split bundles are less reliable than a full APK. Download a full APK if the site offers one |
| The patched app crashes on launch | The selection, not the run, is usually at fault. Patch again with **Enable recommended patches** |
| Every app fails after a source update | Update your patch sources, or restore an earlier selection, see [Managing patch sources](patch-sources.md) |

## Next steps

- [Patching an app in Expert mode](patching-expert-mode.md)
- [Updating a patched app](updating-patched-apps.md)
