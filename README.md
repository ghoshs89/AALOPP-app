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
