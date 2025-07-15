@echo off
echo ========================================
echo PayrollDesktop Windows Installer Builder
echo ========================================
echo.

REM Set environment variables (override these before running if needed)
if not defined JAVA_HOME set "JAVA_HOME=C:\Program Files\Java\jdk-24"
set "PATH=%JAVA_HOME%\bin;%PATH%"

REM Determine Gradle command
set "SCRIPT_DIR=%~dp0"
set "GRADLEW=%SCRIPT_DIR%gradlew.bat"
if exist "%GRADLEW%" (
    set "GRADLE_CMD=%GRADLEW%"
) else if defined GRADLE_HOME (
    set "GRADLE_CMD=%GRADLE_HOME%\bin\gradle.bat"
) else (
    set "GRADLE_CMD=gradle"
)

REM Extract project version from build.gradle
for /f "tokens=2" %%v in ('findstr /B "version =" "%SCRIPT_DIR%build.gradle"') do set "PROJECT_VERSION=%%v"
set "PROJECT_VERSION=%PROJECT_VERSION:'=%"

REM Verify required tools exist
echo Checking required tools...
echo.

REM Check Java
if not exist "%JAVA_HOME%\bin\java.exe" (
    echo ERROR: Java not found at %JAVA_HOME%
    echo Please ensure JDK-24 is installed correctly.
    goto :error
)

REM Check jpackage
if not exist "%JAVA_HOME%\bin\jpackage.exe" (
    echo ERROR: jpackage not found in JDK-24
    echo Please ensure you have JDK-24 installed correctly.
    goto :error
)

REM Check Gradle
where "%GRADLE_CMD%" >nul 2>&1
if %ERRORLEVEL% NEQ 0 (
    echo ERROR: Gradle not found. Please install Gradle or include gradlew in this directory.
    goto :error
)

REM Verify Java version
echo Checking Java version...
"%JAVA_HOME%\bin\java" -version
echo.

REM Verify Gradle version
echo Checking Gradle version...
"%GRADLE_CMD%" --version >nul
if %ERRORLEVEL% NEQ 0 goto :error
echo.

REM Clean previous builds
echo ========================================
echo Step 1: Cleaning previous builds...
echo ========================================
call "%GRADLE_CMD%" clean --no-daemon
if %ERRORLEVEL% NEQ 0 goto :error
echo.

REM Download dependencies
echo ========================================
echo Step 2: Downloading dependencies...
echo ========================================
call "%GRADLE_CMD%" dependencies --no-daemon >nul
if %ERRORLEVEL% NEQ 0 goto :error
echo.

REM Compile the project
echo ========================================
echo Step 3: Compiling project...
echo ========================================
call "%GRADLE_CMD%" classes --no-daemon
if %ERRORLEVEL% NEQ 0 goto :error
echo.

REM Run tests (optional - can be skipped for faster builds)
echo ========================================
echo Step 4: Running tests...
echo ========================================
call "%GRADLE_CMD%" test --no-daemon
if %ERRORLEVEL% NEQ 0 (
    echo WARNING: Tests failed. Continue anyway? [Y/N]
    set /p continue=
    if /i not "%continue%"=="Y" goto :error
)
echo.

REM Package JAR with dependencies
echo ========================================
echo Step 5: Creating JAR with dependencies...
echo ========================================
call "%GRADLE_CMD%" fatJar --no-daemon -x test
if %ERRORLEVEL% NEQ 0 goto :error
echo.

REM Build the installer using Gradle packageExe
echo ========================================
echo Step 6: Building Windows installer...
echo ========================================
call "%GRADLE_CMD%" packageExe --no-daemon
if %ERRORLEVEL% NEQ 0 goto :error
echo.

REM Success
echo ========================================
echo BUILD SUCCESSFUL!
echo ========================================
echo.
echo The installer has been created in: build\installer\
echo File: PayrollDesktop-%PROJECT_VERSION%.exe
echo.
echo Opening installer directory...
start "" "build\installer"
echo.
echo You can now distribute the .exe file to install PayrollDesktop.
echo.
goto :end

:error
echo.
echo ========================================
echo BUILD FAILED!
echo ========================================
echo Please fix the errors above and try again.
echo.

:end
pause