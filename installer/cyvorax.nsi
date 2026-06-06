Unicode true
!include "MUI2.nsh"
!include "LogicLib.nsh"
!include "nsDialogs.nsh"
!include "FileFunc.nsh"
!include "WordFunc.nsh"

!insertmacro GetParent
!insertmacro VersionCompare

!ifndef APP_VERSION
  !define APP_VERSION "1.1.1"
!endif
!ifndef PROJECT_DIR
  !define PROJECT_DIR "."
!endif
!ifndef INSTALLER_DIR
  !define INSTALLER_DIR "${PROJECT_DIR}\installer"
!endif

!define APP_NAME "CyvoraX Suite"
!define APP_EXE "CyvoraX Suite.exe"
!define COMPANY_NAME "CyvoraX"
!define SOFTWARE_KEY "Software\CyvoraX Suite"
!define UNINSTALL_KEY "Software\Microsoft\Windows\CurrentVersion\Uninstall\CyvoraX Suite"

Name "${APP_NAME}"
OutFile "${PROJECT_DIR}\target\CyvoraX-Setup-${APP_VERSION}.exe"
InstallDir "$PROGRAMFILES\CyvoraX Suite"
InstallDirRegKey HKCU "${SOFTWARE_KEY}" ""
RequestExecutionLevel admin
BrandingText "CyvoraX Suite"

!define MUI_ABORTWARNING
!define MUI_ICON "${INSTALLER_DIR}\CyvoraX.ico"
!define MUI_UNICON "${INSTALLER_DIR}\CyvoraX.ico"
!define MUI_WELCOMEFINISHPAGE_BITMAP "${INSTALLER_DIR}\cyvorax_dialog.bmp"
!define MUI_HEADERIMAGE
!define MUI_HEADERIMAGE_BITMAP "${INSTALLER_DIR}\cyvorax_banner.bmp"
!define MUI_HEADERIMAGE_RIGHT
!define MUI_BGCOLOR "0a0f1a"
!define MUI_TEXTCOLOR "ffffff"

!insertmacro MUI_PAGE_WELCOME
!insertmacro MUI_PAGE_LICENSE "${INSTALLER_DIR}\LICENSE.txt"
Page custom UpgradePageCreate UpgradePageLeave
Page custom InstallOptionsPageCreate InstallOptionsPageLeave
!define MUI_PAGE_CUSTOMFUNCTION_PRE DirectoryPagePre
!insertmacro MUI_PAGE_DIRECTORY
!insertmacro MUI_PAGE_INSTFILES
!define MUI_FINISHPAGE_RUN "$INSTDIR\${APP_EXE}"
!define MUI_FINISHPAGE_RUN_TEXT "Launch CyvoraX Suite"
!insertmacro MUI_PAGE_FINISH

!insertmacro MUI_UNPAGE_CONFIRM
!insertmacro MUI_UNPAGE_INSTFILES

!insertmacro MUI_LANGUAGE "English"

Var ExistingFound
Var ExistingInstallDir
Var ExistingInstallVersion
Var ExistingUninstallString
Var InstallMode
Var CompatibilityOk
Var CompatibilityText
Var ProfileDir
Var BackupDir
Var CandidateDir
Var CandidateVersion
Var CandidateUninstall
Var Dialog
Var UpgradeRadio
Var DifferentRadio
Var DesktopShortcut
Var StartMenuShortcut
Var DesktopCheckbox
Var StartMenuCheckbox
Var RunningProcessName
Var LockedFilePath

Function .onInit
  StrCpy $ProfileDir "$PROFILE\.cyvorax-suite"
  StrCpy $DesktopShortcut "1"
  StrCpy $StartMenuShortcut "1"
  StrCpy $InstallMode "fresh"
  StrCpy $ExistingFound "0"
  StrCpy $ExistingInstallDir ""
  StrCpy $ExistingInstallVersion ""
  StrCpy $ExistingUninstallString ""
  StrCpy $BackupDir ""

  Call DetectExistingInstallation
  ${If} $ExistingFound == "1"
    StrCpy $InstallMode "upgrade"
    StrCpy $INSTDIR "$ExistingInstallDir"
    Call ValidateUpgradeCompatibility
  ${EndIf}
FunctionEnd

