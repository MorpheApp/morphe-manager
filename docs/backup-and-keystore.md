# Backing up Morphe and your keystore

Everything Morphe knows lives in its own app data: your settings, your patch sources, your
patch selections, and the keystore every patched APK is signed with. Uninstalling Morphe or
clearing its data takes all of it, and the keystore is the one piece you cannot recreate.

Back it up before you reinstall Morphe, move to another device, or reset your phone.

## Why the keystore matters

Morphe signs every APK it produces with a keystore it generated on first use. Android
compares signatures when installing, so:

- **Same keystore** - a repatched build installs straight over the previous one as an
  update, and the app keeps its data.
- **Different keystore** - Android refuses. The only way to install is to uninstall the
  patched app first, which erases its data.

Reinstalling Morphe or clearing its storage generates a new keystore. Without an exported
copy, every app you patched before becomes un-updatable in place.

> [!TIP]
> Apps installed through [root mount](installers.md#root-mount-in-detail) are unaffected,
> because they are mounted over the stock app rather than installed.

## Exporting the keystore

Open **Settings → System → Import & export → Keystore** and tap the export action. Morphe
writes a `Morphe.keystore` file wherever you choose.

> [!WARNING]
> Treat this file as private. Anyone holding it can sign APKs that your device will accept as
> updates to your patched apps.

## Importing the keystore

Tap the import action and pick the file. Morphe detects the format and first tries the alias
and password combinations it knows, so a keystore it exported itself usually imports without
a prompt.

If that fails, the **Enter keystore credentials** dialog asks for:

- **Username (Alias)**
- **Password**
- **Keystore format**

Keystores Morphe generated use `Morphe` as both alias and password. A mismatch is reported
as **Incorrect keystore credentials**.

## Backing up your settings

**Settings → System → Import & export → Morphe settings** exports a
`morphe_manager_settings.json` holding your Morphe configuration: appearance, expert mode,
update behaviour, installer preferences, patcher settings, and the custom patch sources you
added.

On import, Morphe asks how to apply it:

| Mode | Effect |
| --- | --- |
| **Replace existing** | Match the backup exactly. Anything missing from the backup is removed |
| **Merge with existing** | Add what the backup has, leave your current items unchanged |

> [!NOTE]
> A GitHub personal access token is only included if you enabled **Include in settings
> export** next to it. If you did, keep the exported file private, it contains the token.

## Backing up patch selections

Saved patch selections and patch options are separate from settings and survive uninstalling
a patched app. **Settings → System → Patch selections** lists them per app and per source,
with its own export and import, and the same **Replace** or **Merge** choice, see
[Storage and saved data](storage-and-saved-data.md#patch-selections).

This is what makes a repatch reproduce the build you had, so include it if you care about
your Expert mode choices.

## Saved APKs

**Settings → System** also keeps the APK copies, controlled by two switches:

- **Keep original APKs** - the pre-patch file, so repatching does not need a new download.
  One version per app is kept.
- **Keep patched APKs** - a copy of the result, so it can be exported or reinstalled without
  patching again.

Both lists let you export the files, individually or as a zip, and delete what you no longer
need, see [Storage and saved data](storage-and-saved-data.md).

## A complete backup

1. **Keystore** - the one thing that cannot be regenerated.
2. **Morphe settings** - configuration and your patch sources.
3. **Patch selections** - your per app patch choices and options.
4. Optionally, exported **patched or original APKs**.

## Restoring on a new device

1. Install Morphe.
2. Import the **keystore** first, before patching anything, so new builds match what your
   old device installed.
3. Import **Morphe settings**. Your custom patch sources come back and start updating.
4. Import **patch selections**.
5. Patch as usual, see [Updating a patched app](updating-patched-apps.md).

## Troubleshooting

| Problem | What to do |
| --- | --- |
| "Incorrect keystore credentials" | The alias, password, or format does not match. Morphe's own keystores use `Morphe` for alias and password |
| "No keystore available to export" | Morphe has not generated one yet. Patch an app once, then export |
| Signature conflict after reinstalling Morphe | The keystore changed. Import your backup, then patch again. Without a backup, uninstall the patched app and start fresh |
| Imported settings removed sources you still wanted | **Replace existing** matches the backup exactly. Use **Merge with existing** to keep what you have |
| Patch selections did not come back | They are exported separately from settings, from **Settings → System → Patch selections** |

## Next steps

- [Updating a patched app](updating-patched-apps.md)
- [Managing patch sources](patch-sources.md)
- [Choosing how patched apps are installed](installers.md)
