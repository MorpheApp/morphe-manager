# Patching several apps at once

Patching one app at a time means sitting through every dialog again for the next one. A batch
run asks everything up front, then works through the whole list on its own: same patcher,
same result, one queue.

Morphe also keeps track of which patched apps have fallen behind their patches, and offers the
whole set as one queue.

## Starting a batch

**From the home screen** - long-press a card to enter multi-select, tap the other apps you
want, then tap the wand button in the bar at the bottom.

**From the launcher** - long-press the Morphe icon and pick **Re-patch outdated apps**. Morphe
works out which patched apps are behind their patch source and queues those. If everything is
current, it says so and stays out of your way.

## The preflight list

Nothing is patched yet. This list is where every question a normal patch would ask is
answered, so the run itself never has to stop and wait for you.

<p align="center">
  <img src="images/batch-patching/01-preflight-list.jpg" width="320" alt="Preflight list with two apps ready to patch" />
</p>

Each card shows where the APK comes from, the app version, how many patches will be applied,
and which source they come from. The badge says whether the app is ready:

| Badge | Meaning | What to do |
| --- | --- | --- |
| **Ready** | An APK and a patch selection were found | Nothing |
| **No APK** | No saved original, and the installed app cannot be used | **Select APK** and pick where it comes from |
| **Version** | This APK version is not covered by the patches | **Select APK** for a supported version, or **Patch anyway** |
| **No patches** | No enabled source has patches for this app | Enable a source, or exclude the app |
| **Excluded** | You removed it from this run | **Include** to bring it back |

Where does the APK come from? Morphe looks for a saved original first, then for the stock app
on the device, and only asks you for a file when neither exists. An installed app is skipped
when it looks like it was already patched, because patching a patched APK produces a broken
build.

That means an app you have never patched is queued at whatever version is installed. When the
sources mark that version **experimental**, the card is tagged with it next to the app name.
Nothing stops the run, because experimental versions do work, but it is the one caveat the
queue cannot ask you about mid-run.

**Select APK** on any card opens the same APK question the single-app flow asks, so the answer
is the same everywhere. From there you can pick a version, including an experimental one, and
choose where the file comes from: the saved original, the app installed on the device, a file
you already have, or a download. Downloading sends you to the page for the version you picked
and opens the file picker when you come back, so the file lands on the app it was fetched for.

**Install when finished** decides what happens after the last app. Off keeps the patched APKs
so you install them yourself; on hands the whole batch to the installer as soon as patching
ends.

### Deciding what gets applied

Each app starts from the same patches a normal patch would use: your saved selection if you
have patched it before, the recommended set otherwise. Patches added to a source since your
last run join in if they are recommended, exactly as they do elsewhere.

An app covered by more than one enabled source is patched with all of them. What you do about
that depends on the mode you are in:

- **Expert mode** puts a **Choose patches** button on each card. It opens the same patch list
  the single-app flow uses, with a tab per source, and **Save** writes your choice into the
  queue. A successful run then keeps it as the app's saved selection.
- **Simple mode** puts a **Select patch source** button on cards where more than one source
  applies, the same question simple mode asks before a single-app patch. Picking one drops the
  others for this app, and you can switch again until the queue starts.

> [!TIP]
> Turn on **Keep original APKs** in **Settings → System**. It is what lets a batch run without
> asking for a single file.

## While the queue runs

The run looks exactly like patching a single app, because it is the same screen: the step
list, the log panel and the memory graph in Expert mode, the animated progress in Simple mode.
Above it, a counter shows how far the queue is and which app is being patched.

Leaving the screen does not stop anything. Patching continues in the background, exactly like
a single run, and you can come back to it later.

**Cancel** stops the app being patched right now and drops everything still queued. Apps that
finished before that keep their results.

## The summary

When the queue drains, the list comes back with what happened:

> 2 patched, 0 failed, 1 skipped

<p align="center">
  <img src="images/batch-patching/02-summary.jpg" width="320" alt="Batch summary with two patched apps waiting to be installed" />
</p>

That line is about **patching**. Installing is a separate step with its own result, shown on
each card:

- **Installed** - the patched APK is now on the device.
- **Install failed** - with the reason underneath, in place of the app details.

Use **Install all** at the bottom, or the install button on a single card when you only want
one. An installed app drops out of both: its card swaps the install button for **Open**, and
**Install all** covers only what is left. After a long run that is how you tell at a glance
what still needs doing.

A failed install keeps its button, so you can fix the cause and try again without patching
anything a second time. An app that failed to *patch* gets a button for the full error, which
is longer than the card can show.

**Export** saves the patched APK wherever you choose, without installing it. It is on every
card that produced a file, before and after installing.

The refresh button next to the title re-plans the apps that failed or were canceled.

> [!NOTE]
> Patched APKs are kept only while **Keep patched APKs** is on in **Settings → System**. With
> it off they live long enough for you to install them from this screen and are dropped when
> the next batch starts.

## When patches move on

A patch source that releases something newer than what your apps were built with leaves those
apps behind. The cards say so one at a time with their **Update** badge; above them, Morphe
says it once: **3 apps can be re-patched**, and tapping that queues every one of them in a
preflight list.

This appears once the new patches are actually on the device, which is when Morphe can tell
which apps are behind. Opening the app after a **New patches available** notification is
exactly that moment: the notification pulls the sources in, and the count appears with them.

The run itself begins when you tap **Start patching**. Android does not let a background app
run work this heavy or install anything on its own, so re-patching stays a deliberate tap
rather than something that half-happens overnight.

## Starting a batch from another app

Automation tools can ask Morphe to open a queue:

```
adb shell am start -n app.morphe.manager/app.morphe.manager.MainActivity \
  -a app.morphe.manager.action.BATCH_PATCH \
  --esa packages com.google.android.youtube,com.reddit.frontpage
```

`packages` takes a string array or a comma-separated string of package names.

This is off by default. Turn on **Allow external triggers** in **Settings → Advanced →
Updates**, and
every request asks for your confirmation first. A request never starts patching by itself: it
opens the preflight list, and the run begins when you tap **Start patching**.

### Trusting an app

The confirmation dialog offers **Always allow this app** only when Android can tell Morphe who
sent the request, which it does only for callers that launch Morphe **for a result**
(`startActivityForResult`). A plain `startActivity`, and the `adb` command above, arrive
anonymously.

This is deliberate. The other way to identify a caller is the referrer, and a sender can set
that to any package name it likes, which would let a hostile app both skip the dialog and show
someone else's name in it. Morphe would rather ask every time than trust a name that can be
made up.

So a request from an anonymous caller still works, it just shows **An unknown app** and asks
again next time.

## Troubleshooting

| Problem | What to do |
| --- | --- |
| An app sits at **No APK** | Morphe has no saved original for it. Attach a file, or patch it once normally so the original gets saved |
| An app sits at **Version** | The APK version is not supported by the patches. Attach a supported version, or **Patch anyway** if you know what you are doing |
| **Batch patching is already running** | A queue is in progress. Opening it from the home screen shows the running one instead of starting a second |
| No re-patch count on the home screen | The new patches are not on the device yet. Let the sources finish updating, or pull them by hand from the source sheet |
| **Install failed** on a renamed app | Installing a renamed package is a new install, not an update. On Xiaomi and similar devices, turn on "Install via USB" in developer options |

## Next steps

- [Updating a patched app](updating-patched-apps.md)
- [Choosing how patched apps are installed](installers.md)
- [Storage and saved data](storage-and-saved-data.md)