Function DetectExistingInstallation
  StrCpy $ExistingFound "0"
  StrCpy $ExistingInstallDir ""
  StrCpy $ExistingInstallVersion ""
  StrCpy $ExistingUninstallString ""

  SetRegView 64
  Call DetectRegistryCandidates
  SetRegView 32
  Call DetectRegistryCandidates
  SetRegView lastused

  ${If} $ExistingFound != "1"
    StrCpy $CandidateDir "$PROGRAMFILES\CyvoraX Suite"
    StrCpy $CandidateVersion ""
    StrCpy $CandidateUninstall ""
    Call ConsiderCandidate
  ${EndIf}
FunctionEnd

Function DetectRegistryCandidates
  ${If} $ExistingFound == "1"
    Return
  ${EndIf}

  ClearErrors
  ReadRegStr $CandidateDir HKCU "${UNINSTALL_KEY}" "InstallLocation"
  ReadRegStr $CandidateVersion HKCU "${UNINSTALL_KEY}" "DisplayVersion"
  ReadRegStr $CandidateUninstall HKCU "${UNINSTALL_KEY}" "UninstallString"
  Call ResolveCandidateDirFromUninstall
  Call ConsiderCandidate

  ${If} $ExistingFound == "1"
    Return
  ${EndIf}

  ClearErrors
  ReadRegStr $CandidateDir HKLM "${UNINSTALL_KEY}" "InstallLocation"
  ReadRegStr $CandidateVersion HKLM "${UNINSTALL_KEY}" "DisplayVersion"
  ReadRegStr $CandidateUninstall HKLM "${UNINSTALL_KEY}" "UninstallString"
  Call ResolveCandidateDirFromUninstall
  Call ConsiderCandidate

  ${If} $ExistingFound == "1"
    Return
  ${EndIf}

  ClearErrors
  ReadRegStr $CandidateDir HKCU "${SOFTWARE_KEY}" ""
  ReadRegStr $CandidateVersion HKCU "${SOFTWARE_KEY}" "Version"
  StrCpy $CandidateUninstall ""
  Call ConsiderCandidate

  ${If} $ExistingFound == "1"
    Return
  ${EndIf}

  ClearErrors
  ReadRegStr $CandidateDir HKLM "${SOFTWARE_KEY}" ""
  ReadRegStr $CandidateVersion HKLM "${SOFTWARE_KEY}" "Version"
  StrCpy $CandidateUninstall ""
  Call ConsiderCandidate
FunctionEnd

Function ResolveCandidateDirFromUninstall
  ${If} $CandidateDir == ""
    ${If} $CandidateUninstall != ""
      ${GetParent} "$CandidateUninstall" $CandidateDir
    ${EndIf}
  ${EndIf}
FunctionEnd

Function ConsiderCandidate
  ${If} $ExistingFound == "1"
    Return
  ${EndIf}
  ${If} $CandidateDir == ""
    Return
  ${EndIf}
  ${If} ${FileExists} "$CandidateDir\${APP_EXE}"
  ${OrIf} ${FileExists} "$CandidateDir\Uninstall.exe"
    StrCpy $ExistingFound "1"
    StrCpy $ExistingInstallDir "$CandidateDir"
    StrCpy $ExistingInstallVersion "$CandidateVersion"
    StrCpy $ExistingUninstallString "$CandidateUninstall"
  ${EndIf}
FunctionEnd

Function IsProcessRunning
  Exch $0
  Push $1
  Push $2

  nsExec::ExecToStack 'cmd /C tasklist /FI $\"IMAGENAME eq $0$\" /NH | findstr /I /C:$\"$0$\" >NUL'
  Pop $1
  Pop $2

  ${If} $1 == "0"
    StrCpy $0 "1"
  ${Else}
    StrCpy $0 "0"
  ${EndIf}

  Pop $2
  Pop $1
  Exch $0
FunctionEnd

Function CheckRunningProcess
  Exch $0
  Push $1

  Push "$0"
  Call IsProcessRunning
  Pop $1
  ${If} $1 == "1"
    StrCpy $RunningProcessName "$0"
  ${EndIf}

  Pop $1
  Pop $0
FunctionEnd

