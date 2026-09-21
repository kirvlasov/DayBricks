# Contributing to DayBricks

DayBricks has an Android app in `android/` and an optional Go sync server in
`server/`. Keep changes focused and include tests when behavior changes.

## Development setup

- Android: JDK 21, Android SDK Platform 37, Build Tools 36.0.0, and an Android
  Studio version that supports Android Gradle Plugin 9.1.1. Set `ANDROID_HOME`
  or create `android/local.properties` with your SDK path.
- Server: Go 1.27.x. The server has no third-party Go modules.
- Windows: [local-toolchain.md](docs/local-toolchain.md) documents an optional
  isolated toolchain. That guide is in Russian and describes a maintainer's
  local setup; its `.tools/` directory is not part of the repository.

From `android/`, run the checks relevant to your change:

```sh
./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleDebug --dependency-verification=strict
```

On Windows, use `gradlew.bat` in place of `./gradlew`. The connected Android
tests require an emulator or test device:

```sh
./gradlew :app:connectedDebugAndroidTest --dependency-verification=strict
```

From `server/`, run:

```sh
test -z "$(gofmt -l .)"
go vet ./...
go test ./...
```

CI also builds the Android test APK, checks dependency metadata, and runs Go
tests with the race detector. See [verification.md](docs/verification.md) for
records of checks performed on previous releases.

## Repository boundaries

The release signing key, local SDKs, build outputs, server data, and API tokens
stay outside Git. The `artifacts/` directory is local release output. Release
version values live in `android/version.properties`; see
[release-versioning.md](docs/release-versioning.md) before preparing a release.

For behavior and design context, see [architecture.md](docs/architecture.md),
[ui-behavior.md](docs/ui-behavior.md), and [sync-protocol.md](docs/sync-protocol.md).
