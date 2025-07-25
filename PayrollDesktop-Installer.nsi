; ============================================================================
; Payroll Desktop NSIS Installer Script
; Copyright (c) 2025 MFG UNITED LLC
; ============================================================================

; Include modern UI
!include "MUI2.nsh"
!include "FileFunc.nsh"

; General
Name "Payroll Desktop"
OutFile "PayrollDesktop-Setup.exe"
InstallDir "$PROGRAMFILES64\MFG UNITED LLC\Payroll Desktop"
InstallDirRegKey HKLM "Software\MFG UNITED LLC\Payroll Desktop" "InstallPath"

; Request application privileges
RequestExecutionLevel admin

; Version information
VIProductVersion "0.1.0.0"
VIAddVersionKey "ProductName" "Payroll Desktop"
VIAddVersionKey "CompanyName" "MFG UNITED LLC"
VIAddVersionKey "LegalCopyright" "Copyright (c) 2025 MFG UNITED LLC"
VIAddVersionKey "FileDescription" "Professional payroll management system for trucking companies"
VIAddVersionKey "FileVersion" "0.1.0.0"
VIAddVersionKey "ProductVersion" "0.1.0.0"

; Interface Settings
!define MUI_ABORTWARNING
!define MUI_ICON "icon.ico"
!define MUI_UNICON "icon.ico"

; Pages
!insertmacro MUI_PAGE_WELCOME
!insertmacro MUI_PAGE_LICENSE "LICENSE.txt"
!insertmacro MUI_PAGE_DIRECTORY
!insertmacro MUI_PAGE_INSTFILES
!insertmacro MUI_PAGE_FINISH

; Uninstaller pages
!insertmacro MUI_UNPAGE_WELCOME
!insertmacro MUI_UNPAGE_CONFIRM
!insertmacro MUI_UNPAGE_INSTFILES
!insertmacro MUI_UNPAGE_FINISH

; Language
!insertmacro MUI_LANGUAGE "English"

; ============================================================================
; Installation
; ============================================================================

Section "Main Application" SecMain
    SectionIn RO
    
    ; Set output path to the installation directory
    SetOutPath "$INSTDIR"
    
    ; Main application files
    File "target\PayrollDesktop-0.1.0-jar-with-dependencies.jar"
    File "icon.ico"
    
    ; Copy launcher scripts
    SetOutPath "$INSTDIR\bin"
    File "launcher\PayrollDesktop.bat"
    File "launcher\PayrollDesktop-Console.bat"
    
    ; Install JDK
    SetOutPath "$INSTDIR\jdk"
    File /r "jdk-24\*.*"
    
    ; Install JavaFX
    SetOutPath "$INSTDIR\javafx"
    File /r "javafx-sdk-24.0.1\*.*"
    
    ; Install Maven
    SetOutPath "$INSTDIR\maven"
    File /r "apache-maven-3.9.10\*.*"
    
    ; Write registry information
    WriteRegStr HKLM "Software\MFG UNITED LLC\Payroll Desktop" "InstallPath" "$INSTDIR"
    WriteRegStr HKLM "Software\MFG UNITED LLC\Payroll Desktop" "Version" "0.1.0"
    WriteRegStr HKLM "Software\MFG UNITED LLC\Payroll Desktop" "JavaPath" "$INSTDIR\jdk\bin\java.exe"
    WriteRegStr HKLM "Software\MFG UNITED LLC\Payroll Desktop" "JavaFXPath" "$INSTDIR\javafx"
    WriteRegStr HKLM "Software\MFG UNITED LLC\Payroll Desktop" "MavenPath" "$INSTDIR\maven"
    
    ; Create uninstaller
    WriteUninstaller "$INSTDIR\Uninstall.exe"
    
    ; Create start menu shortcuts
    CreateDirectory "$SMPROGRAMS\Payroll Desktop"
    CreateShortCut "$SMPROGRAMS\Payroll Desktop\Payroll Desktop.lnk" "$INSTDIR\bin\PayrollDesktop.bat" "" "$INSTDIR\icon.ico"
    CreateShortCut "$SMPROGRAMS\Payroll Desktop\Payroll Desktop (Console).lnk" "$INSTDIR\bin\PayrollDesktop-Console.bat" "" "$INSTDIR\icon.ico"
    CreateShortCut "$SMPROGRAMS\Payroll Desktop\Uninstall.lnk" "$INSTDIR\Uninstall.exe"
    
    ; Create desktop shortcut
    CreateShortCut "$DESKTOP\Payroll Desktop.lnk" "$INSTDIR\bin\PayrollDesktop.bat" "" "$INSTDIR\icon.ico"
    
    ; Register uninstaller
    WriteRegStr HKLM "Software\Microsoft\Windows\CurrentVersion\Uninstall\Payroll Desktop" "DisplayName" "Payroll Desktop"
    WriteRegStr HKLM "Software\Microsoft\Windows\CurrentVersion\Uninstall\Payroll Desktop" "UninstallString" "$INSTDIR\Uninstall.exe"
    WriteRegStr HKLM "Software\Microsoft\Windows\CurrentVersion\Uninstall\Payroll Desktop" "DisplayIcon" "$INSTDIR\icon.ico"
    WriteRegStr HKLM "Software\Microsoft\Windows\CurrentVersion\Uninstall\Payroll Desktop" "Publisher" "MFG UNITED LLC"
    WriteRegStr HKLM "Software\Microsoft\Windows\CurrentVersion\Uninstall\Payroll Desktop" "DisplayVersion" "0.1.0"
    WriteRegDWORD HKLM "Software\Microsoft\Windows\CurrentVersion\Uninstall\Payroll Desktop" "NoModify" 1
    WriteRegDWORD HKLM "Software\Microsoft\Windows\CurrentVersion\Uninstall\Payroll Desktop" "NoRepair" 1
SectionEnd

; ============================================================================
; Uninstallation
; ============================================================================

Section "Uninstall"
    ; Remove files and directories
    Delete "$INSTDIR\bin\PayrollDesktop.bat"
    Delete "$INSTDIR\bin\PayrollDesktop-Console.bat"
    RMDir "$INSTDIR\bin"
    
    Delete "$INSTDIR\PayrollDesktop-0.1.0-jar-with-dependencies.jar"
    Delete "$INSTDIR\icon.ico"
    Delete "$INSTDIR\Uninstall.exe"
    
    ; Remove JDK, JavaFX, and Maven directories
    RMDir /r "$INSTDIR\jdk"
    RMDir /r "$INSTDIR\javafx"
    RMDir /r "$INSTDIR\maven"
    
    ; Remove installation directory
    RMDir "$INSTDIR"
    
    ; Remove start menu shortcuts
    Delete "$SMPROGRAMS\Payroll Desktop\Payroll Desktop.lnk"
    Delete "$SMPROGRAMS\Payroll Desktop\Payroll Desktop (Console).lnk"
    Delete "$SMPROGRAMS\Payroll Desktop\Uninstall.lnk"
    RMDir "$SMPROGRAMS\Payroll Desktop"
    
    ; Remove desktop shortcut
    Delete "$DESKTOP\Payroll Desktop.lnk"
    
    ; Remove registry keys
    DeleteRegKey HKLM "Software\Microsoft\Windows\CurrentVersion\Uninstall\Payroll Desktop"
    DeleteRegKey HKLM "Software\MFG UNITED LLC\Payroll Desktop"
    
    ; Note: User data in %LOCALAPPDATA% is preserved
SectionEnd 