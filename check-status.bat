@echo off
chcp 65001 > nul
echo ========================================
echo   =Ê @>25@:0 AB0BCA0 Zoomos v4
echo ========================================
echo.

echo  @>25@:0 Java 25@A88:
java -version
echo.

echo  @>25@:0 JAR D09;0:
if exist "target\file-processing-app-1.0-SNAPSHOT.jar" (
    echo  JAR D09; =0945=: target\file-processing-app-1.0-SNAPSHOT.jar
    dir "target\file-processing-app-1.0-SNAPSHOT.jar" | findstr "file-processing"
) else (
    echo L JAR D09; =5 =0945=!
)
echo.

echo  @>25@:0 ?>@B>2:
netstat -an | findstr ":8080"
if %ERRORLEVEL% EQU 0 (
    echo  >@B 8080 70=OB - ?@8;>65=85 <>65B 1KBL 70?CI5=>
) else (
    echo 9  >@B 8080 A2>1>45=
)
echo.

echo  @>25@:0 4>ABC?=>AB8 ?@8;>65=8O:
curl -s -I http://localhost:8080 2>nul | findstr "HTTP"
if %ERRORLEVEL% EQU 0 (
    echo  @8;>65=85 >B25G05B =0 http://localhost:8080
    echo < 51-8=B5@D59A: http://localhost:8080/
) else (
    echo L @8;>65=85 =5 >B25G05B =0 http://localhost:8080
)
echo.

echo  @>25@:0 @01>G8E 48@5:B>@89:
if exist "data" (
    echo  8@5:B>@8O data/ ACI5AB2C5B
    dir data /b
) else (
    echo 9  8@5:B>@8O data/ 1C45B A>740=0 ?@8 ?5@2>< 70?CA:5
)
echo.

echo  @>25@:0 ;>3>2:
if exist "logs" (
    echo  8@5:B>@8O logs/ ACI5AB2C5B
    dir logs /b
) else (
    echo 9  8@5:B>@8O logs/ 1C45B A>740=0 ?@8 ?5@2>< 70?CA:5
)

echo.
echo =Ë >;=0O 8=D>@<0F8O A>1@0=0!
pause