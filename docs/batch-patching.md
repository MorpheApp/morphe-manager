# Patching several apps at once

Patching one app at a time means sitting through every dialog again for the next one. A batch
run asks everything up front, then works through the whole list on its own: same patcher,
same result, one queue.

Morphe can also do this without you, on a schedule, for apps whose patches have moved on.

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
| **No APK** | No saved original, and the installed app cannot be used | **Attach APK** and pick the file |
| **Version** | This APK version is not covered by the patches | **Patch anyway**, or attach a supported version |
| **No patches** | No enabled source has patches for this app | Enable a source, or exclude the app |
| **Excluded** | You removed it from this run | **Include** to bring it back |

Where does the APK come from? Morphe looks for a saved original first, then for the stock app
on the device, and only asks you for a file when neither exists. An installed app is skipped
when it looks like it was already patched, because patching a patched APK produces a broken
build.

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
one. Cards keep their install button after a failure, so you can fix the cause and try again
without patching anything a second time. Once an app is installed the card offers to open it,
and an app that failed to patch gets a button for the full error, which is longer than the
card can show.

The refresh button next to the title re-plans the apps that failed or were canceled.

> [!NOTE]
> Patched APKs are kept only while **Keep patched APKs** is on in **Settings → System**. With
> it off they live long enough for you to install them from this screen and are dropped when
> the next batch starts.

## Automatic re-patching

**Settings → Advanced → Automatic re-patching** lets Morphe do all of the above on its own
when a patch source releases something newer than what your apps were built with.

Only apps that need no input are queued: they must have a saved original APK (or an unpatched
copy on the device) and a saved patch selection. Anything that would raise a question is
skipped rather than guessed.

| Setting | What it does |
| --- | --- |
| **Automatic re-patching** | Turns the schedule on |
| **Re-patch frequency** | How often Morphe checks, hourly through monthly |
| **Only while charging** | Waits for a charger, since patching is heavy on CPU and memory |
| **Install automatically** | Installs the results without asking. Needs Shizuku, see below |

Morphe reports itself through silent notifications: an ongoing one while the queue runs, and
a result when it finishes. Tapping either opens the queue, where the patched apps are waiting
to be installed.

### What it needs to work at all

Android does not let a background app run heavy work or start a foreground service on its own.
The one thing that changes this is exempting Morphe from battery optimization, which is why
Morphe asks for it the moment you turn the schedule on.

Without that exemption the run stops before it starts and tells you why, instead of failing
silently halfway through an app.

Installing without asking is a separate matter: only **Shizuku** can install unattended. With
any other installer the run patches, saves the APKs, and notifies you that they are ready.

> [!IMPORTANT]
> Some manufacturers, Xiaomi, Huawei, Samsung and OnePlus among them, kill background work
> regardless of Android's own rules. If the schedule never produces anything on such a device,
> allow Morphe to run in the background in the system settings as well.

## Starting a batch from another app

Automation tools can ask Morphe to open a queue:

```
adb shell am start -n app.morphe.manager/app.morphe.manager.MainActivity \
  -a app.morphe.manager.action.BATCH_PATCH \
  --esa packages com.google.android.youtube,com.reddit.frontpage
```

`packages` takes a string array or a comma-separated string of package names.

This is off by default. Turn on **Allow external triggers** in the same settings dialog, and
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
| The schedule never runs | Exempt Morphe from battery optimization, and check that your manufacturer is not blocking background work |
| **Install failed** on a renamed app | Installing a renamed package is a new install, not an update. On Xiaomi and similar devices, turn on "Install via USB" in developer options |

## Next steps

- [Updating a patched app](updating-patched-apps.md)
- [Choosing how patched apps are installed](installers.md)
- [Storage and saved data](storage-and-saved-data.md)
