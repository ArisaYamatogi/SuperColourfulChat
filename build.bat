@echo off
rem Reproducible build without Maven/Gradle (mirror of build.sh):
rem   1. download compileOnly deps into libs\ (never shipped in the jar)
rem   2. compile with JDK17 --release 8 against spigot-api 1.16.5 + EssentialsX 2.19.4 (delivery)
rem   3. compile the same sources with JDK21 against the latest readable paper-api + EssentialsX 2.22.0 (upper bound proof)
rem   4. package build\SuperColourfulChat.jar and verify major version 52
setlocal EnableExtensions EnableDelayedExpansion
cd /d "%~dp0"

set "JDK_LOWER=D:\java17_zulu"
set "JDK_UPPER=D:\JAVA21"
set "CENTRAL=https://repo.maven.apache.org/maven2"
set "PAPERMC=https://repo.papermc.io/repository/maven-public"
set "SPIGOTMC=https://hub.spigotmc.org/nexus/content/repositories/snapshots"
set "ESSX=https://repo.essentialsx.net/releases"
set "ESSX_SNAP=https://repo.essentialsx.net/snapshots"
set "ADVENTURE_VER=4.26.1"
set "EXAMINATION_VER=1.3.0"
set "BUNGEE_UPPER_VER=1.21-R0.1"

if not exist "%JDK_LOWER%\bin\javac.exe" (echo [build] missing JDK: %JDK_LOWER% & exit /b 1)
if not exist "%JDK_UPPER%\bin\javac.exe" (echo [build] missing JDK: %JDK_UPPER% & exit /b 1)

if not exist libs mkdir libs
if not exist build mkdir build

echo [build] == lower bound dependencies ==
call :fetch "%CENTRAL%/net/md-5/bungeecord-chat/1.16-R0.4/bungeecord-chat-1.16-R0.4.jar" "libs\bungeecord-chat-1.16-R0.4.jar" || exit /b 1
call :snapshot "%PAPERMC%" "org/spigotmc" "spigot-api" "1.16.5-R0.1-SNAPSHOT" "libs\spigot-api-1.16.5-R0.1-SNAPSHOT.jar" "%SPIGOTMC%" || exit /b 1
call :ess_lower || exit /b 1

echo [build] == upper bound dependencies ==
call :fetch "%CENTRAL%/net/kyori/adventure-api/%ADVENTURE_VER%/adventure-api-%ADVENTURE_VER%.jar" "libs\adventure-api-%ADVENTURE_VER%.jar" || exit /b 1
call :fetch "%CENTRAL%/net/kyori/adventure-key/%ADVENTURE_VER%/adventure-key-%ADVENTURE_VER%.jar" "libs\adventure-key-%ADVENTURE_VER%.jar" || exit /b 1
call :fetch "%CENTRAL%/net/kyori/adventure-text-serializer-legacy/%ADVENTURE_VER%/adventure-text-serializer-legacy-%ADVENTURE_VER%.jar" "libs\adventure-text-serializer-legacy-%ADVENTURE_VER%.jar" || exit /b 1
call :fetch "%CENTRAL%/net/kyori/examination-api/%EXAMINATION_VER%/examination-api-%EXAMINATION_VER%.jar" "libs\examination-api-%EXAMINATION_VER%.jar" || exit /b 1
call :fetch "%CENTRAL%/net/md-5/bungeecord-chat/%BUNGEE_UPPER_VER%/bungeecord-chat-%BUNGEE_UPPER_VER%.jar" "libs\bungeecord-chat-%BUNGEE_UPPER_VER%.jar" || exit /b 1
set "UPPER_SIG_CP=libs\adventure-api-%ADVENTURE_VER%.jar;libs\adventure-key-%ADVENTURE_VER%.jar;libs\adventure-text-serializer-legacy-%ADVENTURE_VER%.jar;libs\examination-api-%EXAMINATION_VER%.jar;libs\bungeecord-chat-%BUNGEE_UPPER_VER%.jar"
call :paper_upper || exit /b 1
call :ess_upper || exit /b 1

echo [build] == compile ==
if exist build\sources.txt del build\sources.txt
if exist build\sources-all.txt del build\sources-all.txt
for /r "src\main\java" %%f in (*.java) do (
    set "P=%%f"
    set "P=!P:\=/!"
    >>build\sources-all.txt echo "!P!"
    if /i not "%%~nxf"=="ModernChatListener.java" >>build\sources.txt echo "!P!"
)
if exist build\classes rmdir /s /q build\classes
if exist build\classes-upper rmdir /s /q build\classes-upper
mkdir build\classes
mkdir build\classes-upper

