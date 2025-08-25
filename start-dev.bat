@echo off
chcp 65001 > nul
echo ========================================
echo   =à 0?CA: Zoomos v4 ( 568< @07@01>B:8)
echo ========================================
echo.

REM 5@5E>48< 2 48@5:B>@8N ?@>5:B0  
cd /d "%~dp0"

echo  0?CA: A =0AB@>9:0<8 4;O @07@01>B:8:
echo   - >4@>1=>5 ;>38@>20=85 (DEBUG)
echo   - DevTools 2:;NG5=K (02B>?5@5703@C7:0)
echo   - B:;NG5=> :5H8@>20=85 Thymeleaf
echo   - SQL 70?@>AK 2 ;>30E
echo.

java -jar target\file-processing-app-1.0-SNAPSHOT.jar ^
     --spring.profiles.active=verbose ^
     --spring.devtools.restart.enabled=true ^
     --spring.devtools.livereload.enabled=true ^
     --spring.thymeleaf.cache=false

echo.
echo   568< @07@01>B:8 7025@H5=.
pause