Function EnsureNoRunningProcesses
  processRetry:
    StrCpy $RunningProcessName ""

    Push "CyvoraX.exe"
    Call CheckRunningProcess
    Push "CyvoraX Suite.exe"
    Call CheckRunningProcess
    Push "java.exe"
    Call CheckRunningProcess
    Push "javaw.exe"
    Call CheckRunningProcess

    ${If} $RunningProcessName != ""
      MessageBox MB_ICONEXCLAMATION|MB_RETRYCANCEL "CyvoraX is currently running. Please close it before continuing.$\r$\n$\r$\nDetected process: $RunningProcessName" IDRETRY processRetry IDCANCEL processCancel
    ${EndIf}
    Return

  processCancel:
    Abort
FunctionEnd

Function IsFileUnlocked
  Exch $0
  Push $1

  ${IfNot} ${FileExists} "$0"
    StrCpy $0 "1"
    Goto fileLockDone
  ${EndIf}

  StrCpy $1 "$0.cyvorax-lock-test"
  Delete "$1"
  ClearErrors
  Rename "$0" "$1"
  IfErrors fileLocked

  ClearErrors
  Rename "$1" "$0"
  IfErrors fileLocked

  StrCpy $0 "1"
  Goto fileLockDone

  fileLocked:
    ${If} ${FileExists} "$1"
      ClearErrors
      Rename "$1" "$0"
    ${EndIf}
    StrCpy $0 "0"

  fileLockDone:
    Pop $1
    Exch $0
FunctionEnd

Function CheckFileUnlocked
  Exch $0
  Push $1

  ${If} $LockedFilePath == ""
    Push "$0"
    Call IsFileUnlocked
    Pop $1
    ${If} $1 != "1"
      StrCpy $LockedFilePath "$0"
    ${EndIf}
  ${EndIf}

  Pop $1
  Pop $0
FunctionEnd

Function EnsureCriticalFilesUnlocked
  ${If} $ExistingFound != "1"
    Return
  ${EndIf}

  lockRetry:
    StrCpy $LockedFilePath ""

    Push "$INSTDIR\${APP_EXE}"
    Call CheckFileUnlocked
    Push "$INSTDIR\Uninstall.exe"
    Call CheckFileUnlocked
    Push "$INSTDIR\runtime\bin\msvcp140.dll"
    Call CheckFileUnlocked
    Push "$INSTDIR\runtime\bin\vcruntime140.dll"
    Call CheckFileUnlocked
    Push "$INSTDIR\runtime\bin\java.exe"
    Call CheckFileUnlocked
    Push "$INSTDIR\runtime\bin\javaw.exe"
    Call CheckFileUnlocked
    Push "$INSTDIR\CyvoraX Suite.log"
    Call CheckFileUnlocked

    ${If} $LockedFilePath != ""
      MessageBox MB_ICONEXCLAMATION|MB_RETRYCANCEL "CyvoraX setup cannot continue because an existing file is locked.$\r$\n$\r$\nLocked file:$\r$\n$LockedFilePath$\r$\n$\r$\nClose CyvoraX and any related Java processes, then click Retry." IDRETRY lockRetry IDCANCEL lockCancel
    ${EndIf}
    Return

  lockCancel:
    Abort
FunctionEnd

Function EnsureInstallTargetReady
  Call EnsureNoRunningProcesses
  Call EnsureCriticalFilesUnlocked
FunctionEnd

Function RemoveExistingApplicationFiles
  removeRetry:
    Call EnsureInstallTargetReady
    DetailPrint "Removing existing CyvoraX application files from $INSTDIR"
    Delete "$INSTDIR\${APP_EXE}"
    Delete "$INSTDIR\Uninstall.exe"
    RMDir /r "$INSTDIR\app"
    RMDir /r "$INSTDIR\runtime"
    RMDir /r "$INSTDIR\lib"
    RMDir /r "$INSTDIR\tools"

    StrCpy $LockedFilePath ""
    ${If} ${FileExists} "$INSTDIR\${APP_EXE}"
      StrCpy $LockedFilePath "$INSTDIR\${APP_EXE}"
    ${ElseIf} ${FileExists} "$INSTDIR\runtime\bin\msvcp140.dll"
      StrCpy $LockedFilePath "$INSTDIR\runtime\bin\msvcp140.dll"
    ${ElseIf} ${FileExists} "$INSTDIR\runtime\bin\javaw.exe"
      StrCpy $LockedFilePath "$INSTDIR\runtime\bin\javaw.exe"
    ${ElseIf} ${FileExists} "$INSTDIR\runtime\*.*"
      StrCpy $LockedFilePath "$INSTDIR\runtime"
    ${ElseIf} ${FileExists} "$INSTDIR\app\*.*"
      StrCpy $LockedFilePath "$INSTDIR\app"
    ${EndIf}

    ${If} $LockedFilePath != ""
      MessageBox MB_ICONEXCLAMATION|MB_RETRYCANCEL "CyvoraX setup could not remove the previous application files.$\r$\n$\r$\nBlocked path:$\r$\n$LockedFilePath$\r$\n$\r$\nClose CyvoraX and retry. Setup has not copied the new version yet." IDRETRY removeRetry IDCANCEL removeCancel
    ${EndIf}
    Return

  removeCancel:
    Abort
