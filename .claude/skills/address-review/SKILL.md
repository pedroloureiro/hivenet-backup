---
name: address-review
description: Address Pedro's review comments on a pull request as the engineer who wrote the code. Weigh each comment, push back where it is unreasonable, ambiguous or wrong, fix what you agree with, reply on every thread and push. Use whenever Pedro says "review comments", "address the review", "I left comments on the PR", "go through the PR feedback" or names a PR with review feedback.
argument-hint: "[PR number, default: the current branch's PR]"
---

# Address review comments

You are the implementer of this PR, not a neutral reviewer. You know why the code looks the way it does: the issue, CONTEXT.md, SPEC.md, the ADRs, CLAUDE.md and the decisions Pedro made while it was built. Weigh every comment against that context. The goal is better code, not winning the thread and not just agreeing.

## 1. Gather

- PR: `$ARGUMENTS`, else `gh pr view --json number` on the current branch.
- Pull every comment. Pedro often leaves them in a **pending** review, which the normal listing doesn't show:
  - `gh api repos/{owner}/{repo}/pulls/N/reviews` (note every review id, including `PENDING`)
  - `gh api repos/{owner}/{repo}/pulls/N/comments`
  - `gh api repos/{owner}/{repo}/pulls/N/reviews/<id>/comments` for each pending review
  - `gh pr view N --comments` for conversation-level comments
- Skip threads you already answered, unless Pedro replied after you.
- Map each comment to the code it anchors to: read the file at the commented line, because line numbers drift after pushes.

## 2. Weigh each comment

Give each comment exactly one verdict:

| Verdict | When | Then |
|---|---|---|
| **Agree** | It makes the code clearer, safer or more consistent with CLAUDE.md / SPEC | Fix it fully, including what it implies elsewhere |
| **Partly agree** | The intent is right but part of the ask would hurt (breaks a rule, hides an invariant, adds a layer for one caller) | Do the good part; explain the part you left, with the reason |
| **Disagree** | It contradicts SPEC, an ADR or a CLAUDE.md rule, would change behaviour without agreement, or makes the code worse | Don't change code; reply with the concrete reason and what would change your mind |
| **Ambiguous** | It reads two ways that lead to materially different code | If one reading clearly fits the codebase, take it and say so in the reply. Otherwise ask Pedro in chat before coding |
| **Question** | It asks why, without asking for a change | Answer in the thread; no code change |

Rules for pushing back:

- **Push back only with a concrete reason** tied to this codebase: a SPEC line, an ADR, a CLAUDE.md rule, a test that would break, a bug the change would introduce, or a cost (a new layer, a single-use abstraction, a hidden invariant). "I prefer it this way" is not a reason.
- **Never push back for the sake of it.** Style and structure preferences that don't break a rule are Pedro's call: agree and do them.
- **Never agree just to close the thread.** If a change would make the code worse, say so once, clearly, and propose an alternative when there is one.
- When Pedro offers options ("a shared or common or extensions folder"), picking one of his options is not pushback. Say which one and why.
- Anything that changes behaviour or a decision not covered by SPEC/ADRs: stop and agree it with Pedro in chat first (CLAUDE.md workflow). Update SPEC.md first, then code.

Before touching code, write the verdicts in chat: one line per comment with its verdict and the gist of the reasoning. Then carry on. Don't wait for confirmation unless something is **Ambiguous** or changes behaviour.

## 3. Implement

- Follow CLAUDE.md: KISS, glossary terms, surgical changes, folder rule (re-check it for every file you add or move), no `/** */`, comments only for non-obvious invariants.
- Refactors must keep behaviour: the existing tests stay unchanged and green. If a test has to change, that is a behaviour change: say so.
- Apply the living test-plan rule: a class that gained a branch or rule needs tests; a moved one keeps its coverage.
- If the architecture tree, a rule or the test table in SPEC.md changes, update SPEC.md in the same change.
- Run `./gradlew :app:testDebugUnitTest :app:lintDebug`, then report test counts and any new lint findings.

## 4. Commit and push

- Commit with `/commit` conventions: `docs:` (SPEC.md) first, then `refactor:` / `fix:` / `test:`, each `(#issue)`. No attribution lines.
- Push the branch.

## 5. Reply on every thread

- Reply in-thread: `gh api repos/{owner}/{repo}/pulls/N/comments/<comment id>/replies -f body=...`.
- Lead with the verdict in plain words ("Agreed, done in <sha>." / "Half agreed." / "I'd keep this as is, because…").
- Say what changed and where (file, function, commit sha). For pushback, give the concrete reason in two or three sentences, and say what would change your mind.
- Use glossary terms in **bold** where they matter. No attribution or tagline.
- Never resolve threads: Pedro resolves them.

## 6. Report back in chat

A short table: comment → verdict → what was done or why not, then commits, test and lint results, and anything waiting on Pedro.
