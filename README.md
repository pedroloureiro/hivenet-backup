# Prepare for Backup

An Android app for the Hivenet "Prepare for Backup" homework. You pick files with the system document picker and back them up together as a **Batch**. The app copies each **Source file** into a private **Backup store**, confirms the **Backup copy** against a SHA-256 **Fingerprint**, and records one **Outcome** per file. When a file fails, the **Outcome** carries a **Failure reason**. The record survives restarts, and a **Batch** stopped by process death shows its files as **Interrupted** and **Not started** on the next start.

The glossary is in [CONTEXT.md](CONTEXT.md). Behaviour, architecture and the test plan are in [SPEC.md](SPEC.md). Decisions with real trade-offs are in [docs/adr/](docs/adr/).

## Build and run

### What you need

- JDK 17 or newer to run Gradle 9.7.1. The build compiles to Java 17 bytecode and runs unit tests on a Java 21 launcher, because Robolectric needs Java 21 to emulate SDK 37. If either JDK is missing, the foojay toolchain resolver in `settings.gradle.kts` downloads it ([ADR-0004](docs/adr/0004-toolchain-capped-by-kotlin.md)).
- The Android SDK with the 37.1 platform installed (`ANDROID_HOME`, or `sdk.dir` in `local.properties`).
- `adb` on the `PATH` for the demo scripts.

### Build, install, test

Clone the repository, or unpack the source archive from the GitHub release:

```sh
git clone https://github.com/pedroloureiro/hivenet-backup.git
cd hivenet-backup
./gradlew installDebug   # builds the debug APK and installs it
./gradlew test           # runs the 62 local tests
```

`installDebug` installs on every connected device unless `ANDROID_SERIAL` names one. The launcher label is **Hivenet Backup**.

All tests are local. JVM tests use hand-written fakes, `BackupCopyRepositoryImpl` tests write real files in a temporary folder, and `BatchDatabaseManager` tests run Room in memory under Robolectric. The project has no instrumented tests. Connected test tasks uninstall the app and wipe its data, so don't run them after you prepare demo data.

### Android versions and devices

The app has minSdk 26, target SDK 37 and compile SDK 37.1. I ran it on two devices:

- A Pixel 8 (physical) on API 37. I used it for everyday runs and for `seed.sh`, `fail-delete-source.sh` and `grow-file.sh`.
- The emulator AVD `Pixel_3a_API_34_extension_level_7_arm64-v8a`, API 34 (Android 14). I used it for `fill-storage.sh` / `free-storage.sh` and both **Not enough space** paths.

### Demo scripts

The scripts in `scripts/` are plain `adb` and shell. They run against whatever is connected; set `ANDROID_SERIAL` to pick one device when several are attached.

| Script | What it does |
|---|---|
| `seed.sh` | Empties `/sdcard/Download/PrepareForBackup/` and fills it with a text file, an image, a PDF, a 0-byte file, a 1 GB file (`large-1GB.bin`), two `same-name.txt` files in different folders, an emoji name and a 253-byte name. Then it runs a media scan so the picker sees them. |
| `fail-delete-source.sh <name>` | Deletes a seeded file. Pick it first, run the script, then back it up: it ends **Failed**, **Source no longer available**. |
| `grow-file.sh <name>` | Appends to a seeded file every second until Ctrl-C. Back it up while it grows: the byte count no longer matches the reported size, so it ends **Failed**, **Copy didn't match source**. |
| `fill-storage.sh` | Emulator only: it refuses to run on a physical device. Fills `/data` until 51 MB is free, 1 MB over the 50 MB space margin in `RunBatch`. Small files still fit; `large-1GB.bin` fails the pre-check with **Not enough space**. |
| `free-storage.sh` | Emulator only. Deletes the fill file. |

The scripts take names relative to the seed folder, for example `./scripts/grow-file.sh notes.txt`.

To hit **Not enough space** from `ENOSPC` while writing (the second path), run `free-storage.sh`, start backing up `large-1GB.bin` while there is free space, and run `fill-storage.sh` while the file shows **Copying**.

To see **Interrupted** and **Not started**, start a **Batch** of several files and kill the app mid-copy with `adb shell am force-stop com.pedroloureiro.hivenetbackup`, then reopen it.

## Decisions

[SPEC.md](SPEC.md) has the full behaviour. These are the choices a reviewer is most likely to ask about.

### Backup copies live in app-private storage

The app keeps them at `filesDir/backups/<batchId>/<fileId>/<sanitised display name>` ([ADR-0001](docs/adr/0001-app-private-backup-store.md)). No other app or the user can edit a copy before a future upload, and I can write to a temporary name, `fsync` and rename. A folder picked through SAF gives no rename or `fsync` guarantee. The per-file folder means two **Source files** with the same name never collide. The cost: copies are invisible outside the app, so the screen has **Open**, which hands the **Backup copy** to a viewer through a `FileProvider` scoped to `backups/`.

### Atomic per file

