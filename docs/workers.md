# Worker System

## ProfileScheduler

Schedules automatic uploads.

Supports:

* daily
* hourly
* every 2 hours
* every 6 hours

## ProfileWorker

Current responsibilities:

* scan media
* generate content
* export video
* upload video
* cleanup temp files

## Problems

ProfileWorker currently handles too many responsibilities.

Planned refactor:

* GenerateWorker
* UploadWorker
* CleanupWorker

## UploadRunGuard

Prevents duplicate uploads within 30 minutes.
