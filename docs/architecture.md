# ShortsForge Architecture

## Tech Stack

* Kotlin
* Jetpack Compose
* MVVM
* Hilt
* Room
* WorkManager
* Media3
* MediaCodec

## Main Systems

### Profiles

Profiles represent automation channels/accounts.

Each profile stores:

* media folder
* upload settings
* schedule
* generation config
* social credentials

### Automation

Scheduling is handled through WorkManager.

Main components:

* ProfileScheduler
* ProfileWorker
* MoodScheduler
* MoodWorker

### Video Generation

Two modes:

1. Image slideshow generation
2. Source video clip generation

### Upload Pipeline

Exported videos are uploaded to:

* YouTube
* Facebook
* Instagram
* TikTok

### Storage

* Room database
* SAF folder access
* SharedPreferences (legacy systems)
