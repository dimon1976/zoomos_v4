---
name: server
description: "Manage the Zoomos v4 development server. Checks port 8081 status, starts/stops/restarts the Spring Boot app."
disable-model-invocation: true
argument-hint: "[start|stop|restart|status]"
allowed-tools: Bash
---

Отвечай на русском языке.

Manage Zoomos v4 development server (Spring Boot 3.2.12, port 8081).

Current server status: !`netstat -ano 2>/dev/null | findstr ":8081" | findstr "LISTENING" && echo "RUNNING" || echo "STOPPED"`

## Commands

### status
Check: `netstat -ano | findstr :8081`
Report PID if found, or "Server stopped".

### stop
1. `netstat -ano | findstr :8081` → get PID
2. `taskkill /F /PID {pid}`
3. Verify port is free.

### start
1. Check if already running → warn if yes
2. `cd /e/workspace/zoomos_v4 && mvn spring-boot:run -Dspring-boot.run.profiles=silent`
3. Run in background. Inform: "Starting... available at http://localhost:8081"
4. Key URLs: /zoomos | /handbook | /maintenance | /utils | /statistics/setup | /clients

### restart
Stop → Start (sequential).

Always use profile=silent (per CLAUDE.md).
