# Screenshot Sorter — design

Date: 2026-10-09 · Status: waiting for review

## Purpose

On Android, screenshots from every app land in one folder. This app moves each
screenshot into a folder named after the app it was taken in, so the gallery
shows one album per app (Telegram, Duolingo, YouTube, ...).

The first user is a friend with a Redmi phone. The app is delivered as an APK
file sent to him directly. Google Play is a possible later step and is not part
of this version.

## What success looks like

- He takes a screenshot in Telegram. About 15 seconds later the file is in
  `DCIM/Screenshots/Telegram/` and the gallery shows it in a "Telegram" album.
- It keeps working after a reboot. If the phone killed the app in the
  background, the missed screenshots are sorted later.
- It never loses, overwrites or damages a screenshot.
- It works on Android 6.0 and newer, on any brand, not only Xiaomi.

## Not in this version

- Google Play release.
- Screen recordings (videos).
- Custom folder names or per-app rules.
- Undo (moving screenshots back).
- Languages other than English.

## How it finds the app

For each new image in a screenshot folder, the app tries these in order and
stops at the first that works:

1. **App ID in the filename (Xiaomi / Redmi / POCO).** Confirmed on the friend's
   phone: `Screenshot_2026-10-09-10-12-05-243_pdf.reader.pdfviewer.pdfeditor.jpg`.
   The part after the last `_` is the app ID. The app looks up the installed
   app with that ID and uses its display name ("PDF Reader - Hi Read").
2. **App name in the filename (Samsung).** Example:
   `Screenshot_20261009_101205_Telegram.jpg`. The ending is used only if it
   matches the name of an installed app, so endings like `_1` or `~2` are not
   mistaken for apps.
3. **Usage history (every other phone).** The app asks Android which app was on
   screen at the moment the file was created. This needs the "Usage access"
   permission. The app ignores itself and the system's screenshot tool.
4. **Otherwise** the screenshot is left where it is.

Folder name: the app's display name, with characters that are not allowed in
folder names removed. If the filename has an app ID but that app is no longer
installed, the app ID itself is the folder name.

Screenshot folders: any folder named `Screenshots` or `Screenshot` directly
inside `DCIM` or `Pictures`. A sorted file goes into a subfolder of the folder
it was found in. Only files directly inside a screenshot folder are processed,
so a file that was already sorted is never touched again.

Known side effect: the gallery may show two albums with the same name, for
example Telegram's own saved photos and the Telegram screenshots.

## Moving a screenshot

- The app waits at least 10 seconds after the file appears, and until the file
  has stopped changing. This keeps the system's edit/share popup working right
  after a screenshot.
- The move is a rename inside the same storage: instant, no copying. Afterwards
  the gallery index is told about the old and the new location.
- If the target folder already has a file with that name, ` (1)`, ` (2)`, ... is
  added. Nothing is ever overwritten.
- If a move fails, the file stays where it is, the failure is shown in the
  recent list, and the next catch-up pass tries again.
- The app remembers which screenshots it already looked at, so ones it could
  not identify are not re-checked on every pass. "Sort old screenshots"
  re-checks everything.

## Permissions

| Permission | Android versions | Needed for |
|---|---|---|
| Storage (normal popup) | 6 to 10 | Moving files |
| All files access (settings screen) | 11 and newer | Moving files |
| Usage access (settings screen) | all | Only on phones whose screenshot names do not contain the app |
| Ignore battery optimisation | all | Staying alive in the background |
| Notifications | 13 and newer | Optional; sorting works without it |

The app looks at the existing screenshot names on the phone. If they already
identify the app (Xiaomi, Samsung), Usage access is shown as "not needed on
this phone". On the friend's Redmi that leaves one permission plus the
background setting.

## Staying alive in the background

- A foreground service with a silent, low-priority notification watches the
  phone's media index for new images.
- The service starts when sorting is switched on, when the app is opened, and
  after a reboot.
- A catch-up pass sorts anything that was missed. It runs when the service
  starts, when the app is opened, and from a periodic background job about
  every 15 minutes (the shortest interval Android allows).