FunctionEnd

Function UpgradePageCreate
  ${If} $ExistingFound != "1"
    Abort
  ${EndIf}

  !insertmacro MUI_HEADER_TEXT "Existing CyvoraX installation detected" "Choose how this setup should continue."
  nsDialogs::Create 1018
  Pop $Dialog
  ${If} $Dialog == error
    Abort
  ${EndIf}

  ${NSD_CreateLabel} 0 0 100% 24u "Setup found an installed copy of CyvoraX Suite. User data is stored separately and will be preserved."
  Pop $0
  ${NSD_CreateLabel} 0 34u 100% 12u "Installed location: $ExistingInstallDir"
  Pop $0
  ${If} $ExistingInstallVersion == ""
    ${NSD_CreateLabel} 0 50u 100% 12u "Installed version: unknown"
  ${Else}
    ${NSD_CreateLabel} 0 50u 100% 12u "Installed version: $ExistingInstallVersion"
  ${EndIf}
  Pop $0
  ${NSD_CreateLabel} 0 66u 100% 12u "New version: ${APP_VERSION}"
  Pop $0

  Call ValidateUpgradeCompatibility
  ${NSD_CreateLabel} 0 86u 100% 28u "Compatibility: $CompatibilityText"
  Pop $0

  ${NSD_CreateRadioButton} 0 122u 100% 12u "Update Existing Installation"
  Pop $UpgradeRadio
  ${NSD_Check} $UpgradeRadio
  ${NSD_CreateRadioButton} 0 142u 100% 12u "Install To Different Directory"
  Pop $DifferentRadio

  nsDialogs::Show
FunctionEnd

Function UpgradePageLeave
  ${NSD_GetState} $UpgradeRadio $0
  ${If} $0 == ${BST_CHECKED}
    StrCpy $InstallMode "upgrade"
    StrCpy $INSTDIR "$ExistingInstallDir"
    Call ValidateUpgradeCompatibility
    ${If} $CompatibilityOk != "1"
      MessageBox MB_ICONSTOP|MB_OK "$CompatibilityText"
      Abort
    ${EndIf}
  ${Else}
    StrCpy $InstallMode "different"
    StrCpy $INSTDIR "$PROGRAMFILES\CyvoraX Suite ${APP_VERSION}"
  ${EndIf}
FunctionEnd

Function InstallOptionsPageCreate
  !insertmacro MUI_HEADER_TEXT "Installation options" "Choose shortcuts for this CyvoraX Suite installation."
  nsDialogs::Create 1018
  Pop $Dialog
  ${If} $Dialog == error
    Abort
  ${EndIf}

  ${NSD_CreateLabel} 0 0 100% 24u "These options control only application shortcuts. Certificates, plugins, settings, sessions, and workspace data stay in your CyvoraX profile."
  Pop $0

  ${NSD_CreateCheckbox} 0 42u 100% 12u "Create Desktop Shortcut"
  Pop $DesktopCheckbox
  ${If} $DesktopShortcut == "1"
    ${NSD_Check} $DesktopCheckbox
  ${EndIf}

  ${NSD_CreateCheckbox} 0 64u 100% 12u "Create Start Menu Shortcut"
  Pop $StartMenuCheckbox
  ${If} $StartMenuShortcut == "1"
    ${NSD_Check} $StartMenuCheckbox
  ${EndIf}

  ${NSD_CreateLabel} 0 96u 100% 42u "Launch CyvoraX after install is available on the final page. Upgrade mode creates a profile backup before application files are replaced."
  Pop $0

  nsDialogs::Show
