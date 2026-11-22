# Next steps

Status on 2026-11-22: CONTEXT.md, SPEC.md, CLAUDE.md and ADRs 0001–0003 are approved. There is no git repo, Android project or code yet. Nothing below has started.

## 1. Repository (ask Pedro before each outward-facing step)

1. `git init` in this folder and commit the docs and `.claude/` as the first commit.
2. Create the GitHub repository. The brief asks for a public repo or an archive: confirm with Pedro before creating it public.
3. Create a GitHub Project board for `/plan`, and the labels `data` `ui` `infra` `docs` `wiring` (`domain` too, since SPEC.md has a domain layer: update `.claude/commands/plan.md` to match).

## 2. Scaffold (one `infra` issue)

- `android create empty-activity` into this folder, then strip the template's navigation and sample screen.
- Add Room + KSP (schema export to `app/schemas/`), Koin, Robolectric, kotlinx-coroutines-test. Pin versions; check the build and an empty test run.
- minSdk 26, target and compile SDK 36.

## 3. Issues for `/plan`, in build order

| # | Layer | Issue | Test-first |
|---|---|---|---|
| 1 | infra | Scaffold, dependencies, Koin app-wide scope | |
| 2 | domain | Models and repository interfaces | |
| 3 | data | Room entities, DAO, database, `BatchDatabaseManager` | ✅ Robolectric |
| 4 | data | `BackupCopyRepositoryImpl` (`.part` → SHA-256 → fsync → re-read → rename) | ✅ real temp files |
| 5 | data | `SourceFileRepositoryImpl`, `BatchRepositoryImpl` | |
| 6 | domain | `RunBatch`, `SettleAbandonedBatch` | ✅ fakes |
| 7 | ui | `BackupViewModel`, `BackupScreen` (picker, selection, latest **Batch**) | ViewModel only |
| 8 | ui + wiring | Open a **Backup copy** via `FileProvider`; settle on process start | |
| 9 | infra | `scripts/` seed and failure scripts | |
| 10 | docs | README through the submission-writer agent | |

After every issue, apply the living test-plan rule in CLAUDE.md.

## 4. Before submitting

- Run the whole demo on a real device or emulator: success, each real **Failure reason**, kill mid-**Batch**, reopen, **Open**.
- Fill in the README's time spent and AI assistance with Pedro's own account.

## Time tracking

The brief's 2–4 h includes setup and documentation.

- Planning and research (spec session): about 1 h.
- Implementation started: 2026-11-22, 16:55.