- Brand help: on Xiaomi/Redmi/POCO, Oppo/Realme, Vivo and Huawei/Honor the app
  tries to open that brand's "autostart" settings screen. If it cannot, it
  shows two lines of instructions instead.

## The screen

One screen, following the system's light or dark mode. The app uses its own
fixed green colours in both, the same on every phone. It has four parts:

- **Setup checklist.** One row per permission above, each with its status and a
  button that opens the right settings screen.
- **On/off switch** for sorting.
- **"Sort old screenshots" button.** It first shows how many screenshots it can
  sort and asks for confirmation. On Xiaomi and Samsung this covers all old
  screenshots. On other phones it covers only the last few days, because
  Android does not keep usage history longer.
- **Recent list.** The last 20 results: file, folder it went to, time, or the
  reason it was not moved.

## Code structure

Kotlin, Jetpack Compose, one module. Each unit has one job:

| Unit | Job |
|---|---|
| `ScreenshotNameParser` | Filename to app ID or app-name candidate. Pure logic. |
| `AppResolver` | App ID or app name to the installed app's display name. |
| `ForegroundAppFinder` | Usage history to "which app was on screen at this time". |
| `ScreenshotFinder` | Lists unsorted screenshots in the screenshot folders. |
| `FileMover` (interface), `DirectFileMover` | Moves a file and updates the gallery index. |
| `Sorter` | Handles one screenshot: find app, pick folder, move, log. Used by the service, the catch-up pass and "Sort old screenshots". |
| `WatchService` | Foreground service: watches for new images, waits, calls `Sorter`. |
| `BootReceiver`, `CatchUpJob` | Restart after reboot; periodic catch-up. |
| `SetupState` | Which permissions are granted; how to open each settings screen per Android version and brand. |
| `HandledStore`, `ActivityLog` | What was already looked at; the recent list. |
| UI | One screen and its view model. |

`FileMover` is an interface so a Google-Play-compatible mover can replace
`DirectFileMover` later without touching the rest.

## Testing

- **Unit tests** for the pure logic: filename parsing (Xiaomi, Samsung, Pixel,
  edited copies, odd names), folder-name cleaning, name collisions, and picking
  the foreground app from a list of usage events.
- **Simulated Android on the computer** (Robolectric) on Android 6.0 and Android 15: every Android call the
  app makes, the background service, the boot receiver, the periodic job, and a launch of the real screen.
  No emulator was used because the download is several gigabytes; the real phone is the first full test.
- **Real phone.** The friend's Redmi, with a short checklist to follow. Only a
  real phone shows background killing and how the gallery displays the albums.

## Build and delivery

- Minimum Android 6.0 (API 23), the lowest the current Android libraries
  support. Targets Android 16 (API 36) and is compiled against API 37.
- App ID `com.zyrex.screenshotsorter`. It cannot be changed after a Google Play
  release.
- A release APK signed with a new key. The key file stays in the project folder
  and out of git. It must be kept: updates only install over the old version if
  they are signed with the same key.
- The 1.0 APK is built without code shrinking. It could not be run on a phone
  before it was sent, and the tests only run the unshrunk code, so a crash
  caused by shrinking could not have been found or diagnosed.
- Tools: JDK and Android SDK command-line tools installed in user folders, no
  administrator rights, no Android Studio. Gradle's build output is kept
  outside OneDrive so syncing does not lock files during builds.

## Later: Google Play

- Replace `DirectFileMover` with a mover that uses the system's media API and
  asks the user to confirm moves, because Google restricts "All files access".
- Add a disclosure screen for Usage access.
- Replace the `QUERY_ALL_PACKAGES` permission with a `<queries>` block, and drop the direct
  battery-optimisation request, both of which Google restricts.

Nothing in this version blocks that.

## Things only a real phone can confirm

- That the Xiaomi gallery shows subfolders of `DCIM/Screenshots` as separate
  albums.
- That autostart plus the battery setting is enough to survive on the Redmi.
- On Android 15 and newer, "Usage access" may be greyed out for an app
  installed from an APK until "Allow restricted settings" is turned on in the
  app's info page. The app shows instructions for this. It does not affect
  Xiaomi or Samsung phones, which do not need Usage access.
