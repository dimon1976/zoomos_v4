@echo off
echo ========================================
echo Запуск теста OperationDeletionService
echo ========================================
echo.

REM Переходим в директорию проекта
cd /d "%~dp0"

REM Проверяем наличие Maven
where mvn >nul 2>&1
if %ERRORLEVEL% EQU 0 (
    echo Maven найден, запускаем тест через Maven...
    mvn test -Dtest=OperationDeletionServiceTest
    goto :end
)

REM Проверяем наличие Maven Wrapper
if exist mvnw.cmd (
    echo Maven Wrapper найден, запускаем тест...
    mvnw.cmd test -Dtest=OperationDeletionServiceTest
    goto :end
)

REM Проверяем наличие Gradle
where gradle >nul 2>&1
if %ERRORLEVEL% EQU 0 (
    echo Gradle найден, запускаем тест через Gradle...
    gradle test --tests OperationDeletionServiceTest
    goto :end
)

REM Если ничего не найдено
echo.
echo ❌ Не найден ни Maven, ни Gradle для запуска тестов!
echo.
echo Для запуска теста установите один из:
echo   1. Maven: https://maven.apache.org/download.cgi
echo   2. Gradle: https://gradle.org/install/
echo.
echo Или откройте проект в IDE (IntelliJ IDEA/Eclipse) и запустите тест оттуда.
echo.
echo Файл теста: src\test\java\com\java\service\operations\OperationDeletionServiceTest.java
echo.

:end
pause