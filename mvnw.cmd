@echo off
REM Maven Wrapper для Windows
REM Автоматически использует Maven из IntelliJ IDEA

set MAVEN_CMD="C:\Program Files\JetBrains\IntelliJ IDEA 2023.1.1\plugins\maven\lib\maven3\bin\mvn.cmd"

if exist %MAVEN_CMD% (
    echo Используется Maven из IntelliJ IDEA...
    %MAVEN_CMD% %*
) else (
    echo Попытка использовать системный Maven...
    mvn %*
)