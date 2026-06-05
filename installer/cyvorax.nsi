Unicode true
!include "MUI2.nsh"

!ifndef APP_VERSION
  !define APP_VERSION "1.0.1"
!endif

Name "CyvoraX Suite"
OutFile "target\CyvoraX-Suite-Setup.exe"
InstallDir "$PROGRAMFILES\CyvoraX Suite"
InstallDirRegKey HKCU "Software\CyvoraX Suite" ""
RequestExecutionLevel admin
BrandingText "CyvoraX Suite"

!define MUI_ABORTWARNING
!define MUI_ICON "installer\CyvoraX.ico"
!define MUI_UNICON "installer\CyvoraX.ico"
!define MUI_WELCOMEFINISHPAGE_BITMAP "installer\cyvorax_dialog.bmp"
!define MUI_HEADERIMAGE
!define MUI_HEADERIMAGE_BITMAP "installer\cyvorax_banner.bmp"
!define MUI_HEADERIMAGE_RIGHT

; Dark color scheme
!define MUI_BGCOLOR "0a0f1a"
!define MUI_TEXTCOLOR "ffffff"

!insertmacro MUI_PAGE_WELCOME
!insertmacro MUI_PAGE_LICENSE "installer\LICENSE.txt"
!insertmacro MUI_PAGE_DIRECTORY
!insertmacro MUI_PAGE_INSTFILES
!define MUI_FINISHPAGE_RUN "$INSTDIR\CyvoraX Suite.exe"
!define MUI_FINISHPAGE_RUN_TEXT "Launch CyvoraX Suite"
!insertmacro MUI_PAGE_FINISH

!insertmacro MUI_UNPAGE_CONFIRM
!insertmacro MUI_UNPAGE_INSTFILES

!insertmacro MUI_LANGUAGE "English"

Section "Install"
  SetOutPath "$INSTDIR"
  File /r "target\jpackage\CyvoraX Suite\*.*"

  WriteUninstaller "$INSTDIR\Uninstall.exe"

  CreateDirectory "$SMPROGRAMS\CyvoraX Suite"
  CreateShortcut "$SMPROGRAMS\CyvoraX Suite\CyvoraX Suite.lnk" "$INSTDIR\CyvoraX Suite.exe"
  CreateShortcut "$DESKTOP\CyvoraX Suite.lnk" "$INSTDIR\CyvoraX Suite.exe" "" "$INSTDIR\CyvoraX Suite.exe"

  WriteRegStr HKCU "Software\CyvoraX Suite" "" "$INSTDIR"
  WriteRegStr HKCU "Software\Microsoft\Windows\CurrentVersion\Uninstall\CyvoraX Suite" "DisplayName" "CyvoraX Suite"
  WriteRegStr HKCU "Software\Microsoft\Windows\CurrentVersion\Uninstall\CyvoraX Suite" "UninstallString" "$INSTDIR\Uninstall.exe"
  WriteRegStr HKCU "Software\Microsoft\Windows\CurrentVersion\Uninstall\CyvoraX Suite" "DisplayIcon" "$INSTDIR\CyvoraX Suite.exe"
  WriteRegStr HKCU "Software\Microsoft\Windows\CurrentVersion\Uninstall\CyvoraX Suite" "Publisher" "CyvoraX"
  WriteRegStr HKCU "Software\Microsoft\Windows\CurrentVersion\Uninstall\CyvoraX Suite" "DisplayVersion" "${APP_VERSION}"
SectionEnd

Section "Uninstall"
  Delete "$DESKTOP\CyvoraX Suite.lnk"
  RMDir /r "$SMPROGRAMS\CyvoraX Suite"
  DeleteRegKey HKCU "Software\Microsoft\Windows\CurrentVersion\Uninstall\CyvoraX Suite"
  DeleteRegKey HKCU "Software\CyvoraX Suite"
  RMDir /r "$INSTDIR"
SectionEnd
