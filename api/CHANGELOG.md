# v1.6.0 (TBD)


# Features

- Enhanced patcher log export with comprehensive information including timestamps, app metadata, split APK merging details, patching summary, and memory usage information
- Patch profiles now include a gear menu to set version overrides (or choose "All versions") per profile
- Added Korean manager string localization https://github.com/Jman-Github/Universal-ReVanced-Manager/pull/42
- Split APKs now save in Settings > Downloads as merged APks https://github.com/Jman-Github/Universal-ReVanced-Manager/issues/41
- Added a toggle in Settings > Downloads to disable automatically saving APKs fetched via downloader plugins
- Gave the GitHub PAT entry in Settings > Advanced the ability to be saved through the manager settings exports. This is a toggable feature and is not on by default
- Updated the "Uninstall" button to "Unmount" and the "Update" button to "Remount" for saved patched apps in the "Apps" tab for apps installed by the rooted mount installer
- Added ability for users with root to mount patched apps by changing your primary installer to "Rooted mount installer" https://github.com/Jman-Github/Universal-ReVanced-Manager/issues/40
- Added a button to the installation in progress dialog on the patcher screen allowing the user to "Leave anyway" and not wait for the installer to finish or timeout/fail
- Added a "External installer", "Rooted mount installer", "System installer" and "Shizuku" installation types to the app info page for saved patched apps in the "Apps" tab
- Added a confirmation dialog when tapping the back button during a install on the "App info" page for saved patched apps in the "Apps" tab


# Bug fixes

- Fixed patch profiles not saving the selected app version when the APK is provided by a downloader plugin
- Fixed metadata issues with saved patched apps that would sometimes occur
- Fixed issues with InstallerX Revived's silent installer and the manager not detecting a install and timing out instead (if the install made by InstallerX Revived fails, the manager cannot detect the failure. Either wait for the installer to timeout, or exit the patcher screen by pressing "Leave anyway" on the dialog) https://github.com/Jman-Github/Universal-ReVanced-Manager/issues/43
- Fixed the select from storage page not graying out non .apk, .apks, .apkm, or .xapk
- Changed the supported downloader plugins URLs to Brosssh's fork (which has released builds for all plugins)


# Docs

- Added the new unique features to the README.md that were added in this release
- Added a new contributor to the "Contributors" section


# v1.5.1 (2025-11-15)
**Minimal changes & bug fixes**


# Features

- GitHub pull request integration - add patch bundles directly from GitHub pull request artifacts using a PAT, plus release/catalog links in bundle info https://github.com/Jman-Github/Universal-ReVanced-Manager/pull/35
- Manager string localization (Chinese) - add Simplified Chinese strings and expose a user-selectable language toggle https://github.com/Jman-Github/Universal-ReVanced-Manager/pull/33
- Vietnamese localization (new app language option) https://github.com/Jman-Github/Universal-ReVanced-Manager/pull/38
- Revamped Settings > General theme presets: the System preset is now labeled "Follow system" (and is the default for new installs/resets), the Pure black option is simplified to "Pure black", every preset remains single-select so you can clear them to return to manual colors, Dynamic color is the only preset that blocks accent tweaks, and the preset description copy better explains how these toggles work https://github.com/Jman-Github/Universal-ReVanced-Manager/issues/29
- Renamed the dynamic theme to "Material You"
- The GitHub icon buttons on each bundles info and widget now opens a bottom sheet with buttons for the release page and the patch-list catalog section (if available)
- Network requests now retry and respect server Retry-After headers when hit with HTTP 429 errors to reduce failed downloads
- Added an automatic "Merge split APK" step between loading patches and reading the APK so .apks, .apkm and .xapk archives are merged and patched without extra tools.
- Patch selection action buttons now remain visible at all times (graying out when unavailable) and automatically collapse when you scroll or switch bundles
- New Advanced setting lets you choose whether the patch selection action panel should auto-collapse after toggling patches
- Added a option in settings under Settings > Advanced "Patch selection action buttons order" that lets you reorder the patch selection action buttons
- Hold tap on the uninstall button on the app info page for saved patched apps to get the option to update the said app (install over the existing one). The uninstall button still remains
- Add downloader help dialog explaining plugins and linking to supported list https://github.com/Jman-Github/Universal-ReVanced-Manager/pull/37
- Updated to Liso’s patcher v22 (backwards compatible with existing patch bundles too) https://github.com/Jman-Github/Universal-ReVanced-Manager/pull/39
- Moved the rearrange patch bundles button in the patch bundles tab to the top right, next to the settings gear
- Remove the old "patch not does exist" error handling system and replaced it with a simple warning dialog that tells the user the issue, before the patching process begins


# Bug fixes

