# Prepare for Backup

An Android app that lets a person pick real files on their device and keep local backup copies of them, with a lasting record of what happened to each file.

## Language

**Source file**:
A file the user picked with the system document picker. The app only reads it and never changes or deletes it.
_Avoid_: original, input file, document

**Batch**:
The set of **Source files** the user chose to back up together in one action, kept as a point-in-time snapshot. A **Batch** has one **Outcome** per **Source file** and owns its **Backup copies**; no two **Batches** share or replace each other's copies.
_Avoid_: job, backup, session, upload

**Backup copy**:
A complete copy of one **Source file**, kept by the app and confirmed identical, byte for byte, to what was read from the source. It has exactly one **Fingerprint**. A partial or unconfirmed file is never a **Backup copy**.
_Avoid_: backup, copy, staged file, archive

**Fingerprint**:
The content hash of a **Backup copy**, recorded with its **Outcome**. It proves the stored copy matches what was read, and is what a future upload would be checked against.
_Avoid_: checksum, hash, digest, signature

**Backup store**:
The app-owned private area that holds every **Backup copy**, ready for a future cloud upload. Nothing outside the app can see or change it.
_Avoid_: backup folder, staging area, destination, cache

**Outcome**:
The recorded result for one **Source file** within a **Batch**, including the reason when it did not end in a **Backup copy**.
_Avoid_: status, result, log entry

### Outcomes

Every **Source file** in a finished or abandoned **Batch** has exactly one of these:

**Backed up**:
A **Backup copy** of the file exists in the **Backup store**.

**Failed**:
The app tried the file and could not produce a **Backup copy** because of the file or the device. Always carries a **Failure reason**; nothing is kept.
_Avoid_: error, broken

**Interrupted**:
The app stopped while this file was being copied. Nothing is kept. Says nothing about the file itself.
_Avoid_: failed, cancelled, aborted

**Not started**:
The app stopped before this file's turn came. Nothing was read.
_Avoid_: skipped, pending, cancelled

**Failure reason**:
The cause recorded with a **Failed** **Outcome**, phrased so the user can tell what is wrong with the file or the device. One of:
- **Source no longer available**: the file was deleted, moved, or access to it was withdrawn after it was picked.
- **Couldn't read source**: reading started but broke off.
- **Not enough space**: the device cannot hold the **Backup copy**.
- **Copy didn't match source**: the amount or content read and stored disagree, so no **Backup copy** could be confirmed.
- **Unexpected error**: anything else, with its technical detail kept.
_Avoid_: error message, exception

### Progress

While a **Batch** is running, each **Source file** is **Waiting** (its turn has not come) or **Copying**. These are not **Outcomes**: once the app stops, a file still **Waiting** becomes **Not started** and one still **Copying** becomes **Interrupted**.

## Flagged ambiguities

- "Atomicity" means **per file**: each **Source file** either ends as a **Backup copy** or leaves nothing behind. A **Batch** is never all-or-nothing; one file failing does not affect the others.
- A file name does not identify a **Source file**. Two **Source files** in one **Batch** may share a name, and the same file backed up in two **Batches** gives two separate **Backup copies**. Nothing is ever replaced and the user is never asked to choose.
- "Backup" on its own is ambiguous (the action, the **Batch**, or the **Backup copy**). Use the specific term.

## Example dialogue

> **Dev:** The user picked five files and two failed. Is the backup failed?
> **Domain expert:** There's no single "backup" to fail. The **Batch** has five **Outcomes**: three **Source files** became **Backup copies**, two didn't, and each of those two says why.
> **Dev:** One of the failed ones got halfway. Do we keep the half?
> **Domain expert:** No. Half a file isn't a **Backup copy**, so nothing is kept for it.
> **Dev:** And if the app is killed while copying the fourth file?
> **Domain expert:** The fourth is **Interrupted** and the fifth is **Not started**. Neither is **Failed**: nothing is wrong with those files, the app just stopped. **Failed** is only for the file or the device getting in the way, and it always has a **Failure reason**.
