# AtlasAppWindow Repository Guide

## Scope and target

These instructions apply to the entire repository. AtlasAppWindow targets the tested portrait
Android 11 automotive head unit at 1440x1920. Keep confirmed device behavior, Android platform
facts, and implementation assumptions visibly separate.

## Product boundary

- An ordinary sideload APK cannot embed an arbitrary foreign Activity as a child View. The Android
  11 implementation uses an OEM-supported freeform task launched with `ActivityOptions` bounds and
  freeform mode. Device-local ADB is an optional lifecycle/verification helper, not a launch
  prerequisite. Keep that backend behind an interface so a platform-signed ActivityView backend
  can replace it later.
- Never claim the freeform backend works on every Android device. Direct launch requires
  multi-window/freeform support on the target firmware. Exact existing-task resize/removal also
  requires local ADB access, shell authorization, and compatible `am` commands.
- The screen outside the target bounds must remain interactive. Atlas's overlay is chrome and
  controls only; the foreign application's task is owned by Android's window manager.
- Do not use MediaProjection screenshots as a fake interactive embed and do not inject input by
  coordinate automation.
- Do not force-stop user applications during normal switching. Remove or resize only task IDs that
  Atlas positively associated with a selected component.

## External commands

- The public command contract is `com.mmwtl.atlasappwindow.action.SHOW`, `.SWITCH`, and `.HIDE`.
  Accept only explicit flattened launcher components or locally stored preset IDs.
- Treat every external extra and every shell output as untrusted. Shell command builders must use
  strict allowlists for component names and numeric task/bounds arguments; never concatenate raw
  intent strings into a shell command.

## UI and source structure

- Preserve the Atlas Media Widget graphite system used by its current settings screen:
  `#1D2228` background, `#262626` cards, `#333333` nested surfaces, `#F5F5F5` primary text,
  `#D4D4D4` secondary text, and `#7893A0` accent.
- Keep Android/OEM/ADB behavior at adapter and service edges. Keep geometry, command parsing,
  task matching, and state transitions in pure testable Java classes.
- Avoid runtime dependencies except the small ADB transport required by the backend.

## Versioning and artifacts

- Keep `appVersionCode` and `appVersionName` at the top of `app/build.gradle`.
- Increment both for each completed user-requested application improvement.
- Preserve archive naming `<versionName>[<versionCode>]AtlasAppWindow`.
- Never commit signing keys, local SDK paths, generated APKs, Gradle caches, ADB private keys, or
  device dumps.

## Verification

Before handoff run:

```sh
sh gradlew --offline clean check assembleRelease
```

Inspect the release package/version metadata. Unit tests must cover command validation, bounds,
shell parsing, preset selection, and task ownership. Real-device acceptance must cover HOME touch
outside the window, in-window touch/IME, rotation/configuration, switching, relaunch, sleep/wake,
and recovery after ADB loss.

## Repository hygiene

- Preserve unrelated user changes.
- Do not edit sibling Atlas repositories.
- Create a Git commit after a verified improvement unless the user asks to leave it uncommitted.
