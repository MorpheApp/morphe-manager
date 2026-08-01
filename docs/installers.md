# Choosing how patched apps are installed

Morphe patches the APK, but something else has to put it on your device. That something is
the installer, and which one you pick decides whether installing takes a confirmation dialog
or happens silently, whether the original app survives, and what happens when a new version
of the app comes out.

The default works everywhere and needs no setup. Read on if you have root or Shizuku, or if
Google Play keeps trying to overwrite your patched build.

## Where the setting lives

Open **Settings → System → Installer → Default installer**. The **Select installer** dialog
lists everything available on your device, with the shared options underneath.

<p align="center">
  <img src="images/installers/01-select-installer.jpg" width="320" alt="Select installer dialog" />
</p>

Options your device cannot use are greyed out with the reason underneath, for example
**Requires root access**. Pick one and tap **Confirm**.

## The installers

### System installer

The Android package installer, and the default. Android shows its usual confirmation screen,
and the first time you install anything from Morphe it asks you to allow installs from
unknown sources.

This works on every device with no extra software. The trade-off is a confirmation tap for
every install, and if the unpatched app is still installed you have to uninstall it first,
because the patched build is signed with a different certificate.

### Root mount

Only selectable on rooted devices, and only for apps that were patched in root mount mode.
See [Root mount](#root-mount-in-detail) below, it works differently from every other option
here.

### Shizuku

Installs silently through [Shizuku](https://shizuku.rikka.app/) or Sui, with no confirmation
dialog at all. Shizuku has to be installed, running, and have granted Morphe permission,
Morphe tells you which of those is missing.

<p align="center">
  <img src="images/installers/02-shizuku-options.jpg" width="320" alt="Shizuku selected with its extra options" />
</p>

**Check Shizuku status** opens a breakdown of the current state: mode (Shizuku or Sui),
whether it is installed, supported, and running, and whether permission is granted, with
buttons to request permission or open the Shizuku app.

### Third-party installers

Any installer app detected on your system also shows up in the list. Morphe hands the APK
over and waits for that app to report the result.

## The shared options

Below the installer list are the settings that apply to whichever installer you selected.

### Install as Play Store

Records Google Play Store as the installation source for the patched app. This can help
services that check the install source, for example Android Auto, though it is not
guaranteed on every app, device, ROM, or Android version.

> [!WARNING]
> With Play Store recorded as the source, Play may offer updates for the patched app.
> Installing such an update overwrites your patched APK with the original and removes every
> patch. If you use this, open the app's Play Store page, tap the three-dot menu, and choose
> **Turn off updates**.

Morphe shows this warning and asks you to confirm before enabling the option.

### Auto-install after patching

Shizuku only. The patched APK is installed the moment patching finishes, without you
returning to the app.

### Auto-uninstall on conflict

Shizuku only, and off by default. When a silent install fails because the installed app has
a different signature, Morphe uninstalls the existing app first and then installs the
patched one.

> [!WARNING]
> This erases that app's data. If the patched APK then fails to install for another reason,
> the previous app is already gone.

### Installer selection prompt

Shows the installer dialog every time a patched app is installed, so you can choose per
install instead of committing to one default.

## Root mount in detail

Root mount does not install the patched app at all. The unpatched app stays installed, and
Morphe bind-mounts the patched APK over it. Android keeps seeing the stock app: same package,
same signature, same data directory.

That buys you two things ordinary installs cannot offer:

- **Updates without data loss.** Since the app's data belongs to the stock app, replacing the
  patched layer never touches it.
- **No GmsCore needed.** The patched app runs under the stock app's identity, so patches that
  normally require GmsCore work without it.

Morphe writes a Magisk-style module for each mounted app, so the mount is restored
automatically after a reboot.

### Choosing the mode

Root mount is decided **before** patching, not in the installer settings. On a rooted device
Morphe shows a **Choose patch mode** dialog when you start patching:

- **Root mount mode** - patches for mounting over the stock app.
- **Standard install mode** - patches as a normal APK with GmsCore support, and your
  installer setting is used afterwards.

> [!IMPORTANT]
> The patch mode is separate from **Settings → System → Installer** and cannot be changed
> after patching. To switch, patch the app again in the other mode.

In root mount mode the APK download step also changes: you **do** install the original APK,
because the stock app has to be present for the patched one to mount over it. In standard
mode you leave the downloaded APK uninstalled.

### Managing a mounted app

Open the app's info dialog from its card on the Morphe home screen. A mounted app is marked
with a **Mount** chip and offers **Remount** and **Unmount**, an unmounted one offers
**Mount**. Unmounting leaves you with the plain stock app, mounting brings the patches back.

### Limits and gotchas

- **Branding cannot be changed.** A custom app icon or display name does not apply to a
  mounted install, see [Creating a custom app icon](custom-app-icon.md).
- **Versions must match.** The installed stock app has to be the exact version the patched
  APK was built from. If they drift apart, Morphe refuses to mount and tells you which
  version to install.
- **KernelSU and APatch users**: disable module unmounting or hiding for apps installed with
  root mount. Otherwise the stock app may keep running even though Morphe reports it as
  mounted.
- The **Root mount** entry in the installer dialog only handles apps that were patched in
  root mount mode. Standard patched APKs still go through whichever other installer you
  selected.

## Which one should I use?

| Situation | Pick |
| --- | --- |
| No root, no Shizuku | **System installer** |
| You want installs without confirmation taps | **Shizuku** |
| Rooted, and you want to keep app data across updates | **Root mount** (chosen before patching) |
| A service refuses to work unless the app came from Play | **Install as Play Store**, and turn off Play updates for that app |
| You switch between methods often | Enable **Installer selection prompt** |

## Troubleshooting

| Problem | What to do |
| --- | --- |
| "Uninstall required" or a signature conflict | The unpatched app is still installed. Uninstall it, which erases its data, or use root mount instead |
| Shizuku option is greyed out | Shizuku or Sui is not installed, not running, or has not granted Morphe permission. **Check Shizuku status** shows which |
| "Your preferred installer is unavailable" | Fix the underlying issue and retry, or let Morphe fall back to the standard installer |
| Play Store replaced the patched app with the original | Turn off updates for that app on its Play Store page, then patch and install again |
| Mount fails with a version mismatch | Install the stock version that matches the patched APK, then mount again |
| Morphe says the app is mounted but the stock app still runs | On KernelSU or APatch, disable module unmounting or hiding for that app |

## Next steps

- [Patching an app in Simple mode](patching-simple-mode.md)
- [Patching an app in Expert mode](patching-expert-mode.md)
- [Updating a patched app](updating-patched-apps.md)
- [Creating a custom app icon](custom-app-icon.md)
