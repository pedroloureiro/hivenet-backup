# Prepare for Backup: Specification

What the app does and how it is built. Terms in **bold** are defined in [CONTEXT.md](CONTEXT.md); decisions with real trade-offs are in [docs/adr/](docs/adr/). This file is the source of truth for behaviour: when the code and this spec disagree, one of them is wrong and gets fixed in the same change.

## Behaviour

### 1. Picking Source files

- **Select files** opens the system document picker (`ACTION_OPEN_DOCUMENT`, multiple selection, any type).
- For each picked file the app reads its display name, its reported size (may be unknown) and its MIME type, and lists the selection with name and size.
- The selection lives in memory only. Nothing is recorded until the user starts a **Batch**. **Clear** empties it.
- While a **Batch** is running, **Select files** and **Back up** are disabled.

### 2. Running a Batch

Tapping **Back up N files** starts a **Batch** in the app-wide scope (ADR-0002):

1. One row per **Source file** is recorded in one transaction under a new **Batch** id, every file **Waiting**, in picked order.
2. Files are processed one at a time, in order. For each file:
   1. Mark it **Copying**.
   2. If the reported size is known and larger than the free space in the **Backup store** minus a 50 MB margin, it is **Failed: Not enough space** without writing anything.
   3. Stream the source into a temporary file inside the file's own folder (section 4), computing SHA-256 of the bytes as they are read.
   4. If the reported size is known and the byte count differs, it is **Failed: Copy didn't match source**.
   5. `fsync` the temporary file, read it back and hash it again. A different hash is **Failed: Copy didn't match source**.
   6. Rename the temporary file to its final name. Only now is the file **Backed up**, recorded with its **Fingerprint** and stored size.
   7. On any failure the file's folder, which holds only its temporary file, is deleted, the **Failure reason** recorded, and the next file starts.
3. Every file ends with exactly one final **Outcome**. Only cancellation of the scope (process death) stops the loop.

### 3. Failure reasons

| Cause | **Failure reason** |
|---|---|
| `FileNotFoundException` or `SecurityException` opening or reading the source (Downloads reports a deleted file as `SecurityException`) | Source no longer available |
| Any other `IOException` while reading the source | Couldn't read source |
| Pre-check above, or `ENOSPC` while writing | Not enough space |
| Byte count or re-read **Fingerprint** mismatch | Copy didn't match source |
| Anything else | Unexpected error (technical detail kept) |

### 4. Backup store layout

`filesDir/backups/<batchId>/<fileId>/<sanitised display name>` (ADR-0001). The per-file folder makes same-named **Source files** impossible to collide; the display name is kept so an opened or uploaded copy has a meaningful name. Temporary files end in `.part` and never sit under the final name.

Sanitising replaces `/` and NUL with `_` and turns an empty name, `.` or `..` into `_`. A name longer than 250 UTF-8 bytes (255, the file-system limit, less `.part`) is cut to fit at a character boundary, keeping an extension of up to 32 bytes.

### 5. Reopening the app

- On every process start, before any **Batch** can begin, abandoned files are settled: **Waiting** → **Not started**, **Copying** → **Interrupted**. Every `.part` file in the **Backup store** is deleted, and so is the folder `<batchId>/<fileId>/` of each file just marked **Interrupted**: the app may have stopped after the rename but before recording **Backed up**, and an **Interrupted** file keeps nothing.
- The screen shows the most recent **Batch**: when it was made, a count per **Outcome**, and one row per **Source file** with name, size, **Outcome** (or progress while running), the **Failure reason** when **Failed**, and **Open** when **Backed up**.

### 6. Opening a Backup copy

**Open** hands the **Backup copy** (never the **Source file**) to a viewer app through a `FileProvider` scoped to `backups/` only, read-only, with the recorded MIME type. If no app handles the type, the system chooser says so.

## Architecture

Layer-based MVVM with Clean Architecture, one Gradle module, Koin for DI.

```
ui/               BackupScreen, BackupViewModel, BackupUiState
domain/
  models/         Batch, BatchFile, SourceFile, FileState, FailureReason, BackupCopy
  repositories/   BatchRepository, BackupCopyRepository, SourceFileRepository
  usecases/       RunBatch, SettleAbandonedBatch
  BackupCopyFailedException
data/
  repositories/   BatchRepositoryImpl, BackupCopyRepositoryImpl, SourceFileRepositoryImpl
  BatchDatabaseManager, BackupDao, BackupDatabase, BatchFileRecord, BatchFileOutcome
di/               AppModule (app-wide scope), DataModule, DomainModule, UiModule
```

| Repository | Wraps | Owns |
|---|---|---|
| `BatchRepository` | Room, only through `BatchDatabaseManager` (ADR-0003) | The record of **Batches** and **Outcomes** |
| `SourceFileRepository` | `ContentResolver` | Read-only access to **Source files**: name, size, MIME type, stream |
| `BackupCopyRepository` | App-private files | Writing, confirming, opening and deleting **Backup copies** (steps 3–6 above) |

`BackupCopyRepository` reports the failures only the data layer can tell apart (`ENOSPC`, a mismatch, any other write-side error) as `BackupCopyFailedException` carrying its **Failure reason**. Exceptions from the **Source file**'s stream reach `RunBatch` unchanged, and `RunBatch` maps them using the table in section 3.

