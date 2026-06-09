# ShortsForge AI Agent Instructions

This is a Kotlin Android automation app.

Tech stack:

* Jetpack Compose
* MVVM
* Hilt
* Room
* WorkManager
* Media3

Important systems:

* Upload scheduling
* Video rendering
* Media scanning
* Multi-platform upload

Rules:

* Keep workers focused
* Avoid blocking operations
* Preserve upload compatibility
* Prefer repository pattern
* Avoid duplicate scheduling logic
* Avoid modifying exporter unless required

Current planned refactor:

* Introduce UploadTaskEntity queue
* Split ProfileWorker responsibilities
* Add foreground workers
* Add retry system
