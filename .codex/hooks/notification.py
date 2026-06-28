#!/usr/bin/env python3
"""
Notification hook: Windows звуковые уведомления через winsound.

Использование (через settings.json):
  python .claude/hooks/notification.py stop
  python .claude/hooks/notification.py subagent_stop
  python .claude/hooks/notification.py session_start
  python .claude/hooks/notification.py error

Звуки (частота Hz, длительность мс):
  session_start  — низкий тон, старт работы
  stop           — высокий тон, Claude завершил задачу
  subagent_stop  — средний тон, субагент завершил
  error          — низкий тон, что-то пошло не так
"""
import sys
import json

# Обязательно читаем stdin (хуки всегда передают JSON)
try:
    data = json.load(sys.stdin)
except Exception:
    data = {}

event = sys.argv[1] if len(sys.argv) > 1 else "stop"

SOUNDS = {
    "session_start": [(523, 80), (659, 80)],          # C5, E5 — мелодичный старт
    "stop":          [(880, 120), (1047, 200)],        # A5, C6 — завершение задачи
    "subagent_stop": [(659, 100)],                     # E5 — тихое завершение субагента
    "error":         [(330, 300), (294, 400)],         # E4, D4 — нисходящий тон
}

try:
    import winsound
    beeps = SOUNDS.get(event, [(880, 200)])
    for freq, duration in beeps:
        winsound.Beep(freq, duration)
except ImportError:
    # Не Windows или winsound недоступен — тихо игнорируем
    pass
except Exception:
    pass

sys.exit(0)
