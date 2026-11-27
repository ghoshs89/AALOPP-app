# Puzzle Quest (Android)

Adaptive learning and cognitive reasoning practice app built with Kotlin and Jetpack Compose, preserving the complete curriculum bank, adaptive spaced-review engine, visual puzzles, and grown-ups analytics dashboard from AALOPP (Puzzle Quest).

## Core Features

- **Adaptive Event-Sourced Engine**: Replays attempt history deterministically (`SkillStateEngine`, `SessionSelector`) with mastery criteria (4 of last 5 right across 2+ days), spaced review intervals (1, 3, 7, 14, 30 days), per-strand working bands, and soft-gated everyday convention skills.
- **Complete Question & Asset Bank**: Loads all verified question sets from `content/questions/*.json`, `content/skills.json`, `content/themes.json`, and vector assets from `public/flags`, `public/logos`, and `public/maps`.
- **Interactive Puzzle Player**: Supports multiple-choice (text & picture choices), numeric keypad, Find-the-Bug with gentle first-mistake nudges, interactive ordering boards, first-time teach cards, hints, scratchpad canvas (with stylus palm rejection), Android Text-to-Speech (English, Spanish, French), and optional voice answers.
- **Grown-Ups Dashboard & Progress Report**: PIN-gated parent area (default PIN `1234`, changeable in-app) with Overview charts, Skills progress, interactive Prerequisite Skill Map, Recent Answers log, Parent Notes, player management, and a shareable Progress Report.

## Tech Stack

- Kotlin, Jetpack Compose, Material Design 3, Navigation Compose
- Local SQLite persistence (`PuzzleQuestDatabase`)
- Kotlinx Serialization JSON, Coil SVG + AndroidSVG, Android TextToSpeech & SpeechRecognizer
- Bundled Google Fonts (`Baloo 2`, `Nunito`)

## Install and update

Install the signed APK from [GitHub Releases](https://github.com/ghoshs89/AALOPP-app/releases/latest). On Android 8 or later, open **Grown-ups** to check for updates. When a newer stable release is available, choose **Install update**. Android may ask you to allow installations from Puzzle Quest. Return to the app and confirm the installation in Android’s installer. Installing over the existing app keeps its local profiles, answers and notes.

Practice works offline. The update check contacts GitHub for release metadata and sends no learner records. The app verifies the APK digest, package name, version code and signing certificate before opening the installer.

## Publish a release

1. Set `version.txt` to a higher `MAJOR.MINOR.PATCH` version (minor and patch each 0–99). Commit it and tag that commit with the matching `v` prefix.
2. GitHub Actions builds with JDK 21, Gradle 9.3.1, SDK 36 and Build Tools 36.0.0. To publish from a tag automatically, configure repository secrets `PUZZLE_KEYSTORE_BASE64` (the original publisher PKCS12 file, base64 encoded) and `PUZZLE_KEY_PASSWORD`. The signing alias is `puzzlequest`. Keep a private offline backup of both files.
3. For local signing, export `PUZZLE_KEYSTORE` as the absolute keystore path and `PUZZLE_KEY_PASSWORD` from private storage. Run `gradle :app:assembleRelease`. With `ANDROID_HOME` set, run `scripts/verify-release.sh app/build/outputs/apk/release/app-release.apk vX.Y.Z`, substituting the tag. Publish the verified file as `PuzzleQuest-vX.Y.Z.apk` on a stable GitHub Release for that tag. The updater uses the SHA-256 digest supplied by GitHub.
4. Workflows without signing secrets produce a build artifact for offline signing. Only APKs that pass the pinned certificate and version checks are eligible for publication. Never regenerate the publisher key for an update.

For an offline-signed APK, the **Release APK** workflow can also publish from the repository’s Git blob store. After the tag exists and the APK passes the local verifier, run the following from the repository. This transfers only the signed APK.

```sh
tag="v$(cat version.txt)"
apk=app/build/outputs/apk/release/app-release.apk
scripts/verify-release.sh "$apk" "$tag"
blob=$(base64 < "$apk" | jq -Rs '{encoding:"base64", content:.}' |
  gh api "repos/ghoshs89/AALOPP-app/git/blobs" --input - --jq .sha)
gh workflow run release-apk.yml --ref main -f signed_blob="$blob" -f release_tag="$tag"
```