FunctionEnd

Function InstallOptionsPageLeave
  ${NSD_GetState} $DesktopCheckbox $0
  ${If} $0 == ${BST_CHECKED}
    StrCpy $DesktopShortcut "1"
  ${Else}
    StrCpy $DesktopShortcut "0"
  ${EndIf}

  ${NSD_GetState} $StartMenuCheckbox $0
  ${If} $0 == ${BST_CHECKED}
    StrCpy $StartMenuShortcut "1"
  ${Else}
    StrCpy $StartMenuShortcut "0"
  ${EndIf}
FunctionEnd

Function DirectoryPagePre
  ${If} $InstallMode == "upgrade"
    Abort
  ${EndIf}
FunctionEnd

Function ValidateUpgradeCompatibility
  StrCpy $CompatibilityOk "1"
  ${If} $ExistingInstallVersion == ""
    StrCpy $CompatibilityText "Version metadata was not recorded by the existing installer. Setup will create a full CyvoraX profile backup before replacing application files."
    Return
  ${EndIf}

  ${VersionCompare} "$ExistingInstallVersion" "${APP_VERSION}" $0
  ${If} $0 == "1"
    StrCpy $CompatibilityOk "0"
    StrCpy $CompatibilityText "Installed version $ExistingInstallVersion is newer than this setup (${APP_VERSION}). Downgrades are not supported."
    Return
  ${EndIf}

  Push "$ExistingInstallVersion"
  Call ExtractMajorVersion
  Pop $0
  Push "${APP_VERSION}"
  Call ExtractMajorVersion
  Pop $1

  ${If} $0 == ""
  ${OrIf} $1 == ""
    StrCpy $CompatibilityText "Version metadata is non-standard. Setup will preserve user data and create a full profile backup before upgrading."
    Return
  ${EndIf}

  IntCmp $0 $1 majorEqual majorOlder majorNewer

  majorEqual:
    StrCpy $CompatibilityText "Compatible upgrade path verified for major version $1."
    Return

  majorOlder:
    StrCpy $CompatibilityOk "0"
    StrCpy $CompatibilityText "Installed major version $0 requires a migration-aware upgrade to ${APP_VERSION}. Install to a different directory or upgrade through a compatible release first."
    Return

  majorNewer:
    StrCpy $CompatibilityOk "0"
    StrCpy $CompatibilityText "Installed major version $0 is newer than setup major version $1. Downgrades are not supported."
FunctionEnd

Function ExtractMajorVersion
  Exch $0
  Push $1
  Push $2
  StrCpy $1 0

  majorLoop:
    StrCpy $2 $0 1 $1
    StrCmp $2 "" majorDone
    StrCmp $2 "." majorDone
    IntOp $1 $1 + 1
    Goto majorLoop

  majorDone:
    StrCpy $0 $0 $1
    Pop $2
    Pop $1
    Exch $0
FunctionEnd

Function BackupUserProfile
  ${IfNot} ${FileExists} "$ProfileDir\*.*"
    DetailPrint "No CyvoraX user profile found at $ProfileDir; backup not required."
    StrCpy $BackupDir ""
    Return
  ${EndIf}

  ${IfNot} ${FileExists} "$SYSDIR\robocopy.exe"
    MessageBox MB_ICONSTOP|MB_OK "Cannot create the required CyvoraX profile backup because robocopy.exe was not found."
    Abort
  ${EndIf}

  CreateDirectory "$PROFILE\.cyvorax-suite-backups"
  System::Call 'kernel32::GetTickCount() i .r0'
  StrCpy $BackupDir "$PROFILE\.cyvorax-suite-backups\backup-${APP_VERSION}-$0"
  CreateDirectory "$BackupDir"
  DetailPrint "Backing up CyvoraX profile: $ProfileDir"
  DetailPrint "Backup destination: $BackupDir"
  ExecWait '"$SYSDIR\robocopy.exe" "$ProfileDir" "$BackupDir" /E /COPY:DAT /R:2 /W:1 /NFL /NDL /NJH /NJS /NP' $1
  IntCmp $1 8 backupFailed backupOk backupFailed

  backupOk:
    DetailPrint "CyvoraX profile backup completed."
    Return

  backupFailed:
    MessageBox MB_ICONSTOP|MB_OK "Could not create the required CyvoraX profile backup. Upgrade cancelled to protect certificates, sessions, settings, database, plugins, and workspace data."
    Abort
