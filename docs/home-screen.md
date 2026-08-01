# The home screen and managing apps

The home screen is the app list Morphe opens on. Beyond tapping a card to patch, it lets you
reorder, group, hide, and act on several apps at once, so a long list stays yours.

## The app list

Each card shows an app, its patched version, and quick status: **Not patched yet**, an
**Update** badge when newer patches exist, or **App was uninstalled** when the patched app is
gone from the device.

Tapping a card starts patching if the app is not patched yet, and opens the app's info
dialog once it is, see [Updating a patched app](updating-patched-apps.md).

Two gestures work directly on a card:

- **Swipe right** - shows the patches available for that app.
- **Swipe left** - hides the card from the home screen.

Long-pressing a card enters multi-select.

At the bottom are **Sources**, see [Managing patch sources](patch-sources.md), and
**Settings**. Long lists get a floating button to jump back to the top.

## Searching and sorting

The search field filters the list by app name. **Sort apps** offers:

| Order | What it does |
| --- | --- |
| **Recommended order** | Installed and pinned apps first |
| **Name (A-Z)** and **(Z-A)** | Alphabetical, either direction |
| **Patch updates first** | Apps carrying an update badge on top, then recommended order |
| **Manual order** | Your own order, long-press an app and use the reorder button |

## Grouping

**Settings → Appearance → App grouping** adds a switcher above the list, with:

- **All apps** - one flat list.
- **Sources** - apps grouped by the patch source that provides them, handy when you run more
  than one source.
- **Custom** - your own categories.

Categories are created with **Add category**, renamed, reordered, and deleted from the same
switcher. Deleting one moves its apps back to **Uncategorized**, nothing is lost. To fill a
category, select apps and use **Move to category**.

Turning grouping off hides the switcher but remembers your selection.

## Hiding apps

Swiping a card left hides it, which is useful for apps you never patch. Morphe confirms the
first time. Hidden apps are not gone: a **Hidden apps** button at the bottom of the list
shows them, and tapping one brings it back.

## Multi-select

Long-press any card to select it, then tap others to add them. The bar that appears offers
**Select all** and **Deselect all**, **Move to category**, reordering, **Reset order**, and
the batch actions, including uninstalling every selected app at once.

The wand button patches everything you selected in one queue, see
[Patching several apps at once](batch-patching.md).

## Other apps

**Other apps** at the bottom of the list opens patching for apps outside the built-in list:
pick any APK from storage, or any app already installed on the device. This is an Expert mode
feature, in Simple mode Morphe tells you so.

Patching such an app only makes sense with universal patches, or with a source that declares
support for it.

## Personal touches

- **Greeting** - the question above the list changes with the time of day. It can be turned
  off in **Settings → Appearance**.
- **App icons and colors** on the cards come from the app itself or from the patch source's
  metadata.

## Next steps

- [Patching an app in Simple mode](patching-simple-mode.md)
- [Updating a patched app](updating-patched-apps.md)
- [Managing patch sources](patch-sources.md)