- Correctly display pure black theme option - pure black toggle only shows when the app is in dark mode or following a dark system theme https://github.com/Jman-Github/Universal-ReVanced-Manager/pull/30
- Typo - wording fixes for Theme color pickers and universal patches safeguard description https://github.com/Jman-Github/Universal-ReVanced-Manager/pull/36
- Preserve applied patch counts in app details when bundles are unavailable so patched apps no longer show 0 patches applied https://github.com/Jman-Github/Universal-ReVanced-Manager/issues/31
- Handle corrupted or empty pre-installed/remote patch bundles gracefully instead of crashing bundle loading https://github.com/Jman-Github/Universal-ReVanced-Manager/issues/34
- Fixed the custom installer manager sometimes showing the android package installer twice
- Fixed occasional issues with importing patch bundles via remote
- Fixed pre-installed patch bundle sometimes ignoring the user's custom order when restoring large bundle imports
- Fixed patch profiles sub-options and values dialogs showing internal names instead of user-friendly names when the patch bundle used no longer exists in the app
- Patch selection screen buttons should now correctly align across different screen sizes
- Fixed the pre-installed patch bundle, resetting custom display names after restarting the app
- Patch profiles now record an app version even when saved before an APK is provided (e.g., downloader-based patch flows)
- Fixed the positioning and alignment of the patch selection menus action buttons on smaller screen sizes
- Fixed the "Auto-collapse completed patcher steps" setting under Settings > Advanced not being included in manager setting exports
- Fixed app sub option & value metadata not being reapplied/saved through the "Repatch" button on saved apps in the saved apps tab


# Docs

- Added the new unique features to the README.md that were added in this release
- Added a contributors section giving credit to those who have contributed to this repository


# v1.4.0 (2025-11-07)


# Features

- Added an export filename template for patched APKs with placeholders for app and patch metadata
- Added Shizuku as an installer option for silent installs when Shizuku/Sui is available https://github.com/Jman-Github/Universal-ReVanced-Manager/issues/17
- Official patch bundle can now be deleted from the patch bundles tab, and restored from Advanced settings https://github.com/Jman-Github/Universal-ReVanced-Manager/issues/18
- Primary and fallback installer menus now prevent selecting the same installer twice and grey out conflicting entries
- Advanced settings now support saving custom installer packages, including package-name lookup with autocomplete, and dedicated management for third-party installers https://github.com/Jman-Github/Universal-ReVanced-Manager/issues/17
- Installer workflow now times out on stalled installs and automatically surfaces the system error dialog
- New bundle recommendation picker lets you choose per-bundle suggested versions or override them with any other supported version
- "Select an app" screen now groups bundle suggestions behind a toggle with inline dialogs for viewing additional supported versions
- The built-in Official ReVanced patch bundle now shows a dedicated "Pre-installed" origin label when viewed or restored
- Added a hyerplink in Settings > About that links to the unique features section of the README.md
- Changed the "Universal ReVanced Manager" title text on the main three tabs to "URV Manager"
- Updated the app icon of the manager to a custom one
- Removed the "Open souce licenses" button & page in Settings > About


# Bug fixes

- Fixed patch option expandables in bundle patch lists collapsing or opening in sync when toggling multiple patches
- Fixed incorrect themeing of certain UI elements with the pure black theme toggled on https://github.com/Jman-Github/Universal-ReVanced-Manager/issues/15 https://github.com/Jman-Github/Universal-ReVanced-Manager/issues/20
- "Remove unused native libraries" setting should now actually remove all unnecessary/unused libraries completely when toggled on
- Fixed repatching through the "Apps" tab & using last applied patches & sub options on apps not saving https://github.com/Jman-Github/Universal-ReVanced-Manager/issues/19
- Saved apps in the "Apps" tab should now stay (and not delete themselves automatically) when the user unisntalls the app directly from that page
- Fixed issues with installing directly from the patcher page https://github.com/Jman-Github/Universal-ReVanced-Manager/issues/22


# Docs

- Updated the README.md to include the new unique features added in this release
- Added a section to the README.md which lists what downloader plugins that are currently supported by the manager


# v1.3.1 (2025-11-01)
**Minimal changes & bug fixes**


# Features

