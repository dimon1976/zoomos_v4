#!/usr/bin/env python3
"""
PreToolUse hook: блокирует опасные операции в Zoomos v4.

Блокирует:
- taskkill /IM java.exe (убивает все JVM в системе!)
- rm -rf на критических путях
- прямой доступ к .env файлам
"""
import json
import sys
import re

try:
    data = json.load(sys.stdin)
except Exception:
    sys.exit(0)

tool = data.get("tool_name", "")
tool_input = data.get("tool_input", {})

if tool == "Bash":
    cmd = tool_input.get("command", "")

    # КРИТИЧНО: taskkill /IM java.exe убивает все JVM в системе
    if re.search(r"taskkill.*?/IM\s+java\.exe", cmd, re.IGNORECASE):
        print(json.dumps({
            "decision": "block",
            "reason": (
                "ЗАПРЕЩЕНО: 'taskkill /IM java.exe' убивает ВСЕ JVM в системе!\n"
                "Правильный способ:\n"
                "  1. netstat -ano | findstr :8081\n"
                "  2. taskkill /F /PID <конкретный_PID>"
            )
        }))
        sys.exit(0)

    # Опасные rm паттерны на критических путях
    if re.search(r"rm\s+(-\w*[rf]\w*\s+|--recursive|--force)", cmd):
        if re.search(r"(~|\.\.|/\s*$|\*\s*$|/e/workspace/zoomos_v4/?$|/home|/root)", cmd):
            print(json.dumps({
                "decision": "block",
                "reason": (
                    "Опасная рекурсивная команда rm на критическом пути обнаружена.\n"
                    "Укажи конкретные файлы/директории для удаления."
                )
            }))
            sys.exit(0)

    # Доступ к .env файлам (кроме .env.sample, .env.example)
    if re.search(r"(cat|echo|cp|mv|type)\s+.*\.env\b(?!\.sample)(?!\.example)", cmd):
        print(json.dumps({
            "decision": "block",
            "reason": (
                "Прямой доступ к .env файлам заблокирован.\n"
                "Конфигурация проекта хранится в src/main/resources/application.properties"
            )
        }))
        sys.exit(0)

# Разрешить всё остальное
sys.exit(0)
