# Server Management

Управление Spring Boot сервером на порту 8081.

## КРИТИЧЕСКИ ВАЖНО

**НИКОГДА** не использовать `run_in_background: true` для запуска сервера — процессы из отдельной задачи невидимы bash и их нельзя убить через `kill`.

Всегда запускать через `&` в обычном Bash tool (без `run_in_background`), чтобы bash видел PID и мог его убить.

## Алгоритм

### stop

1. `kill -9 $(cat /tmp/server.pid)` — убить по сохранённому PID
2. Проверить: `netstat -ano | findstr :8081`
3. Если порт всё ещё занят — `cmd /c "taskkill /F /PID <PID>"` (через cmd /c — bash ломает флаги /F /PID)

### start

1. Проверить что порт свободен: `netstat -ano | findstr :8081`
2. Если занят — выполнить **stop** сначала
3. Запустить (БЕЗ run_in_background):

   ```bash
   mvn spring-boot:run -Dspring-boot.run.profiles=silent > C:/Temp/srv.log 2>&1 &
   echo $! > /tmp/server.pid
   ```

4. Подождать 15 сек: `sleep 15 && tail -5 C:/Temp/srv.log`

### restart

1. Выполнить **stop**
2. Выполнить **start**

### status

`netstat -ano | findstr :8081`

## Почему kill работает только для процессов текущей bash-сессии

- `run_in_background: true` → отдельная Windows-сессия → bash не видит → `kill` и `tasklist` не работают
- `&` в Bash tool → та же bash-сессия → `kill -9 $(cat /tmp/server.pid)` работает

## Arguments

`$ARGUMENTS` — одно из: `start`, `stop`, `restart`, `status`