set "LOWER_CP=libs\spigot-api-1.16.5-R0.1-SNAPSHOT.jar;libs\bungeecord-chat-1.16-R0.4.jar;!ESS_LOWER_JAR!"
"%JDK_LOWER%\bin\javac.exe" --release 8 -encoding UTF-8 -cp "!LOWER_CP!" -d build\classes @build\sources.txt
if errorlevel 1 (echo [build] lower compile FAILED & exit /b 1)
echo [build] lower bound compile OK

"%JDK_UPPER%\bin\javac.exe" --release 8 -encoding UTF-8 -cp "!PAPER_JAR!;!UPPER_SIG_CP!;build\classes" -d build\classes src\main\java\com\supercolourfulchat\listener\ModernChatListener.java
if errorlevel 1 (echo [build] modern compile FAILED & exit /b 1)
echo [build] modern listener compile OK

set "UPPER_CP=!PAPER_JAR!;!ESS_UPPER_JAR!;!UPPER_SIG_CP!"
"%JDK_UPPER%\bin\javac.exe" -encoding UTF-8 -cp "!UPPER_CP!" -d build\classes-upper @build\sources-all.txt
if errorlevel 1 (echo [build] upper compile FAILED & exit /b 1)
echo [build] upper bound compile OK

"%JDK_LOWER%\bin\jar.exe" --create --file build\SuperColourfulChat.jar -C build\classes . -C src\main\resources .
if errorlevel 1 (echo [build] jar FAILED & exit /b 1)

echo [build] == verify ==
"%JDK_LOWER%\bin\javap.exe" -verbose -cp build\classes com.supercolourfulchat.SuperColourfulChatPlugin | findstr /c:"major version: 52" >nul
if errorlevel 1 (echo [build] FAIL: main class not major 52 & exit /b 1)
"%JDK_LOWER%\bin\jar.exe" --list --file build\SuperColourfulChat.jar | findstr /c:"plugin.yml" >nul
if errorlevel 1 (echo [build] FAIL: plugin.yml missing & exit /b 1)
echo [build] main class major 52, plugin.yml present
echo [build] DONE: %CD%\build\SuperColourfulChat.jar
exit /b 0

:fetch
set "URL=%~1"
set "DEST=%~2"
if exist "!DEST!" exit /b 0
echo [build] download !URL!
curl -fL --retry 3 --retry-delay 2 --connect-timeout 30 -o "!DEST!.part" "!URL!"
if errorlevel 1 (
    if exist "!DEST!.part" del /q "!DEST!.part"
    exit /b 1
)
move /y "!DEST!.part" "!DEST!" >nul
exit /b 0

:snapshot
set "REPO=%~1"
set "GROUP=%~2"
set "ART=%~3"
set "VER=%~4"
set "DEST=%~5"
set "FALLBACK=%~6"
if exist "!DEST!" exit /b 0
set "BASE=!VER:-SNAPSHOT=!"
curl -fsSL --retry 3 --connect-timeout 30 -o "build\meta-!ART!.xml" "!REPO!/!GROUP!/!ART!/!VER!/maven-metadata.xml"
if not errorlevel 1 (
    set "TS="
    set "BN="
    for /f "tokens=3 delims=<>" %%a in ('findstr /c:"<timestamp>" "build\meta-!ART!.xml"') do if not defined TS set "TS=%%a"
    for /f "tokens=3 delims=<>" %%a in ('findstr /c:"<buildNumber>" "build\meta-!ART!.xml"') do if not defined BN set "BN=%%a"
    if defined TS if defined BN (
        call :fetch "!REPO!/!GROUP!/!ART!/!VER!/!ART!-!BASE!-!TS!-!BN!.jar" "!DEST!"
        if not errorlevel 1 exit /b 0
    )
)
if defined FALLBACK if not "!FALLBACK!"=="" (
    call :snapshot "!FALLBACK!" "!GROUP!" "!ART!" "!VER!" "!DEST!" ""
    exit /b !errorlevel!
)
call :fetch "!REPO!/!GROUP!/!ART!/!VER!/!ART!-!VER!.jar" "!DEST!"
exit /b !errorlevel!

