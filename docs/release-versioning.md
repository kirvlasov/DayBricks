# Android release versioning

The single source of truth is `android/version.properties`.

- `VERSION_CODE` is a positive integer and must increase for every published APK or bundle.
- `VERSION_NAME` is the user-facing semantic version (`MAJOR.MINOR.PATCH`, with an optional prerelease/build suffix).
- Increment `PATCH` for compatible fixes, `MINOR` for compatible functionality, and `MAJOR` for incompatible product or data-contract changes.
- The Gradle build validates both properties during configuration. A release must never reuse a previously published `VERSION_CODE`.

For the next release, update both values in the same change before running `assembleRelease`.
