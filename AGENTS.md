# RuntimeRocket Agent Rules

These rules apply to every agent working in this repository.

## Apply changes directly to this repo

- Write all implementation, design docs, plugin sources, tests, and configuration **directly into this working tree** (`F:\grok\RuntimeRocket`).
- Do **not** use isolated git worktrees, shadow checkouts, or temp-only copies as the destination for deliverables.
- Do **not** leave finished work only under `%TEMP%`, `/tmp`, or a scratch directory. Scratch files may be used while drafting; copy the approved result into this repo before finishing.
- Do **not** set `isolation=worktree` on subagents for this project. Use the current workspace.
- After a design or planning loop, persist the approved design document under `docs/` in this repo (for example `docs/design.md`).
- When implementing a PR plan, land files on the current branch in this checkout so the user can build, run, and commit immediately.

## Project intent

RuntimeRocket is a JRebel-style IntelliJ plugin: reload compiled JVM changes into a running application without a restart, preserving application state so developers stay in flow.
