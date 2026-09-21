# Architecture

One Android module, manual dependency injection in `AppContainer`.

`PlannerScreen` consumes immutable `PlannerUiState` and emits
`PlannerAction`. `PlannerViewModel` owns placement and selection state;
it depends on repository interfaces, never `ContentResolver` or HTTP.
`SavedStateHandle` contains the serializable transient planning session.

Pure domain helpers: `TimeAxisMapper`, `EventLayoutEngine`,
`DurationRulerMath`, `DefaultPlacementPolicy`, `AdaptiveLayoutPolicy`,
`OwnershipDetector`. Positions use elapsed minutes from the selected
date's zoned start of day; the next zoned midnight defines its end.

`AndroidCalendarRepository` queries `Instances` on IO, cancels provider
queries with `CancellationSignal`, and observes the Instances, Events and
Calendars URIs with a `ContentObserver`. Changing dates cancels the previous
flow. Non-recurring DayBricks events are also read from `Events` until their
local instances appear; duplicate IDs and sync snapshots are excluded. All
locally available instances are displayed because calendar clients
do not consistently mirror their own display setting into the provider's
`Calendars.VISIBLE` flag. The user can hide individual readable calendars in
Settings; their Android IDs stay in device-local preferences and newly found
calendars remain visible by default. Only the configured writable calendar
receives new events. The repository creates events and can delete an event
after explicit user confirmation; it does not update existing events.

Room is the single configuration store. `LocalStateRepository` stores
templates (including presets as JSON), device/shared preferences, outbox,
and sync metadata. Theme, pane layout, zoom and Android calendar IDs are
device-local. Template edits and their operations are one transaction.
Room's exported v1 schema lives in `android/app/schemas/`.

`StateSyncRepository` serializes sync attempts with a mutex. It reads a
snapshot of pending operations, fetches server state, applies them, then
conditionally PUTs. On success, a Room transaction acknowledges only the
snapshot's sequence IDs, replays remaining operations, and replaces local
templates/shared preferences. Concurrent local edits are never discarded.
Template reminder defaults sync with templates; the device-wide default only
initializes newly created templates and remains local to that device.

Downloads are fully validated before the DB transaction. Failure preserves
the previous DB and outbox. There is no wall-clock last-write-wins logic.

Keystore credentials are separate from Room and JSON. The transport rejects
unsafe URLs, limits responses to 2 MiB, and refuses redirects. Every request
has a finite timeout and coroutine cancellation cancels its OkHttp call.

The Go API derives a principal from the bearer token. `StateStore` takes
that principal explicitly. The file implementation hashes the ID, serializes
CAS operations, writes a private temporary file, fsyncs it, renames it, and
syncs the directory on Unix. A corrupt stored state is an error, never an
empty replacement. One process must own the directory; horizontal server
scaling needs a store with interprocess CAS.

Known implementation choices: no adjacent-day cache (provider flow is the
fresh source); no network background service; v1 Room has no destructive
migration fallback. Future migrations must preserve outbox and templates.
