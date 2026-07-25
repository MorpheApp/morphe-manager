# Patching an app in Simple mode

Simple mode is how Morphe works out of the box. You pick an app, Morphe tells you which
original APK it needs, helps you download it, and applies a curated set of patches with
sensible defaults. There is nothing to configure.

This guide walks through a first patch from start to finish, using YouTube as the example.
The steps are identical for YouTube Music and Reddit.

**Time needed:** about 5 to 10 minutes, most of it spent downloading the original APK.

## Before you start

- **Android 8.0 (API 26) or newer.**
- **At least 1 GB of free storage.** Morphe needs room for the original APK, the patched
  output, and temporary files. It warns you if free space is below this.
- **An internet connection.** Morphe downloads its patch sources on first launch, and you
  will download the original APK from the web.
- **Permission to install unknown apps.** Android asks for this the first time you install
  a patched APK. You can grant it when prompted.

> [!NOTE]
> Everything happens on your device. Morphe never uploads your APKs or personal data.

## 1. Choose the app to patch

Open Morphe. The home screen lists every app Morphe can patch. Tap the one you want, in
this case **YouTube**.

<p align="center">
  <img src="images/common/choose-app.jpg" width="320" alt="Home screen with the list of patchable apps" />
</p>

Apps you have not patched yet are marked **Not patched yet**.

> [!TIP]
> If more than one of your patch sources provides patches for the same app, Morphe first
> asks you to pick one in a **Select patch source** dialog. With the default setup this
> does not happen, see [Managing patch sources](patch-sources.md).

## 2. Check the required APK version

Morphe now shows which version of the original app the patches are built for, along with an
**Unpatched** badge. Patches are version specific, so this exact version is what you need.

<p align="center">
  <img src="images/patching-simple-mode/02-apk-availability.jpg" width="320" alt="Dialog showing the required unpatched APK version" />
</p>

- **Yes, help me find an APK** - Morphe walks you through downloading it. Choose this if
  this is your first patch.
- **No, I already have an APK** - skips straight to the file picker in step 5.

## 3. Follow the download instructions

Morphe explains what to do on the download site. Read the four steps, then tap
**Continue to APKMirror.com**. Morphe opens the correct download page for the required
version in your browser.

<p align="center">
  <img src="images/patching-simple-mode/03-download-instructions.jpg" width="320" alt="Instructions for downloading the original APK" />
</p>

> [!IMPORTANT]
> The red **DOWNLOAD APK** button on this screen is only a picture of what to look for on
> the website. Tapping it does nothing but show you a friendly reminder.

## 4. Download the original APK

On the website, scroll down to the download section and press the real **DOWNLOAD APK**
button.

<p align="center">
  <img src="images/patching-simple-mode/04-apkmirror-download.jpg" width="420" alt="The DOWNLOAD APK button on the website" />
</p>

Two things to keep in mind:

- **Do not install the APK** after it downloads. Morphe needs the untouched file.
- **Ignore any "a more recent upload may be available" notice.** Download exactly the
  version Morphe asked for in step 2, not a newer one.

When the download finishes, return to Morphe.

## 5. Select the downloaded APK

Morphe asks for the file you just downloaded. Tap **Open APK file** and pick it in the file
picker, usually from the **Downloads** folder.

<p align="center">
  <img src="images/patching-simple-mode/05-select-apk.jpg" width="320" alt="Prompt to select the downloaded APK file" />
</p>

Morphe verifies the file before continuing and warns you if the package, version, or
signing certificate does not match what the patches expect.

> [!TIP]
> If the file cannot be selected or opened, move it out of **Downloads** to the root of your
> internal storage and try again. You can also switch to
> [Morphe's built-in file picker](file-picker.md).

## 6. Wait for patching to finish

### Allow background patching

Before the very first patch, Morphe asks to be excluded from battery optimization. Some
devices stop the patcher when the app is minimized, and this prevents that.

<p align="center">
  <img src="images/common/background-protection.jpg" width="320" alt="Background patching protection dialog" />
</p>

**Allow** opens the Android system dialog. **Not now** skips it, in which case keep Morphe in
the foreground while patching. The prompt is shown once, in whichever mode you patch in.

### Patching

Patching starts automatically. The progress ring shows how many patches have been applied.

<p align="center">
  <img src="images/patching-simple-mode/06-patching-progress.jpg" width="320" alt="Patching progress screen" />
</p>

This usually takes a few minutes and depends on your device. You can leave Morphe in the
background, a notification arrives the moment patching finishes. The red button cancels
the process.

## 7. Install the patched app

When patching is done, Morphe switches to the completion screen on its own. Tap **Install**
there.

<p align="center">
  <img src="images/common/patching-complete.jpg" width="320" alt="Patching complete screen with the Install button" />
</p>

Android takes over from here. If it asks to allow installs from unknown sources, follow the
prompt and grant it, this is required for any app that does not come from the Play Store.

The buttons at the bottom of this screen are **Home**, which returns to the app list, and
**Save**, which exports the patched APK.

### Exporting the patched APK

**Save** opens the system file picker and writes the finished APK wherever you choose, so
you can keep it, move it to another device, or reinstall it later without patching again.
Export it before you close Morphe, the patched file itself sits in temporary storage that
is cleared the next time the app starts.

> [!IMPORTANT]
> The patched app keeps the original package name, and it is signed with a different
> certificate. If the unpatched app is still installed, Morphe reports an **Uninstall
> required** conflict and you have to uninstall it before the patched build can be
> installed. Uninstalling permanently erases that app's data, including downloads and
> local settings.

## 8. Two questions after the first install

Once the patched app is installed for the first time, Morphe asks two one-off questions.

### Notifications

<p align="center">
  <img src="images/common/notification-permission.jpg" width="320" alt="Notification permission prompt" />
</p>

**Allow** lets Morphe tell you when a new version of Morphe or of your patch sources is
available, even while the app is closed. **Cancel** declines, and update checks then only
happen while you have Morphe open. You can change this later in the Android settings for
Morphe.

### Quick feature tour

<p align="center">
  <img src="images/common/feature-tour.jpg" width="320" alt="Quick feature tour prompt" />
</p>

**Show me around** starts a short guided tour that points out the home screen, the app
sheet, and the settings. **Skip** dismisses it.

Neither prompt appears again. If you skipped the tour and want it later, start it from
**Feature tour** in the About section of **Settings → System**.

## After your first patch

- The home card for the app now shows the patched version and offers quick actions.
- Patch options such as a custom app name, header logo, or theme colors are available in
  **Settings → Advanced** while in Simple mode, see
  [Creating a custom app icon](custom-app-icon.md).

## Troubleshooting

| Problem | What to do |
| --- | --- |
| The APK cannot be selected from Downloads | Move it to the root of your internal storage, or enable the [built-in file picker](file-picker.md) |
| "Unsupported version" warning | You downloaded a different version than the one Morphe asked for. Download the exact version shown in step 2 |
| "Split APK detected" warning | You downloaded a bundle (APKM / APKS / XAPK). A full APK gives the best results, pick another download on the website |
| "Unverified APK" warning | The file does not match the expected signing certificate. Download it again from a trusted source |
| Patching fails or the app crashes | Update your patch sources and try again. Export debug logs from **Settings → Advanced** when reporting the issue |

## Next steps

- [Patching an app in Expert mode](patching-expert-mode.md), for choosing individual patches
  and configuring their options
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
