# Prepare for Backup: Project Instructions

> Glossary: [CONTEXT.md](CONTEXT.md) · Behaviour and architecture: [SPEC.md](SPEC.md) · Decisions: [docs/adr/](docs/adr/)
> README: written by the submission-writer agent (.claude/agents/submission-writer.md), in Pedro's voice

## Workflow

- Read CONTEXT.md, SPEC.md and the ADRs before starting a task.
- SPEC.md is the source of truth for behaviour. A change in behaviour updates SPEC.md first, with Pedro's agreement, then the code.
- Any decision not covered by CONTEXT.md, SPEC.md or an ADR → stop and resolve it with Pedro before writing code.
- `/plan` scopes and creates GitHub issues; to work on an existing issue, skip it and start from the issue's acceptance criteria.
- Before starting an issue, sync `main` (`git checkout main && git pull`), then branch `feat/issue-N-short-description` from it. The previous PR is usually merged remotely while local `main` is stale.
- Commit with `/commit`, in this order: `docs:` (CONTEXT.md, SPEC.md) first, then `feat:`, then `refactor:` / `fix:` for review changes. The PR body says `Closes #N`.
- No attribution anywhere: no `Co-Authored-By` lines in commits, no "Generated with Claude Code" or similar in PR bodies.
- Handle review comments with `/address-review`: a verdict per comment, pushback only with a concrete reason, fix what is agreed, reply on every thread.

## Language

- Use the glossary terms in code, UI text, tests and commits: **Batch**, **Source file**, **Backup copy**, **Backup store**, **Fingerprint**, **Outcome**, **Failure reason**. Never their _Avoid_ aliases.
- A new domain term is added to CONTEXT.md when it is agreed, not later.

## Code style

- KISS. No abstractions beyond the task. No speculative features.
- Code reads like prose: names are precise, structure is self-evident.
- Comments only for non-obvious invariants or workarounds. Never describe what the code does.
- One-line comments use `//`, never `/** */`. No doc comments on interface methods: the implementation makes the behaviour clear.
- No single-value wrapper types (e.g. a value class around a `String`) unless they carry operations or rules of their own.
- Prefer one flat sealed type over a layered hierarchy (e.g. `FileState` holds all six states; there is no separate `Outcome` type).
- Parameter names match their type (`fileState: FileState`).
- No `!!`. Resolve a nullable where it arises: build the value so it isn't nullable (`folderOf(batchId, batchFileId)` instead of `file.parentFile!!`), or handle the `null` case. Tests included.
- Units go in the name, not in a comment: `createdAtEpochMs`, `maxBytes`, `BUFFER_BYTES`.
- Before adding a table, entity or carrier type, check it can't be a column or an existing type.
- Split a long method into named private steps, so the public method reads as the sequence of what it does.
- Surgical changes only. Every changed line traces to the current task.

## Architecture

- Layers as packages: `ui` → `domain` ← `data`, wired in `di` with Koin. See SPEC.md for the tree.
- `domain` has no `android.*` or `androidx.*` imports.
- Add a use case only when an operation has logic of its own. The ViewModel reads `BatchRepository` directly for plain reads.
- Only `BatchDatabaseManager` touches the DAO (ADR-0003). Each of its operations enforces a rule or is a read the system needs; a method that only forwards one DAO call is not allowed.
- Repository implementations map between data and domain; rules live in use cases or the manager.
- Keep repository implementations to orchestration. Conversions (data ↔ domain, exception → **Failure reason**, display name → file name) are top-level extension functions in `data/mappers/`; generic helpers (`File`, `String`) are extension functions in `data/extensions/`. Both stay inside `data/` until another layer needs them.
- **Batches** run only in the app-wide scope provided by Koin (ADR-0002), never in `viewModelScope`.

### Folder structure

Inside a folder, files of the same kind (models, repositories, managers, use cases, entities…) move into their own subfolder only when there are two or more of that kind and the folder holds more than one kind. A kind with a single file stays at the folder's root; a folder holding a single kind stays flat. Classify files by their real kind: only Room `@Entity` classes are entities; a partial-update class or relation holder is not. Re-check this whenever a file is added.

## Data integrity

- Never write a **Backup copy** under its final name: write the `.part` file, `fsync`, re-read and compare the **Fingerprint**, then rename.
- Never record **Backed up** before that rename has succeeded.
- On failure, delete the file's `<batchId>/<fileId>/` folder (which holds only its `.part` file) before recording the **Outcome**.
- Inside the per-file loop, catch everything except `CancellationException`, map it to a **Failure reason**, record it, and move on.
- Never modify, move or delete a **Source file**.

## UI

- Compose, Material 3. One screen, one `BackupUiState` exposed as a `StateFlow`.
- Composables are stateless and receive only what they render.

## Testing

- Test-first for `RunBatch`, `BackupCopyRepositoryImpl` and `BatchDatabaseManager`. Tests ship in the same change as the code.
- Hand-written fakes, no mocking library. Room is tested with Robolectric and an in-memory database.
- `BackupCopyRepositoryImpl` tests use real files in a temporary folder.
- A refactor keeps the existing tests unchanged and green; if a test has to change, the behaviour changed.
- Don't assert exception identity across `withContext`: coroutine stack-trace recovery may copy the exception. Compare type and message.
- Do not test composables.
- **The test plan is a living list.** For every class created or changed in a task, check whether it gained a branch, a rule, or a way to produce an **Outcome** or **Failure reason**. If so, it needs tests, whatever the plan said. If a planned class turned out trivial, say so instead of writing ceremonial tests. Update the test table in SPEC.md in the same change.

## Devices

- Run on whatever is connected, physical device or emulator. Pin `adb` commands to one device with `ANDROID_SERIAL` when more than one is attached.
- Never run `scripts/fill-storage.sh` on a physical device.
- Connected (instrumented) tests uninstall the app and wipe its data; don't run them after preparing demo data.
