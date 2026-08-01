# Patching an app in Expert mode

Expert mode turns off Morphe's curated defaults and hands you the patch list. You decide
which of the 100+ patches are applied, configure their options, and watch the patcher work
with live logs and memory usage.

The overall flow is the same as [Simple mode](patching-simple-mode.md), with three
differences: you get every compatible app version to choose from, a patch selection screen
before patching, and an expanded patching screen.

> [!WARNING]
> Misconfiguring patches can produce an app that crashes on launch or misbehaves in subtle
> ways. If something breaks, the first thing to try is **Enable recommended patches**, which
> restores the same selection Simple mode would use.

## 1. Enable Expert mode

Expert mode is off by default. Open **Settings → Advanced**, scroll to **Expert settings**,
and turn on **Expert mode**. Morphe asks you to confirm.

<p align="center">
  <img src="images/patching-expert-mode/01-enable-expert-mode.jpg" width="320" alt="Expert mode toggle in Advanced settings" />
</p>

The switch changes how the whole patching flow behaves, so you can turn it back off at any
time and return to Simple mode.

> [!NOTE]
> Patch options move with the mode. In Simple mode they live in **Settings → Advanced**, in
> Expert mode they are configured per patch on the selection screen in step 4.

### Optimize for device architecture

Turning on Expert mode reveals a few extra settings in the same place. The one worth knowing
before you patch is **Optimize for device architecture**, which strips everything the build
does not need on your phone. It is off by default and applies to both kinds of input:

- **A split APK bundle** (APKM, APKS, XAPK) is merged into a single APK before patching.
  With the option on, Morphe skips the modules that do not apply to your device while
  merging: other CPU architectures, other locales, and other screen densities.
- **A plain APK** keeps its native libraries for every architecture. With the option on,
  Morphe removes them after patching and keeps only the one your device actually loads.

The result is a noticeably smaller APK, often by a third or more on apps that ship libraries
for every architecture.

> [!IMPORTANT]
> The output only runs on devices like yours. Do not share an optimized build, and patch
> again without the option if you want a portable APK, for example one to install on a
> second device with a different CPU.

## 2. Choose the app to patch

Identical to Simple mode: open the home screen and tap the app you want to patch.

<p align="center">
  <img src="images/common/choose-app.jpg" width="320" alt="Home screen with the list of patchable apps" />
</p>

## 3. Pick an APK version

In Expert mode the dialog lists every version the patches declare support for, not just the
recommended one.

<p align="center">
  <img src="images/patching-expert-mode/02-apk-availability.jpg" width="320" alt="Version list with Recommended and Experimental badges" />
</p>

- **Recommended** - the version the patches are built and tested against. Pick this unless
  you have a reason not to.
- **Experimental** - support is early and may be unstable or incomplete. Morphe asks for an
  extra confirmation before patching one of these.
- **No badge** - an older supported version.

Tap a version to select it, then choose how to provide the file:

