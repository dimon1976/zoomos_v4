#!/usr/bin/env python3
"""
SessionStart hook: загружает git-контекст в начало сессии.
Выводит текущую ветку, количество изменённых файлов и последний коммит.
"""
import json
import sys
import subprocess

try:
    data = json.load(sys.stdin)
except Exception:
    data = {}

import os
PROJECT_DIR = os.environ.get("CLAUDE_PROJECT_DIR", os.getcwd())


def run(cmd):
    try:
        return subprocess.check_output(
            cmd, stderr=subprocess.DEVNULL, cwd=PROJECT_DIR
        ).decode("utf-8", errors="replace").strip()
    except Exception:
        return ""


branch = run(["git", "branch", "--show-current"]) or "unknown"
status_output = run(["git", "status", "--short"])
changed_count = len([l for l in status_output.split("\n") if l.strip()]) if status_output else 0
last_commit = run(["git", "log", "--oneline", "-1"])

parts = [f"Zoomos v4 | ветка: {branch}"]
if changed_count > 0:
    parts.append(f"{changed_count} незакоммиченных файлов")
if last_commit:
    parts.append(f"последний коммит: {last_commit[:70]}")

print(json.dumps({"context": " | ".join(parts)}))
sys.exit(0)