A **Batch** is never all-or-nothing: one file failing doesn't affect the others. The app streams a file into a `.part` file while hashing it, checks the byte count against the reported size when that size is known, `fsync`s, reads the file back and compares the **Fingerprint**, then renames it. It records **Backed up** only after the rename succeeds. Any failure deletes that file's folder, records the **Failure reason** and moves to the next file. The mapping from exceptions to **Failure reasons** is the table in [SPEC.md section 3](SPEC.md#3-failure-reasons).

### Batches run in an app-wide scope

A **Batch** runs in a coroutine scope that Koin provides for the whole app ([ADR-0002](docs/adr/0002-copy-in-app-wide-scope.md)). It survives rotation and leaving the screen, and stops with the process. I left out WorkManager because it would resume after process death, which the brief puts out of scope and which would make **Interrupted** impossible. A foreground service is the upgrade if large **Batches** must finish in the background. Until then, a large **Batch** left in the background may end **Interrupted**.

### Settling on start

Before any new **Batch** can begin, the app turns **Waiting** into **Not started** and **Copying** into **Interrupted**, deletes every `.part` file, and deletes the folders of files it just marked **Interrupted** or finds **Removed**. A stop between the rename and recording **Backed up** therefore leaves nothing behind.

### Only `BatchDatabaseManager` touches the DAO

The manager is the only class given Room's DAO ([ADR-0003](docs/adr/0003-database-manager-guards-the-dao.md)). It enforces the rules the DAO can't: a **Batch** and its files are created in one transaction, a file moves **Waiting** → **Copying** → one final **Outcome**, and the only later change is **Backed up** → **Removed**. A refused change throws. Room holds one table, `batch_file_records`, one row per **Batch file**.

### Deleting a Backup copy

**Delete** records **Removed** first and then deletes the folder, so a stop in between leaves a folder that the next start deletes. It never leaves a **Backed up** row without its copy. The app never modifies, moves or deletes a **Source file**.

### Nothing leaves the device

`allowBackup="false"` and `dataExtractionRules` exclude every domain from Auto Backup and device transfer. A restore out of step could leave the record claiming **Backup copies** that never arrived.

### Toolchain

Every library and tool is on its newest stable release, except where that would leave a combination nobody tested ([ADR-0004](docs/adr/0004-toolchain-capped-by-kotlin.md)). Kotlin is the constraint: KGP 2.4.21 is tested up to AGP 9.3 and Gradle 9.7, so the build uses AGP 9.3.3 and Gradle 9.7.1 although AGP 9.5 and Gradle 9.9 exist.

## Time spent

About half an hour of planning and about 3 hours of implementation.

### Complete

Everything in SPEC.md's Behaviour sections 1 to 7: picking **Source files**, running a **Batch**, the five **Failure reasons**, the **Backup store** layout, settling on reopen, **Open**, and the **Backup copies** section with **Delete**. Issues #1 to #9 are merged (PRs #11 to #19), with 62 tests across the classes in [SPEC.md's test plan](SPEC.md#testing).

### Unproven

- The re-read **Fingerprint** mismatch (step 5 of a copy) has no test and no script. The `.part` file would have to change between `fsync` and the re-read, which real files on the JVM can't produce and no script produces. It stays a defensive check.
- `ENOSPC` while writing has no unit test either, because real files on the JVM can't produce it. I proved it by hand on the emulator with `fill-storage.sh`.
- The media scan at the end of `seed.sh` needs API 29 or newer, so seeding is unverified on API 26 to 28.

### Out of scope

[SPEC.md's Future improvements](SPEC.md#future-improvements) lists what I would add next. The main items are encryption before upload, cloud upload as a WorkManager job reading from the **Backup store**, a foreground service for large **Batches**, retry for **Failed**, **Interrupted** and **Not started** files, and skipping files whose **Fingerprint** is already in the **Backup store**.

## AI assistance

I used Claude Code (Claude Opus) throughout.

Planning took about half an hour, working with Claude. Its result is [CONTEXT.md](CONTEXT.md), [SPEC.md](SPEC.md) and the [ADRs](docs/adr/). I made every architecture, design and solution decision, or explicitly delegated it to Claude.

Claude broke the work into GitHub issues #1 to #10 with the repo's `/plan` command. Claude then implemented the code and tests, and made the commits and PRs with `/commit`. `CLAUDE.md` tells it to add no attribution lines, so the git history doesn't mark them as Claude's.

I did the code review. My comments on PRs #12 to #15 changed the design. In #12 I asked why `Fingerprint` was a value class with no operations of its own, and Claude dropped the wrapper. In #13 the Room schema became one table. In #14 the generic `String` and `File` extensions moved to a root `extensions/` package. In #15 the mapping of the document provider's `Cursor` moved out of the repository into `data/mappers/`. Claude addressed each comment with the `/address-review` skill, which gives every comment a verdict, replies on every thread and may push back when it has a concrete reason. Lasting conventions from those reviews went into `CLAUDE.md`.

Pivots during implementation were my choice. One was adding the **Backup copies** section with **Open** and **Delete** in the middle of issue #8, which brought in the **Removed** state.

The instructions and tools Claude worked from are in the repo:

- [`CLAUDE.md`](CLAUDE.md): project rules, including the glossary terms, the layers, the data-integrity rules and which classes are test-first.
- [`.claude/commands/plan.md`](.claude/commands/plan.md) (`/plan`): scopes the work and creates GitHub issues with acceptance criteria, one architectural concern each, labelled by layer.
- [`.claude/commands/commit.md`](.claude/commands/commit.md) (`/commit`): conventional commits referencing the issue, then push and open the PR. `CLAUDE.md` sets the order (`docs:` first, then `feat:`, then `refactor:` / `fix:` for review changes) and a PR body with `Closes #N`.
- [`.claude/skills/address-review/SKILL.md`](.claude/skills/address-review/SKILL.md) (`/address-review`): handles my review comments as described above.
- [`.claude/agents/submission-writer.md`](.claude/agents/submission-writer.md): the agent that drafted this README in my voice, from my own account of the time spent and of how I used AI.
