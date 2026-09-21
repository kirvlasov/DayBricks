# DayBricks

DayBricks is a native Android day planner built around your device's calendar. Pick an activity, move the timeline to the time you want, adjust its duration, and confirm. DayBricks creates a regular calendar event; your calendar account handles any subsequent calendar sync.

## What you can do

- **Plan on a single-day timeline.** Browse timed and all-day events, including recurring and overlapping events. Scroll or zoom to find a time, then place an activity in five-minute start increments with a duration from 1 to 720 minutes. The timeline accounts for daylight saving time changes.
- **Reuse activities.** Create and reorder templates with an optional emoji, description, default duration, reminder, and activity options. A small set of starter templates is available on first launch. Templates are stored locally and work offline.
- **Work with your calendars.** Choose a writable calendar for new activities and which readable calendars appear in DayBricks. Tap an event to see its details, open it in a calendar app, or delete it after confirmation. Deleting a template does not delete events created from it.
- **Make the layout your own.** The template panel adapts to the available space, with side and bottom options. Choose a light, dark, or system theme and select an app language independently of the device language.
- **Move your app data.** Export templates and settings as JSON, or import DayBricks JSON or ZIP data by merging or replacing templates. Calendar events and sync credentials are not included in exports.

The app uses Android's calendar provider rather than maintaining a separate calendar. Calendar access requires read and write permissions and a writable calendar on the device. The template library remains available without calendar permission.

## Get started

1. Install the app on Android 8.0 (API 26) or newer. On first launch, choose a language and grant calendar access.
2. Select the calendar for new activities in **Settings**. If no writable calendar appears, add a calendar account in Android first.
3. Open the template library with **+**. Choose a starter template or create one with **Add new template**.
4. Select an activity, position it on the timeline, adjust its duration or reminder, and tap **✓** to create the event.

The interface and date and time formats follow the selected app language. DayBricks can plan and manage templates without a network connection.

## Build from source

Open `android/` in Android Studio with support for Android Gradle Plugin 9.1.1. The project uses JDK 21 (17 minimum), Android SDK Platform 37, and Build Tools 36.0.0. Set the SDK path through `ANDROID_HOME` or `android/local.properties`.

```powershell
cd android
.\gradlew.bat :app:assembleDebug
```

On Linux or macOS, run `chmod +x gradlew` and `./gradlew :app:assembleDebug` from `android/`. The APK is written to `android/app/build/outputs/apk/debug/app-debug.apk`. For development setup and checks, see [CONTRIBUTING.md](CONTRIBUTING.md). Release versions are described in [release-versioning.md](docs/release-versioning.md).

## Optional template sync

DayBricks works without a DayBricks account or server. If you want to sync templates across devices, you can run the included Go server (Go 1.27.x; no third-party Go dependencies):

```sh
cd server
go build -trimpath -o daybricks-server ./cmd/daybricks-server
export DAYBRICKS_TOKEN="$(openssl rand -hex 32)"
./daybricks-server
```

The server listens on `127.0.0.1:8080` by default and stores state in `./data`. Keep the token for the app and future server starts. For access from another device, configure a reachable address with `DAYBRICKS_LISTEN` and serve it over HTTPS; the supplied [systemd unit](server/deploy/daybricks.service) and [Caddyfile](server/deploy/Caddyfile) are deployment examples. In **Settings → Your sync server**, enter the base URL and token, then save. Sync runs after edits, when you return to the app, or when you choose **Sync now**. It does not run as a continuous background service. See [sync-protocol.md](docs/sync-protocol.md) for the protocol and deployment details.

Template data is sent to this server; calendar events are not. Calendar events stay with Android's calendar provider and are synced, if applicable, by your calendar account. Templates may themselves contain personal information, so protect the server's data directory, token, and network connection.

## Project notes

The Android app is built with Kotlin, Jetpack Compose, and Room. The optional sync server is written in Go. DayBricks has no ads, analytics, registration, or third-party crash reporting; Android backup is disabled.

- [Architecture](docs/architecture.md)
- [UI behavior](docs/ui-behavior.md)
- [Verification](docs/verification.md)
- [License: Apache 2.0](LICENSE)
