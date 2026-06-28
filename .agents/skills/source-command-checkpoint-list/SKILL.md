---
name: "source-command-checkpoint-list"
description: "List all Codex checkpoints with time and description"
---

# source-command-checkpoint-list

Use this skill when the user asks to run the migrated source command `checkpoint-list`.

## Command Template

## List Codex checkpoints

Display all checkpoints created by Codex during this and previous sessions.

## Task

List all Codex checkpoints. Steps:

1. Run `git stash list` to get all stashes
2. Filter for lines containing "Codex-checkpoint:" using grep or by parsing the output
3. For each matching stash line (format: `stash@{n}: On branch: message`):
   - Extract the stash number from `stash@{n}`
   - Extract the branch name after "On "
   - Extract the checkpoint description after "Codex-checkpoint: "
   - Use `git log -1 --format="%ai" stash@{n}` to get the timestamp for each stash

4. Format and display as:
   ```
   Codex Checkpoints:
   [n] YYYY-MM-DD HH:MM:SS - Description (branch)
   ```
   Where n is the stash index number

5. If `git stash list | grep "Codex-checkpoint:"` returns nothing, display:
   "No checkpoints found. Use /checkpoint [description] to create one."

Example: A stash line like `stash@{2}: On main: Codex-checkpoint: before auth refactor`
Should display as: `[2] 2025-01-15 10:30:45 - before auth refactor (main)`
