@echo off
echo ========================================
echo PayrollDesktop Windows Installer Builder
echo ========================================
echo.

REM Set environment variables
set JAVA_HOME=C:\Program Files\Java\jdk-24
set PATH_TO_FX=C:\Program Files\Java\javafx-sdk-24.0.1
set MAVEN_HOME=C:\Program Files\Java\apache-maven-3.9.10
set PATH=%JAVA_HOME%\bin;%MAVEN_HOME%\bin;%PATH%

REM Set JavaFX module path for Maven
set MAVEN_OPTS=--module-path="%PATH_TO_FX%\lib" --add-modules=javafx.controls,javafx.fxml

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

REM Check Maven
if not exist "%MAVEN_HOME%\bin\mvn.cmd" (
    echo ERROR: Maven not found at %MAVEN_HOME%
    echo Please ensure Maven is installed correctly.
    goto :error
)

REM Check JavaFX
if not exist "%PATH_TO_FX%\lib\javafx.base.jar" (
    echo ERROR: JavaFX not found at %PATH_TO_FX%
    echo Please ensure JavaFX SDK is installed correctly.
    goto :error
)

REM Verify Java version
echo Checking Java version...
"%JAVA_HOME%\bin\java" -version
echo.

REM Verify Maven version
echo Checking Maven version...
call "%MAVEN_HOME%\bin\mvn" -version
echo.

REM Clean previous builds
echo ========================================
echo Step 1: Cleaning previous builds...
echo ========================================
call mvn clean
if %ERRORLEVEL% NEQ 0 goto :error
echo.

REM Download dependencies
echo ========================================
echo Step 2: Downloading dependencies...
echo ========================================
call mvn dependency:resolve
if %ERRORLEVEL% NEQ 0 goto :error
echo.

REM Compile the project
echo ========================================
echo Step 3: Compiling project...
echo ========================================
call mvn compile
if %ERRORLEVEL% NEQ 0 goto :error
echo.

REM Run tests (optional - can be skipped for faster builds)
echo ========================================
echo Step 4: Running tests...
echo ========================================
call mvn test
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
call mvn package -DskipTests
if %ERRORLEVEL% NEQ 0 goto :error
echo.

REM Build the installer using the windows-installer profile
echo ========================================
echo Step 6: Building Windows installer...
echo ========================================
call mvn package -Pwindows-installer -DskipTests
if %ERRORLEVEL% NEQ 0 goto :error
echo.

REM Success
echo ========================================
echo BUILD SUCCESSFUL!
echo ========================================
echo.
echo The installer has been created in: target\installer\
echo File: PayrollDesktop-0.1.0.exe
echo.
echo Opening installer directory...
start "" "target\installer"
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