`BatchDatabaseManager` works only with data-layer types; `BatchRepositoryImpl` maps them to and from the domain. Its operations: create a **Batch** with its files (one transaction, next **Batch** id, every file **Waiting**, in picked order; an empty **Batch** is refused), mark a file **Copying** (only from **Waiting**), record an **Outcome** (only on a **Copying** file, and only a final **File state**, so a recorded **Outcome** never changes), settle abandoned files (returning the files it marked **Interrupted**), observe the latest **Batch** (highest **Batch** id) with its files in picked order. A refused change throws `IllegalStateException`, an empty **Batch** `IllegalArgumentException`.

## Data model

Room holds the record of what happened to each **Source file**; the **Backup store** holds only the bytes, and a **Backed up** row points to its copy with `relativePath`. Rows of older **Batches** are kept: they hold the **Fingerprints** of copies still in the **Backup store**. One table, one row per **Batch file**:

- `batch_file_records`: `id`, `batchId` (the manager assigns the highest existing one + 1), `batchCreatedAtEpochMs`, `position`, `sourceUri`, `displayName`, `mimeType`, `reportedSize?`, `state` (`WAITING`, `COPYING`, `BACKED_UP`, `FAILED`, `INTERRUPTED`, `NOT_STARTED`), `failureReason?` (`SOURCE_NO_LONGER_AVAILABLE`, `COULD_NOT_READ_SOURCE`, `NOT_ENOUGH_SPACE`, `COPY_DID_NOT_MATCH_SOURCE`, `UNEXPECTED_ERROR`), `failureDetail?`, `storedSize?`, `fingerprint?` (lowercase hex SHA-256), `relativePath?`.
- Schema version 1, exported to `app/schemas/`.

## Testing

Living plan: revisit it whenever a class is added or gains logic (see CLAUDE.md).

| Class | Kind | Proves |
|---|---|---|
| `RunBatch` | JVM, fakes | Order; isolation; each **Failure reason** from its real cause; space check fails only the file that doesn't fit; exactly one **Outcome** per file |
| `BackupCopyRepositoryImpl` | JVM, real temp files | Byte-identical copy, empty files included; correct **Fingerprint**; reported size checked only when known; no file left after a read error, a byte-count mismatch or a write-side error, which surfaces as **Unexpected error** rather than a source exception; same names don't collide; sanitised and length-capped names; free space before the first copy; delete scope (`.part` files only, one file's folder only). Not covered: re-read mismatch and `ENOSPC`, which real files can't produce on the JVM |
| `BatchDatabaseManager` | Robolectric, in-memory Room | Transactional create, next **Batch** id, every file **Waiting** in picked order, empty **Batch** refused; **Copying** only from **Waiting**; an **Outcome** only on a **Copying** file and only a final state, so it never changes; settle rules and the files it marks **Interrupted**; latest **Batch** with files in picked order |
| `SettleAbandonedBatch` | JVM, fakes | Settles the record, removes `.part` files and the folder of each file it marks **Interrupted** |
| `BackupViewModel` | JVM, fakes | Starting clears the selection; state follows the latest **Batch** |

Not unit-tested: composables, `SourceFileRepositoryImpl` (proven by the demo), `BatchRepositoryImpl` (mapping only, covered through the above).

## Demo and seed scripts

`scripts/`, plain `adb` and shell, run against whatever device or emulator is connected:

| Script | Purpose |
|---|---|
| `seed.sh` | Text, image, PDF, 0-byte, 1 GB, same-named pair in two folders, emoji and very long names; then a media scan |
| `fail-delete-source.sh <name>` | Delete a picked file → Source no longer available |
| `fill-storage.sh` / `free-storage.sh` | **Emulator only.** Leave ~50 MB free, then undo → Not enough space |
| `grow-file.sh <name>` | Keep appending to a file → Copy didn't match source |
| `adb shell am force-stop <package>` | Kill mid-**Batch** → **Interrupted** and **Not started** |

## Platform and stack

minSdk 26, target SDK 37, compile SDK 37.1. Kotlin 2.4.21, AGP 9.3.3, Gradle 9.7.1, Java 17 bytecode (ADR-0004). Jetpack Compose BOM 2026.11.01 (Material 3), Coroutines 1.11.0 (BOM-aligned) and Flow, Room 2.8.5 with KSP 2.3.14, Koin 4.2.2. Tests: JUnit 4, kotlinx-coroutines-test, Robolectric 4.18.1 on a Java 21 test launcher. Versions live in `gradle/libs.versions.toml`.

## Future improvements

- Encrypt **Backup copies** before upload (Tink `StreamingAead` with an Android Keystore-backed keyset).
- Cloud upload as a WorkManager job reading from the **Backup store**.
- A foreground service so large **Batches** finish in the background.
- Retry for **Failed**, **Interrupted** and **Not started** files.
- Skip files whose **Fingerprint** is already in the **Backup store**.
- Detect a source changing mid-copy (size and last-modified before and after).
- Delete a **Batch** and its **Backup copies**.
- A history of older **Batches**.
- Compression, only for types that are not already compressed.
- A user-visible destination folder through SAF.
- Split `domain` and `data` into Gradle modules once a second feature arrives.
