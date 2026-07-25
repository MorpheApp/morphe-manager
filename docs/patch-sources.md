# Managing patch sources

A patch source is the bundle of patches Morphe applies to an app. Morphe ships with one
pre-installed source, **Morphe Patches**, and that is all most people ever need. You can add
more if you want patches from another maintainer, or a bundle you built yourself.

## The Patch sources sheet

Tap **Sources** at the bottom of the home screen. The sheet lists every source you have, with
the count in the subtitle and a **+** button to add another.

<p align="center">
  <img src="images/patch-sources/01-sources-sheet.jpg" width="320" alt="Patch sources sheet with the pre-installed source expanded" />
</p>

Each card shows when the source was last updated, and a **Pre-installed** badge on the one
that ships with Morphe.

### What is on a source card

- **Patches** - how many patches the source provides. **Details** lists them all.
- **Version** - the version currently installed. **Details** opens its changelog.
- **Open in browser** - opens the source's repository or metadata URL.

### The toggles

**Pre-release patches** gives this source early access to new experimental patch versions.
With it off you only receive stable releases. It appears on sources that publish
pre-releases at all.

> [!NOTE]
> This is per source. The **Pre-release updates** setting in **Settings → Advanced → Updates**
> is a different thing, it controls early access to new versions of the Morphe app itself.

**Experimental app versions** makes this source patch experimental app targets when it has
them, so Morphe recommends a newer app version whose patches are still being refined. It
only appears when the source actually declares experimental targets, and on remote sources
it requires **Pre-release patches** to be on first, which is why it can disappear when you
turn that off.

> [!TIP]
> Expect quirks with experimental targets. Leave this off if you want the version the
> patches were tested against, see
> [Pick an APK version](patching-expert-mode.md#3-pick-an-apk-version).

### The action buttons

The pill row at the bottom of a card holds the actions available for that source:

| Icon | Action | Shown for |
| --- | --- | --- |
| circular arrow | **Update** - fetch the newest version now | Remote sources |
| crossed circle / check | **Disable** or **Enable** - keep the source without using its patches | Any source, once you have more than one |
| pencil | **Rename** - give the source your own display name | Sources you added |
| trash | **Delete** - remove the source, with a confirmation | Sources you added |

The pre-installed source cannot be renamed or deleted.

## Adding a source

Tap **+** and pick where the bundle comes from.

### Remote

<p align="center">
  <img src="images/patch-sources/02-add-remote.jpg" width="320" alt="Add patch source dialog, Remote tab" />
</p>

Paste the address. Morphe accepts a GitHub or GitLab repository, or a direct link to a
bundle's JSON metadata:

```
github.com/owner/repo
gitlab.com/owner/repo
example.com/patches-bundle.json
```

The field validates as you type and tells you whether the URL is usable before **Add**
becomes available. A repository URL also lets Morphe keep the source updated automatically.

A source can also arrive as a deep link, which carries the repository in the address:

```
https://morphe.software/add-source?github=owner/repo
https://morphe.software/add-source?gitlab=owner/repo
```

An optional `&name=` sets the display name. Opening such a link hands the repository to
Morphe, which asks **Do you want to add this patch source?** before anything is added. Only
GitHub and GitLab repositories are accepted this way.

> [!WARNING]
> Only add sources you trust. A patch source decides what ends up inside your patched apps.

### Local

<p align="center">
  <img src="images/patch-sources/03-add-local.jpg" width="320" alt="Add patch source dialog, Local tab" />
</p>

Pick a `.mpp` patch bundle from storage. Local sources never update on their own, you replace
the file yourself when a new one comes out.

## Using more than one source

Extra sources change how patching behaves:

- **Simple mode** asks which source to use in a **Select patch source** dialog whenever more
  than one has patches for the app you tapped.
- **Expert mode** gives each source its own tab on the patch selection screen. The action
  buttons there apply to the tab you are viewing.

> [!WARNING]
> Enabling patches from several sources for the same app is possible but risky. They are
> built independently and can conflict, so Morphe asks you to confirm before patching such a
> selection. Sticking to one source per app is the safe default.

The **Update** badge on a home card is tied to the source the app was patched with, see
[Updating a patched app](updating-patched-apps.md).

## Keeping sources up to date

Remote sources refresh in the background on the schedule set in
**Settings → Advanced → Updates**, and the **Update** pill on a card forces a check right
away. If **Mobile data updates** is off and you are not on Wi-Fi, checks are skipped and
Morphe warns you before patching with what may be an outdated bundle.

Sorting is available from the sheet: **Manual order** (long-press a card to drag it),
**Last updated**, or **Enabled first**.

## Troubleshooting

| Problem | What to do |
| --- | --- |
| "This source has already been added" | The same URL is already in your list |
| "Invalid source URL" | The address must point to a GitHub or GitLab repository, or to a `.json` metadata file |
| **Blocked** badge on a source | The source is blocked and cannot be used. Remove it |
| **Metadata N/A** on a card | The remote metadata file is unavailable. Patching with the installed bundle still works, but this source will not auto-update |
| A patch shows an **Expert** badge | That patch is not part of the default selection. Enable Expert mode to include it manually |
| The file will not add | Local bundles must have the `.mpp` extension |

## Next steps

- [Patching an app in Expert mode](patching-expert-mode.md)
- [Updating a patched app](updating-patched-apps.md)
