@echo off
chcp 65001 > nul
echo ========================================
echo   =€ 0?CA: Zoomos v4
echo ========================================
echo.

REM 5@5E>48< 2 48@5:B>@8N ?@>5:B0
cd /d "%~dp0"

echo  @>25@:0 Java...
java -version > nul 2>&1
if %ERRORLEVEL% NEQ 0 (
    echo L Java =5 =0945=0! #AB0=>28B5 Java 17+
    pause
    exit /b 1
)

echo  @>25@:0 JAR D09;0...
if not exist "target\file-processing-app-1.0-SNAPSHOT.jar" (
    echo L JAR D09; =5 =0945=!
    echo 0?CAB8B5 A=0G0;0: mvn clean package
    pause
    exit /b 1
)

echo  @>25@:0 ?>@B0 8080...
netstat -an | findstr ":8080" > nul
if %ERRORLEVEL% EQU 0 (
    echo    >@B 8080 C65 70=OB!
    echo >7<>6=>, ?@8;>65=85 C65 70?CI5=>.
    echo @>25@LB5: http://localhost:8080
    pause
)

echo.
echo <¯ K15@8B5 @568< 70?CA:0:
echo   1 - !B0=40@B=K9 @568<
echo   2 - "8E89 @568< (silent)
echo   3 - >4@>1=K9 @568< (verbose)
echo   4 - ! C25;8G5==>9 ?0<OBLN (2GB)
echo.

set /p choice="0H 2K1>@ (1-4): "

if "%choice%"=="1" (
    echo =€ 0?CA: 2 AB0=40@B=>< @568<5...
    java -jar target\file-processing-app-1.0-SNAPSHOT.jar
) else if "%choice%"=="2" (
    echo = 0?CA: 2 B8E>< @568<5...
    java -jar target\file-processing-app-1.0-SNAPSHOT.jar --spring.profiles.active=silent
) else if "%choice%"=="3" (
    echo =Ý 0?CA: A ?>4@>1=K< ;>38@>20=85<...
    java -jar target\file-processing-app-1.0-SNAPSHOT.jar --spring.profiles.active=verbose
) else if "%choice%"=="4" (
    echo >à 0?CA: A C25;8G5==>9 ?0<OBLN (2GB)...
    java -Xmx2G -Xms512M -jar target\file-processing-app-1.0-SNAPSHOT.jar
) else (
    echo L 525@=K9 2K1>@! 0?CA: 2 AB0=40@B=>< @568<5...
    java -jar target\file-processing-app-1.0-SNAPSHOT.jar
)

echo.
echo  @8;>65=85 7025@H8;> @01>BC.
pause