FunctionEnd

Function RestoreProfileData
  ${If} $InstallMode != "upgrade"
    Return
  ${EndIf}
  ${If} $BackupDir == ""
    Return
  ${EndIf}
  ${If} ${FileExists} "$ProfileDir\cyvorax-suite.db"
    DetailPrint "CyvoraX user profile preserved: $ProfileDir"
    Return
  ${EndIf}

  DetailPrint "CyvoraX profile database was not found after upgrade. Restoring from backup."
  CreateDirectory "$ProfileDir"
  ExecWait '"$SYSDIR\robocopy.exe" "$BackupDir" "$ProfileDir" /E /COPY:DAT /R:2 /W:1 /NFL /NDL /NJH /NJS /NP' $1
  IntCmp $1 8 restoreFailed restoreOk restoreFailed

  restoreOk:
    DetailPrint "CyvoraX user settings restored from backup."
    Return

  restoreFailed:
    MessageBox MB_ICONEXCLAMATION|MB_OK "CyvoraX was installed, but user profile restore failed. Your backup is available at: $BackupDir"
FunctionEnd

Section "Install"
  Call EnsureInstallTargetReady

  ${If} $InstallMode == "upgrade"
    Call ValidateUpgradeCompatibility
    ${If} $CompatibilityOk != "1"
      MessageBox MB_ICONSTOP|MB_OK "$CompatibilityText"
      Abort
    ${EndIf}
    Call EnsureInstallTargetReady
    Call BackupUserProfile
    Call EnsureInstallTargetReady
    DetailPrint "Replacing existing CyvoraX application files in $INSTDIR"
    Call RemoveExistingApplicationFiles
  ${EndIf}

  CreateDirectory "$INSTDIR"
  SetOutPath "$INSTDIR"
  File /r "${PROJECT_DIR}\target\jpackage\CyvoraX Suite\*.*"

  Call RestoreProfileData

  WriteUninstaller "$INSTDIR\Uninstall.exe"

  ${If} $StartMenuShortcut == "1"
    CreateDirectory "$SMPROGRAMS\CyvoraX Suite"
    CreateShortcut "$SMPROGRAMS\CyvoraX Suite\CyvoraX Suite.lnk" "$INSTDIR\${APP_EXE}"
    CreateShortcut "$SMPROGRAMS\CyvoraX Suite\Uninstall CyvoraX Suite.lnk" "$INSTDIR\Uninstall.exe"
  ${Else}
    Delete "$SMPROGRAMS\CyvoraX Suite\CyvoraX Suite.lnk"
    Delete "$SMPROGRAMS\CyvoraX Suite\Uninstall CyvoraX Suite.lnk"
    RMDir "$SMPROGRAMS\CyvoraX Suite"
  ${EndIf}

  ${If} $DesktopShortcut == "1"
    CreateShortcut "$DESKTOP\CyvoraX Suite.lnk" "$INSTDIR\${APP_EXE}" "" "$INSTDIR\${APP_EXE}"
  ${Else}
    Delete "$DESKTOP\CyvoraX Suite.lnk"
  ${EndIf}

  WriteRegStr HKCU "${SOFTWARE_KEY}" "" "$INSTDIR"
  WriteRegStr HKCU "${SOFTWARE_KEY}" "Version" "${APP_VERSION}"
  WriteRegStr HKCU "${SOFTWARE_KEY}" "ProfileDirectory" "$ProfileDir"
  WriteRegStr HKCU "${SOFTWARE_KEY}" "LastBackupDirectory" "$BackupDir"
  WriteRegStr HKCU "${UNINSTALL_KEY}" "DisplayName" "${APP_NAME}"
  WriteRegStr HKCU "${UNINSTALL_KEY}" "UninstallString" "$\"$INSTDIR\Uninstall.exe$\""
  WriteRegStr HKCU "${UNINSTALL_KEY}" "QuietUninstallString" "$\"$INSTDIR\Uninstall.exe$\" /S"
  WriteRegStr HKCU "${UNINSTALL_KEY}" "InstallLocation" "$INSTDIR"
  WriteRegStr HKCU "${UNINSTALL_KEY}" "DisplayIcon" "$INSTDIR\${APP_EXE}"
  WriteRegStr HKCU "${UNINSTALL_KEY}" "Publisher" "${COMPANY_NAME}"
  WriteRegStr HKCU "${UNINSTALL_KEY}" "DisplayVersion" "${APP_VERSION}"
  WriteRegDWORD HKCU "${UNINSTALL_KEY}" "NoModify" 1
  WriteRegDWORD HKCU "${UNINSTALL_KEY}" "NoRepair" 1
