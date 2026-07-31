# Updating a patched app

Patches keep improving, and the apps they patch keep changing. Morphe tells you when your
patched build has fallen behind by putting an **Update** badge on its home card.

Updating means patching again. The result installs straight over the app you already have,
without uninstalling and without losing its data.

## The Update badge

<p align="center">
  <img src="images/updating/01-update-badge.jpg" width="320" alt="Home screen with an Update badge on the patched app" />
</p>

The badge means **the patch source has a newer version than the one this app was patched
with**, and that this newer version actually contains changes for this app. Morphe reads the
source's changelog to check, so a patch release that only touches other apps does not put a
badge on yours.

> [!NOTE]
> The badge is about patches, not about the app itself. Morphe does not track new YouTube or
> Reddit releases, see [Moving to a newer app version](#moving-to-a-newer-app-version) below.

Morphe checks your sources in the background on a schedule you set in
**Settings → Advanced → Updates**:

- **Check frequency** - hourly, daily, weekly, or monthly.
- **Background update notifications** - notifies you about new releases even when Morphe is
  closed.
- **Mobile data updates** - allows those downloads over mobile data. With this off and no
  Wi-Fi, checks are skipped, and Morphe warns you if you patch with sources it could not
  refresh.
- **Pre-release updates** - early access to new Morphe versions. Pre-release for individual
  patch sources is toggled per source in **Sources**.

## Updating

Tap the app's card to open its info dialog. The **Patch update available** card at the top
explains what changed and offers the shortcut.

<p align="center">
  <img src="images/updating/02-app-info-update.jpg" width="320" alt="App info dialog with the patch update card" />
</p>

Tap **Patch** and the normal patching flow starts. Two things make it shorter than the first
time:

- If Morphe kept the original APK, it offers **Use saved APK** and you skip the download
  entirely.
- Your patch selection and patch options are remembered, so Expert mode opens with the same
  choices as last time.

> [!TIP]
> Whether those copies exist is up to **Keep original APKs** and **Keep patched APKs** in
> **Settings → System**. With the first one off, every update means downloading the APK
> again.

With several apps behind at once, patch them in one queue instead of one at a time, or let
Morphe do it on a schedule, see [Patching several apps at once](batch-patching.md).

When patching finishes, install as usual. Android accepts it as an update to the existing
app because Morphe signs every build with the same keystore, so your data, logins, and
settings inside the patched app survive.

> [!IMPORTANT]
> That only holds while the keystore stays the same. Reinstalling Morphe or clearing its data
> generates a new one, and then patched builds no longer match what is installed, Android
> reports a signature conflict and the only way forward is uninstalling the patched app.
> Export your keystore from **Settings → System → Keystore** and keep it somewhere safe, see
> [Backing up Morphe and your keystore](backup-and-keystore.md).

### Root mount installs

An app patched in root mount mode is updated the same way, but the result is mounted over
the stock app instead of installed. Since the data belongs to the stock app, nothing is at
risk from the signature side at all. See
[Root mount](installers.md#root-mount-in-detail).

## What else is in the info dialog

Below the update card, the dialog shows what the current build is made of:

| Row | Meaning |
| --- | --- |
| **Package name** | The package the patched app is installed under |
| **Original package name** | The package it was patched from |
| **APK size** | Size of the installed patched APK |
| **CPU architecture** | Architectures kept in the build |
| **Applied patches** | How many patches were applied, with a button that lists them |
| **Patch source used** | Which source and version produced this build |

The chips at the top show the installer that was used and how long ago the app was patched.
The buttons at the bottom are **Open**, **Export** (write the patched APK to storage),
**Reinstall** (install the saved patched APK again without patching), and **Uninstall**.

## Moving to a newer app version

Patches target specific app versions, so a newer YouTube release is only usable once the
patches declare support for it. When that happens, patch the new version the same way you
did the first time: pick the app, let Morphe tell you which version it now wants, download
that APK, and patch.

In Expert mode you can see every supported version at once in the APK selection dialog,
including **Experimental** ones, see
[Pick an APK version](patching-expert-mode.md#3-pick-an-apk-version).

## Updating Morphe itself

Morphe checks for its own updates on the same schedule. When one is available, a **Morphe
update available** card appears at the top of the home screen, and from there Morphe
downloads and installs the new version itself. This is separate from patch source updates
and does not affect your patched apps.

## Troubleshooting

| Problem | What to do |
| --- | --- |
| No **Update** badge although the source released a new version | The release contains no changes for this app, so there is nothing to repatch for |
| "Patches may be outdated" before patching | Mobile data updates are off and Morphe could not refresh its sources. Connect to Wi-Fi, or choose **Update & patch** |
| Signature conflict when installing the update | The keystore changed since the app was patched. Uninstall the patched app, which erases its data, then install the new build |
| The app was uninstalled outside Morphe | The card shows **App was uninstalled**. Patch it again to restore it |
| You want the previous build back | Install the APK you exported earlier. Saved copies are listed under **Settings → System → Patched APKs** |

## Next steps

- [Choosing how patched apps are installed](installers.md)
- [Patching an app in Expert mode](patching-expert-mode.md)
