# Verification record

This is a historical record of checks performed for earlier releases, not a
claim that every check has been rerun against the current source. The automated
checks for new changes are defined in [CI](../.github/workflows/ci.yml).

## Android 0.5.0 release

- All 26 locales have the new concise emoji, calendar, and backup labels.
- Signed APK and App Bundle built successfully; release lint vital passed.
- APK and App Bundle signatures verified; APK metadata reports version code 7,
  version name 0.5.0, minSdk 26, targetSdk 36.
- Full test suite and emulator tests were not rerun for this focused UI change.

Artifacts: `artifacts/DayBricks-0.5.0-release.apk`,
`artifacts/DayBricks-0.5.0-release.aab`.

## 21 September 2026 — Android 0.4.2 release

Completed successfully:

- Targeted language and starter-template tests, including complete valid packs
  for all 26 supported languages.
- Signed, R8-minified APK and App Bundle builds; release lint vital passed.
- APK and App Bundle signatures verified.
- APK metadata: `app.daybricks.planner`, version code 6 / name 0.4.2,
  minSdk 26, targetSdk 36.

Release artifacts:

- `artifacts/DayBricks-0.4.2-release.apk`
- `artifacts/DayBricks-0.4.2-release.aab`

## 20 September 2026 — Android 0.4.1 release

Completed successfully:

- Targeted `AppDataExporterTest` and `PlannerViewModelTest` checks for JSON/ZIP
  import, invalid-file rejection, merge, replace, and device-local calendar preservation.
- Signed, R8-minified APK and App Bundle builds; release lint vital passed.
- APK and App Bundle signatures verified.
- APK metadata: `app.daybricks.planner`, version code 5 / name 0.4.1,
  minSdk 26, targetSdk 36.

Release artifacts:

- `artifacts/DayBricks-0.4.1-release.apk`
- `artifacts/DayBricks-0.4.1-release.aab`

This file records completed checks separately from intended coverage.

## 20 September 2026 — Android 0.4.0 release

Completed successfully:

- 43 JVM unit tests, including starter-template and JSON/ZIP export coverage.
- Android lint (`0 errors`, `77 warnings`).
- 21 instrumentation tests on the API 36 emulator, including bottom-panel
  close-by-swipe behavior, activity-editor inheritance/actions, local Calendar
  Provider insertion, RTL side-panel gestures, and export controls.
- Signed, R8-minified release APK and signed App Bundle builds for version code
  4 / version name 0.4.0.
- APK signature verification with the persistent RSA-4096 DayBricks release
  certificate; App Bundle JAR signature verification also passed.
- APK metadata: `app.daybricks.planner`, version code 4 / name 0.4.0,
  minSdk 26, targetSdk 36.

Release artifacts:

- `artifacts/DayBricks-0.4.0-release.apk`
- `artifacts/DayBricks-0.4.0-release.aab`

## 20 September 2026 — Android 0.3.0 release

Completed successfully:

- 38 JVM unit tests.
- Android lint (`0 errors`; existing non-blocking warnings remain).
- Debug APK and instrumentation APK builds.
- 18 instrumentation tests on the API 36 emulator, including bottom-panel
  swipe/open/close behavior, two consecutive resize gestures, removal of the
  covered navigation dock, and the dedicated activity editor dialog.
- Signed, R8-minified release APK and signed App Bundle builds for version code
  3 / version name 0.3.0.
- APK signature verification with the persistent RSA-4096 DayBricks release
  certificate, plus package/version/minSdk/targetSdk inspection.
- Go `vet ./...` and `test ./...`.

Release artifacts:

- `artifacts/DayBricks-0.3.0-release.apk`
- `artifacts/DayBricks-0.3.0-release.aab`

The aggregate check's Android phase passed. Its separate formatting gate still
reports the pre-existing `server/internal/state/model.go` as not matching
`gofmt`; that unrelated server file was not modified by this Android release.

## 19 September 2026 — local Windows build

Environment: JDK 21.0.12.1, Gradle 9.3.1, AGP 9.1.1, Android SDK 37.0,
Go 1.27.1, Android API 36 x86_64 Google APIs emulator.

Completed successfully:

- Android debug APK build.
- Android release APK build with R8, release lint checks and the persistent
  local RSA-4096 release key. The verified package is `app.daybricks.planner`,
  label `DayBricks`.
- Android test APK build.
- Android lint (`lintDebug`, zero errors; non-blocking version warnings).
- 32 JVM unit tests: 5 event layout, 10 time/domain math, 11 sync,
  and 6 ViewModel/state tests, including the local calendar display filter.
- Go `gofmt -l` (no output), `go vet ./...`, and `go test ./...`.
- One Calendar Provider instrumentation test: creates a private temporary
  local calendar whose provider `VISIBLE` flag is `0`, inserts a tagged event
  through the production repository, verifies duration/ownership, verifies a
  recurring occurrence through `CalendarContract.Instances`, then removes the
  temporary calendar. This covers providers whose display flag differs from
  the calendar client's own visibility setting.
- Twelve Compose UI instrumentation tests: bottom pane open/close, template
  selection and placement, duration accessibility action, confirm, cancel,
  date arrows, RTL confirmation at font scale 2.0, activity
  recreation with draft, explicit and automatic side-pane layouts on phones,
  a compressed calendar strip, closing the side pane by swiping directly on
  it, collapsible calendar settings and a persistent two-pane layout.
- The current interactive side-pane implementation was rerun separately on
  the API 36 emulator after its follow-the-finger animation was added.
- One Room instrumentation test for transactional outbox acknowledgement,
  concurrent local preference preservation and delete operation.
- Gradle dependency locks and SHA-256 verification metadata generated,
  including Windows, Linux and macOS AAPT2 artifacts. Gradle Wrapper
  distribution SHA-256 pinned.

The debug APK produced by these checks is at
`android/app/build/outputs/apk/debug/app-debug.apk`.
The signed release artifact is at `artifacts/DayBricks-0.1.0-release.apk`.

Not claimed by this record:

- Manual validation on physical foldable hardware or a physical device.
- A production VPS/TLS deployment or multi-process access to FileStateStore.
- Visual checks across every font/theme/window-size combination listed in
  the product specification. Policy fixtures cover all listed sizes; the UI
  instrumentation suite renders representative bottom, side and persistent
  layouts plus font scale 2.0.
- Android accessibility scanner results. Semantic actions and descriptions
  are covered structurally and by the duration action UI test.

CI repeats debug compilation, unit tests, lint, test APK build, dependency
verification, Go formatting, vet and race-enabled tests. Instrumentation is
kept as an explicit emulator/device job because it needs a Calendar Provider.