:ess_lower
set "ESS_LOWER_JAR=libs\EssentialsX-2.19.4.jar"
if exist "!ESS_LOWER_JAR!" exit /b 0
call :fetch "%ESSX%/net/essentialsx/EssentialsX/2.19.4/EssentialsX-2.19.4.jar" "!ESS_LOWER_JAR!"
if not errorlevel 1 exit /b 0
curl -fsSL --retry 3 -o "build\ess-meta.xml" "%ESSX%/net/essentialsx/EssentialsX/maven-metadata.xml"
if errorlevel 1 exit /b 1
set "ALT="
for /f "tokens=3 delims=<>" %%a in ('findstr /r /c:"<version>2\.19\." "build\ess-meta.xml"') do set "ALT=%%a"
if not defined ALT for /f "tokens=3 delims=<>" %%a in ('findstr /r /c:"<version>2\.20\." "build\ess-meta.xml"') do set "ALT=%%a"
if not defined ALT (echo [build] no EssentialsX 2.19.x/2.20.x available & exit /b 1)
set "ESS_LOWER_JAR=libs\EssentialsX-!ALT!.jar"
call :fetch "%ESSX%/net/essentialsx/EssentialsX/!ALT!/EssentialsX-!ALT!.jar" "!ESS_LOWER_JAR!"
exit /b !errorlevel!

:ess_upper
set "ESS_UPPER_JAR=libs\EssentialsX-2.22.0.jar"
if exist "!ESS_UPPER_JAR!" exit /b 0
call :fetch "%ESSX%/net/essentialsx/EssentialsX/2.22.0/EssentialsX-2.22.0.jar" "!ESS_UPPER_JAR!"
if not errorlevel 1 exit /b 0
set "ESS_UPPER_JAR=libs\EssentialsX-2.22.0-SNAPSHOT.jar"
call :snapshot "%ESSX_SNAP%" "net/essentialsx" "EssentialsX" "2.22.0-SNAPSHOT" "!ESS_UPPER_JAR!" ""
if not errorlevel 1 exit /b 0
curl -fsSL --retry 3 -o "build\ess-meta.xml" "%ESSX%/net/essentialsx/EssentialsX/maven-metadata.xml"
if errorlevel 1 exit /b 1
set "REL="
for /f "tokens=3 delims=<>" %%a in ('findstr /c:"<release>" "build\ess-meta.xml"') do set "REL=%%a"
if not defined REL (echo [build] cannot resolve EssentialsX upper version & exit /b 1)
set "ESS_UPPER_JAR=libs\EssentialsX-!REL!.jar"
call :fetch "%ESSX%/net/essentialsx/EssentialsX/!REL!/EssentialsX-!REL!.jar" "!ESS_UPPER_JAR!"
exit /b !errorlevel!

:paper_upper
set "PAPER_JAR="
for %%f in (libs\paper-api-*.jar) do (
    if exist "%%f" if not defined PAPER_JAR (
        call :paper_readable "%%f"
        if not errorlevel 1 set "PAPER_JAR=%%f"
    )
)
if defined PAPER_JAR exit /b 0
curl -fsSL --retry 3 -o "build\paper-meta.xml" "%PAPERMC%/io/papermc/paper/paper-api/maven-metadata.xml"
if errorlevel 1 exit /b 1
set "PV="
for /f "tokens=3 delims=<>" %%a in ('findstr /c:"<latest>" "build\paper-meta.xml"') do set "PV=%%a"
if not defined PV for /f "tokens=3 delims=<>" %%a in ('findstr /c:"<version>" "build\paper-meta.xml"') do set "PV=%%a"
if not defined PV (echo [build] cannot resolve paper-api version & exit /b 1)
call :paper_get "!PV!"
if not errorlevel 1 exit /b 0
echo [build] paper-api !PV! needs newer bytecode than %JDK_UPPER%; falling back to latest 1.21.x
set "FV="
for /f "tokens=3 delims=<>" %%a in ('findstr /r /c:"<version>1\.21" "build\paper-meta.xml" ^| findstr /v /c:"rc" /c:"pre" /c:"no-moonrise"') do set "FV=%%a"
if not defined FV (echo [build] no 1.21.x fallback found & exit /b 1)
call :paper_get "!FV!"
exit /b !errorlevel!

:paper_get
set "PV=%~1"
set "PJ=libs\paper-api-!PV!.jar"
echo !PV! | findstr /c:"-SNAPSHOT" >nul
if not errorlevel 1 (
    call :snapshot "%PAPERMC%" "io/papermc/paper" "paper-api" "!PV!" "!PJ!" ""
) else (
    call :fetch "%PAPERMC%/io/papermc/paper/paper-api/!PV!/paper-api-!PV!.jar" "!PJ!"
)
if errorlevel 1 exit /b 1
call :paper_readable "!PJ!"
if errorlevel 1 exit /b 1
set "PAPER_JAR=!PJ!"
exit /b 0

:paper_readable
>build\SccProbe.java echo class SccProbe { org.bukkit.Server server; }
"%JDK_UPPER%\bin\javac.exe" -cp "%~1;!UPPER_SIG_CP!" -d build\probe build\SccProbe.java >nul 2>&1
exit /b !errorlevel!