# Batches run in an app-wide coroutine scope, not WorkManager or a service

A **Batch** is copied by a coroutine in one scope that lives as long as the app process, owned by the app and handed out through dependency injection. It survives rotation, leaving the screen and the app going to the background, and stops when the process dies. That stop is reported truthfully on the next start: the file being copied becomes **Interrupted** and the waiting ones **Not started**. The brief puts guaranteed background execution and process-death recovery out of scope, and this is the smallest design that still never loses track of a file.

## Considered Options

- **ViewModel scope**: ties the **Batch** to the screen, so pressing Back would cancel it and mark files **Interrupted** while the app is still running.
- **WorkManager**: reschedules work after process death, which is the recovery the brief excludes and would make **Interrupted** impossible. A worker may run after a reboot, so every picked file would need a persisted read permission, and work longer than about ten minutes needs a foreground notification anyway.
- **Foreground service (`dataSync`)**: keeps large batches going after the user leaves, at the cost of a notification permission on Android 13+, the `dataSync` type and permission on Android 14, and the six-hour cap on Android 15. It is the upgrade if finishing large batches in the background became a requirement.

## Consequences

- A large **Batch** left running in the background may end **Interrupted** if Android reclaims the process.
- A future cloud upload belongs in WorkManager: it reads from the **Backup store**, which needs no permissions from the user (ADR-0001).
