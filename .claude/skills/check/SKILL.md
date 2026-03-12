---
name: check
description: "Pre-commit health check: compile, changed files, docs reminder, basic security scan."
disable-model-invocation: true
context: fork
agent: general-purpose
allowed-tools: Bash, Read, Glob, Grep
---

Отвечай на русском языке.

Run pre-commit health check for Zoomos v4.

## 1. Compile check
`cd /e/workspace/zoomos_v4 && mvn compile -q 2>&1`
Report: OK Compiled | Errors: {list}

## 2. Changed files
`cd /e/workspace/zoomos_v4 && git diff --name-only HEAD && git status --short`
Group by type: Java | SQL | HTML | Properties

## 3. Docs reminder (mandatory per CLAUDE.md)
If any zoomos/* files changed → "Update docs/zoomos-check.md"
If any V*.sql created → "Add to CLAUDE.md Recent Changes section"
If new controller/service → "Check CLAUDE.md Package Structure"

## 4. Quick security scan
Check changed files for:
- Hardcoded secrets (password, api-key, token in strings)
- SQL string concatenation (potential injection)
- Missing @Transactional where business logic modifies multiple entities

## 5. Summary
READY TO COMMIT | ISSUES FOUND (list issues)