SectionEnd

Function un.IsProcessRunning
  Exch $0
  Push $1
  Push $2

  nsExec::ExecToStack 'cmd /C tasklist /FI $\"IMAGENAME eq $0$\" /NH | findstr /I /C:$\"$0$\" >NUL'
  Pop $1
  Pop $2

  ${If} $1 == "0"
    StrCpy $0 "1"
  ${Else}
    StrCpy $0 "0"
  ${EndIf}

  Pop $2
  Pop $1
  Exch $0
FunctionEnd

Function un.CheckRunningProcess
  Exch $0
  Push $1

  Push "$0"
  Call un.IsProcessRunning
  Pop $1
  ${If} $1 == "1"
    StrCpy $RunningProcessName "$0"
  ${EndIf}

  Pop $1
  Pop $0
FunctionEnd

Function un.EnsureNoRunningProcesses
  processRetry:
    StrCpy $RunningProcessName ""

    Push "CyvoraX.exe"
    Call un.CheckRunningProcess
    Push "CyvoraX Suite.exe"
    Call un.CheckRunningProcess
    Push "java.exe"
    Call un.CheckRunningProcess
    Push "javaw.exe"
    Call un.CheckRunningProcess

    ${If} $RunningProcessName != ""
      MessageBox MB_ICONEXCLAMATION|MB_RETRYCANCEL "CyvoraX is currently running. Please close it before continuing.$\r$\n$\r$\nDetected process: $RunningProcessName" IDRETRY processRetry IDCANCEL processCancel
    ${EndIf}
    Return

  processCancel:
    Abort
FunctionEnd

Function un.RemoveInstallDirectory
  removeRetry:
    Call un.EnsureNoRunningProcesses
    RMDir /r "$INSTDIR"
    ${If} ${FileExists} "$INSTDIR\runtime\bin\msvcp140.dll"
      StrCpy $LockedFilePath "$INSTDIR\runtime\bin\msvcp140.dll"
    ${ElseIf} ${FileExists} "$INSTDIR\runtime\bin\javaw.exe"
      StrCpy $LockedFilePath "$INSTDIR\runtime\bin\javaw.exe"
    ${ElseIf} ${FileExists} "$INSTDIR\*.*"
      StrCpy $LockedFilePath "$INSTDIR"
    ${Else}
      StrCpy $LockedFilePath ""
    ${EndIf}

    ${If} $LockedFilePath != ""
      MessageBox MB_ICONEXCLAMATION|MB_RETRYCANCEL "CyvoraX uninstall could not remove all files because a file is locked.$\r$\n$\r$\nBlocked path:$\r$\n$LockedFilePath$\r$\n$\r$\nClose CyvoraX and retry." IDRETRY removeRetry IDCANCEL removeCancel
    ${EndIf}
    Return

  removeCancel:
    Abort
FunctionEnd

Section "Uninstall"
  Call un.EnsureNoRunningProcesses
  Delete "$DESKTOP\CyvoraX Suite.lnk"
  Delete "$SMPROGRAMS\CyvoraX Suite\CyvoraX Suite.lnk"
  Delete "$SMPROGRAMS\CyvoraX Suite\Uninstall CyvoraX Suite.lnk"
  RMDir "$SMPROGRAMS\CyvoraX Suite"
  DeleteRegKey HKCU "${UNINSTALL_KEY}"
  DeleteRegKey HKCU "${SOFTWARE_KEY}"
  Call un.RemoveInstallDirectory
SectionEnd