- Added a full installer management system with metadata, configurable primary/fallback choices that applies to patched apps, manager updates, etc. Configurable from Settings > Advanced (https://github.com/Jman-Github/Universal-ReVanced-Manager/issues/8)
- Updated the "Allow using universal patches" (now renamed to "Show & allow using universal patches") setting to also hide universal patches when toggled off and not just prevent the selection of them (https://github.com/Jman-Github/Universal-ReVanced-Manager/issues/9)
- Local patch bundle details show their bundle UID with a quick copy shortcut, imported & existing patch profiles automatically update their local patch bundle by using hashes, and the ability to manually edit the bundle UID for patch profiles that are using local patch bundles (https://github.com/Jman-Github/Universal-ReVanced-Manager/issues/11)
- Added the preinstalled, official ReVanced patch bundle user set display name to patch bundle exports
- Added the ability to edit/update existing patch profile names
- Prevent users from naming patch profiles the same as another per app (different apps patch profiles can only have the same names now)
- Remove obsolete add/plus button in the bottom right hand corner on the patch profiles tab
- Removed selection warning popup for toggling Universal Patches


# Bug fixes

- Made the patcher recover from out-of-memory exits caused by the user set memory limit with the experimental patcher process memory limit setting by automatically prompting the user to repatch, and lowering the memory limit (https://github.com/Jman-Github/Universal-ReVanced-Manager/issues/12)
- Cached bundle changelog responses so repeated requests fall back to a stored version instead of hitting GitHub rate limits (https://github.com/Jman-Github/Universal-ReVanced-Manager/issues/10)
- Fixed patch profiles duplicating instead of overlapping when imported multiple times
- Fixed delete confirmation menus not disappearing after confirming a deletion
- Fixed patch deselection shortcuts (deselect all & deselect all per bundle) not following patch selection safeguard settings
- Optimized patch bundles importing


# v1.3.0 (2025-10-26)


# Features

- Added the ability to uninstall downloader plugins from inside the manager via the downloads settings page
- Upstream with Official ReVanced Mananger
  - Add pure black theme
  - Correct grammer mistakes
  - Prevent back presses during installation
- Added an advanced option to strip unused native libraries (unsupported ABIs) from patched APKs during the patching process (https://github.com/Jman-Github/Universal-ReVanced-Manager/issues/7)
- Added support for the manager to store multiple downloaded apps (ones downloaded through the downloader plugins) in the downloads settings & the ability to export the app to your devices storage
- Added a "Downloaded apps" option on the select source screen for patching apps that allows the user to select a APK that the manager has cached from a downloader plugins downloads (this option will only appear if the said app is downloaded, otherwise you won't see it)
- Added the ability to update an existing patch profiles through the save profile menu on the patch selection page
- Exporting a patched app to storage from the patching screen will now automatically save the patched app under the "Apps" tab. This previously only occurred when the user installed the app directly from the patching screen
- Added an accent color picker in appearance settings so users can choose a custom theme color (in addition to Material You and pure black)
- Added a confirmation popup when tapping the back button on the patching screen after the app has been successfully patched confirming the user wants to leave the screen. It also includes a option to save the patched app for later (saves it to the "Apps" tab) on the popup
- Added the ability to see the applied patches of a patched APK in the "Apps" tab, and the patch bundle(s) used
- Added the "View changelog" button to the chanelog viewer in settings
- Added the ability to delete saved patched apps in the "Apps" tab (this will not uninstall them from your device)
- Removed redundant "View changelog" button at the top of the changelog screen popup


# Bug fixes

- A few grammatical errors
- Release workflow errors


# v1.2.1 (2025-10-23)
**Minimal changes & bug fixes**


# Features

- Added a changelog log section in remote/URL imported patch bundles information that shows the latest GitHub release changelog for said bundle
- Added a note on each patch bunlde on whether they where imported via remote, or local (remote is via URL and local is via a file on your device)
- Removed reduntant bundle counter on patches profile tab (there were two counters)


# Bug fixes

- (ci): incorrect version names on releases sometimes
- (ci): not uploading APK artifact to release
- Exporting patch bundles with locally imported patch bundles mixed with ones imported by a URL will now export (automatically excluding the locally imported ones from the export)


# v1.2.0 (2025-10-22)


# Features

- Added Patch Profiles; the ability to save individual patch selections per bundle(s) for a specific app to the new "Patch Profiles" tab
- Added a "Show actions" button that collapses/expands the action buttons in the patch selection menu
- Added the ability to export and import Patch Profiles to/from JSON files
- Added a copy patch bundle URL button in patch bundle options
- Added the ability to export and import the managers settings from/to a JSON file (this only includes settings, not patch bundles, patch options, patch selections, etc)
- Adjusted the placement of the patch selection menu action buttons to be go vertically instead of horizontally
- Upstrean with the Official ReVanced Manager `dev` branch


# Bug fixes

- UI being cut off in patch bundle selection menus for reseting patch selection & options


# v1.1.1 (2025-10-20)
**Minimal changes & bug fixes**


# Features

- App launcher name is now "URV Manager" so the full name is displayed on different ROMs (name isnide the app still remains the same)
- Selected patch counter shows count when scrolling in patch selection menu

# Bug fixes

- Incorrect keystore used on releases
- Incorrect patch count in patch selection menu


# v1.1.0 (2025-10-16)


# Features

- Added patch bundle exporting and importing support
- Added a deselect all per-bundle button in patch selection menu (the global deselect all button now has a different icon)
- Permentalty enabled "Developer Options" in setings (removed the hidden flow to unlock them)
- Added an toggle in settings for updating the manager and patch bundles on metered connections
- Re-added the manager changelog app functions, screens, and buttons
- Added labels to the global patch deselection, per-bundle patch deselection, and reset to default buttons in the patch selection screen
- Renamed parts of the app from "Patch" or "Patches" to "Patch Bundle" to help with termonalogy clarity


# v1.0.0 (2025-10-13)


# Features
**Initial release**

- Added patch bundle display naming
- Added support for all 3rd party patch bundles
- Added the ability to deselect all patches in selection menu