- **Yes, help me find an APK** - opens the download instructions for the selected version,
  exactly as described in [steps 3 to 5 of the Simple mode guide](patching-simple-mode.md#3-follow-the-download-instructions).
- **No, I already have an APK** - opens the file picker directly.
- **Use saved APK** - reuses an original APK you already patched with, no download needed.
  This button only appears when Morphe has one stored.

## 4. Select patches

This is the heart of Expert mode. The counter in the header shows how many patches are
enabled out of the total, and the magnifier searches the list by name.

<p align="center">
  <img src="images/patching-expert-mode/03-patch-selection.jpg" width="320" alt="Expert mode patch selection screen" />
</p>

Tap a card to enable or disable that patch. Patches that carry configurable values show a
settings icon that opens their options, for example a custom app name, header logo, or
theme colors, see [Creating a custom app icon](custom-app-icon.md). Newly added patches are
highlighted with a **New** badge.

If patches for this app come from more than one source, each source gets its own tab and
the row of action buttons applies to the source you are currently viewing, see
[Managing patch sources](patch-sources.md).

### The action buttons

The row of pill buttons above the patch list acts on the source you are currently viewing.
Long press any of them to see its name.

| # | Icon | Button | What it does |
| --- | --- | --- | --- |
| 1 | double check, blue | **Enable all** | Turns on every patch in the source. Greyed out when everything is already on. Rarely a good idea, some patches conflict with each other |
| 2 | thumbs up, purple | **Enable recommended patches** | Restores the default selection, the same one Simple mode applies. Your safety net when a build turns out broken |
| 3 | clock with arrow | **Restore saved selection** | Reverts to the selection you previously saved for this app. Greyed out when there is none |
| 4 | two sheets | **Copy selection from another bundle** | Copies enabled patches and their options from a different patch source. Only patches that exist in both sources are copied |
| 5 | stacked lines, red | **Disable all** | Turns off every patch in the source. Greyed out when nothing is on |

Each button confirms with a short toast so you can see what changed.

When you are happy with the selection, tap **Proceed to patching** at the bottom. If you
enabled patches from more than one source, Morphe warns you first, mixing sources can cause
compatibility issues.

Before the very first patch Morphe also asks to be excluded from battery optimization, see
[Allow background patching](patching-simple-mode.md#allow-background-patching).

## 5. Watch the patching process

Expert mode replaces the simple progress ring with a detailed view: overall progress, the
patch counter, live memory usage against your configured heap limit, and the patcher log.

<p align="center">
  <img src="images/patching-expert-mode/05-patching-progress.jpg" width="320" alt="Expert patching screen with logs and memory usage" />
</p>

The log opens with a summary block worth checking if you ever report a problem: app version,
patches version, APK size, patch count, whether the input was a split APK, the runtime and
heap limit, your Android version and device, plus free RAM and storage.

Below it, individual steps stream in as they happen. The **Games** tab next to
**Patcher logs** has a small game to pass the time while patching runs.

At the bottom, the red button cancels patching and the copy button puts the entire log on
your clipboard.

## 6. Install the patched app

When the run finishes, the header shows **All done!** and the log ends with a
**Patching succeeded** block: output size, elapsed time, and average and peak memory usage.

<p align="center">
  <img src="images/patching-expert-mode/06-patching-finished-logs.jpg" width="320" alt="Finished patching with the success summary in the log" />
</p>

After a moment Morphe switches to the completion screen on its own.

<p align="center">
  <img src="images/common/patching-complete.jpg" width="320" alt="Patching complete screen with the Install button" />
</p>

Tap **Install** here and Android takes over. If it asks to allow installs from unknown
sources, follow the prompt and grant it.

> [!IMPORTANT]
> The patched app keeps the original package name and is signed with a different
> certificate. If the unpatched app is still installed, Morphe reports an **Uninstall
> required** conflict and you have to uninstall it first. Uninstalling permanently erases
> that app's data.

After the first patched app is installed, Morphe asks once about notification permission and
offers a quick feature tour, see
[Two questions after the first install](patching-simple-mode.md#8-two-questions-after-the-first-install).

### Exporting the patched APK

The bottom bar of the completion screen has three buttons in Expert mode: **Logs** returns
to the patcher log, **Home** goes back to the app list, and **Save** exports the finished
APK through the system file picker.

Export it before you close Morphe if you want to keep the file, the patched APK itself sits
in temporary storage that is cleared the next time the app starts.

## Troubleshooting

| Problem | What to do |
| --- | --- |
| The patched app crashes or misbehaves | Patch again with **Enable recommended patches**, then re-enable your extra patches one at a time |
| Patching fails partway through | Copy the log with the button at the bottom, it contains the failing step. Raising the memory limit in **Settings → Advanced → Process runtime** helps with out-of-memory failures |
| "Multiple patch sources selected" warning | You enabled patches from more than one source. Stick to a single source unless you know the combination works |
| A patch shows a red border | It has required options that are not filled in. Open its options and complete them |
| Experimental version behaves oddly | Expected, patches for that version are still being refined. Use the **Recommended** version for a stable result |

## Next steps

- [Patching an app in Simple mode](patching-simple-mode.md)
- [Choosing how patched apps are installed](installers.md), including silent installs and
  root mount
- [Updating a patched app](updating-patched-apps.md) when new patches arrive
- [The home screen and managing apps](home-screen.md), for sorting, grouping, and hiding
- [How patching works and how to tune it](patching-process.md), when a run is slow or fails
- [Managing patch sources](patch-sources.md), to add patches from other maintainers
- [Backing up Morphe and your keystore](backup-and-keystore.md) before you reinstall or
  switch devices
- [Storage and saved data](storage-and-saved-data.md), to see what Morphe keeps and free
  space
- [Creating a custom app icon](custom-app-icon.md) or
  [header logo](custom-header-logo.md) for the patched app
