# Using an APK download helper

Patching starts with the original, unpatched APK, and Morphe normally sends you to a website
to fetch it by hand. A *download helper* is a separate app that does that part for you and
hands the downloaded file straight back to Morphe.

Morphe ships no helper of its own and does not endorse any. The integration is an open intent
contract: any app can implement it.

## Turning it on

**Settings → System → Files & storage → APK download helper**. The toggle only appears once
an app implementing the contract is installed, and it is off by default.

With it on, the download instructions dialog gains a **Use a helper app** button
next to the usual **Continue** button.

## What happens when you use it

1. Morphe shows which app the file will come from and asks you to confirm. When several
   helpers are installed, you pick one.
2. The helper opens with a description of the APK Morphe needs: package name, version and its
   build codes, other versions that would also work, your device ABIs, and the archive format
   the patch bundle expects.
3. The helper downloads the file and returns it. Morphe then treats it exactly like a file you
   picked yourself.

Morphe never lets a helper install anything. If an unpatched app has to be installed first for
a mount install, Morphe does that itself.

## What Morphe checks

A helper is a convenience, not a trusted source. Every file coming back goes through the same
checks as a manual selection:

- the package name matches the app you chose,
- the version is one the patch bundle supports,
- the signature matches what the patch bundle declares.

The signature check has limits worth knowing about. It needs Android 11 or newer, because
older versions cannot read a signature out of an archive file, and it needs the patch bundle
to declare the expected signatures. When Morphe cannot verify the signature, the confirmation
dialog says so before you continue.

## Writing a helper

The contract lives in
[`ApkDownloadHelperContract.kt`](../app/src/main/java/app/morphe/manager/util/ApkDownloadHelperContract.kt).
In short, a helper declares an exported activity with an intent filter for
`app.morphe.manager.action.DOWNLOAD_ORIGINAL_APK` and `android.intent.category.DEFAULT`, reads
the request from the intent extras, and answers with `RESULT_OK`, the downloaded file in
`Intent.setData`, and `FLAG_GRANT_READ_URI_PERMISSION` so Morphe can open it. The answer must
use a `content://` Uri; a result without the flag, or on any other scheme, is rejected before
Morphe reads anything.

Requests are always sent to the exact component the user picked, so a helper is never invoked
just for claiming the action.

## Troubleshooting

| Problem | What to do |
| --- | --- |
| The toggle is missing in Settings | No installed app implements the contract |
| The button is missing in the dialog | The toggle is off, or the helper was uninstalled since Morphe last looked |
| "The helper app did not return an APK" | The helper finished without handing back a file. Use **Continue** to download it manually |
| "The helper app did not grant access to the APK it returned" | The helper answered without `FLAG_GRANT_READ_URI_PERMISSION` |
| A wrong package or version warning | The helper returned a different app or version than the one requested |

## Next steps

- [Patching an app in Simple mode](patching-simple-mode.md)
- [Using the built-in file picker](file-picker.md)
