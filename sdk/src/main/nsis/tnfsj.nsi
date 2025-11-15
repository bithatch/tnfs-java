;TNFSJ

;--------------------------------
;Include Modern UI

  !define MUI_WELCOMEFINISHPAGE_BITMAP "sidebar.bmp"
  !define MUI_UNWELCOMEFINISHPAGE_BITMAP "sidebar.bmp"
  !include "MUI2.nsh"
  !include LogicLib.nsh


;--------------------------------
;General

  ;Name and file
  Name "TNFSJ"
  ;OutFile ..\..\target\release\TNFSJ_Installer.exe

  ;Default installation folder
  InstallDir "$PROGRAMFILES64\TNFSJ"
  
  ;Get installation folder from registry if available
  InstallDirRegKey HKLM  "Software\TNFSJ" ""

  ;Request application privileges for Windows Vista
  RequestExecutionLevel admin


;--------------------------------
;Variables

  Var StartMenuFolder
;--------------------------------
;Interface Settings

  !define MUI_ABORTWARNING
  !define MUI_HEADERIMAGE
  !define MUI_HEADERIMAGE_BITMAP "banner.bmp" ; optional

;--------------------------------
;Pages

  !insertmacro MUI_PAGE_WELCOME
  !define MUI_PAGE_CUSTOMFUNCTION_SHOW licpageshow
  !insertmacro MUI_PAGE_LICENSE "../../../../LICENSE"
  !insertmacro MUI_PAGE_COMPONENTS
  !insertmacro MUI_PAGE_DIRECTORY

;Start Menu Folder Page Configuration
  !define MUI_STARTMENUPAGE_REGISTRY_ROOT "HKLM" 
  !define MUI_STARTMENUPAGE_REGISTRY_KEY "Software\TNFSJ" 
  !define MUI_STARTMENUPAGE_REGISTRY_VALUENAME "TNFSJ"
  
  !insertmacro MUI_PAGE_STARTMENU Application $StartMenuFolder

  !insertmacro MUI_PAGE_INSTFILES
  !define MUI_FINISHPAGE_NOAUTOCLOSE
; !define MUI_FINISHPAGE_RUN
;   !define MUI_FINISHPAGE_RUN_NOTCHECKED
;   !define MUI_FINISHPAGE_RUN_TEXT "Start TNFSJ"
;   !define MUI_FINISHPAGE_RUN_FUNCTION "LaunchLink"
;   !define MUI_FINISHPAGE_SHOWREADME_NOTCHECKED
;   !define MUI_FINISHPAGE_SHOWREADME $INSTDIR\README.md
  
  
  !insertmacro MUI_PAGE_FINISH
  
  !insertmacro MUI_UNPAGE_CONFIRM
  !insertmacro MUI_UNPAGE_INSTFILES
  !insertmacro MUI_UNPAGE_FINISH
  
;--------------------------------
;Languages
 
  !insertmacro MUI_LANGUAGE "English"

;--------------------------------
;Installer Sections

Section "TNFSJ Common Files" TNFSJ

  SetOutPath "$INSTDIR"
  
  ;ADD YOUR OWN FILES HERE...
  File icon.ico
  File ..\..\..\..\README.md
  
  ;Store installation folder
  WriteRegStr HKLM "Software\TNFSJ" "" $INSTDIR
  
  ;Create uninstaller
  WriteUninstaller "$INSTDIR\Uninstall.exe"
  
  ;Create shortcuts
  CreateDirectory "$SMPROGRAMS\$StartMenuFolder"  
  CreateShortCut "$SMPROGRAMS\$StartMenuFolder\TNFSJ Documentation.lnk" "$INSTDIR\doc"
  CreateShortCut "$SMPROGRAMS\$StartMenuFolder\Uninstall.lnk" "$INSTDIR\Uninstall.exe"
  
SectionEnd

Section "TNFSJ Java Libraries" TNFSJ_JavaLibs

  SetOutPath "$INSTDIR\lib"
  File /r ..\..\..\target\sdk\tnfs-java-sdk-windows-amd64\lib\*.*
  
SectionEnd

Section "TNFSJ Client Tools" TNFSJ_ClientTools

  SetOutPath "$INSTDIR\bin"
  File /r ..\..\..\target\sdk\tnfs-java-sdk-windows-amd64\bin\*.*
  
  SetOutPath "$INSTDIR\doc"
  File /r ..\..\..\target\sdk\tnfs-java-sdk-windows-amd64\doc\tnfscp*.*
  File /r ..\..\..\target\sdk\tnfs-java-sdk-windows-amd64\doc\tnfs-fuse*.*
  File /r ..\..\..\target\sdk\tnfs-java-sdk-windows-amd64\doc\tnfstp*.*
   
  CreateShortCut "$SMPROGRAMS\$StartMenuFolder\TNFSTP.lnk" "$INSTDIR\tnfstp.exe" \
    "" \
    $INSTDIR\icon.ico 0 SW_SHOWNORMAL ALT|CONTROL|SHIFT|P "TNFSJ"
  
SectionEnd

Section "TNFSJ Server (Service)" TNFSJ_Server

  SetOutPath "$INSTDIR\sbin"
  File /r ..\..\..\target\sdk\tnfs-java-sdk-windows-amd64\sbin\tnfsjd.exe
  File /r ..\..\..\target\sdk\tnfs-java-sdk-windows-amd64\sbin\tnfs-user.exe
  
  SetOutPath "$INSTDIR\doc"
  File /r ..\..\..\target\sdk\tnfs-java-sdk-windows-amd64\doc\tnfs-user*.*
  
  SetOutPath "$INSTDIR\etc"
  File /r configuration\tnfsjd
  
  SetOutPath "$INSTDIR"
  File tnfsjd-service.exe
  File tnfsjd-service.xml 
  
  ExecShellWait "" "$INSTDIR\tnfsjd-service.exe" "install" SW_HIDE
  ExecShellWait "" "$INSTDIR\tnfsjd-service.exe" "start" SW_HIDE
SectionEnd

Section "TNFSJ Web Basd File Browser (Service)" TNFSJ_Web

  SetOutPath "$INSTDIR\sbin"
  File /r ..\..\..\target\sdk\tnfs-java-sdk-windows-amd64\sbin\tnfsjd-web.exe
  
  SetOutPath "$INSTDIR"
  File tnfsjd-web-service.exe
  File tnfsjd-web-service.xml
  
  SetOutPath "$INSTDIR\etc"
  File /r configuration\tnfsjd-web
  
  ExecShellWait "" "$INSTDIR\tnfsjd-web-service.exe" "install" SW_HIDE
  ExecShellWait "" "$INSTDIR\tnfsjd-web-service.exe" "start" SW_HIDE
SectionEnd

Section /o "Source (Zipped)" TNFSJ_Source

  SetOutPath "$INSTDIR"
  File /r ..\..\..\target\tnfs-java-src.zip
  
SectionEnd
;--------------------------------
;Functions

Function .onInit
UserInfo::GetAccountType
pop $0
${If} $0 != "admin" ;Require admin rights on NT4+
    MessageBox mb_iconstop "Administrator rights required!"
    SetErrorLevel 740 ;ERROR_ELEVATION_REQUIRED
    Quit
${EndIf}

	IntOp $0 ${SF_SELECTED} | ${SF_RO}
  	SectionSetFlags ${TNFSJ} $0


FunctionEnd



;--------------------------------
;Descriptions

  ;Language strings
  LangString DESC_TNFSJ ${LANG_ENGLISH} "A full SDK for the retro computing focused file transfer protocol"

  ;Assign language strings to sections
  !insertmacro MUI_FUNCTION_DESCRIPTION_BEGIN
    !insertmacro MUI_DESCRIPTION_TEXT ${TNFSJ} $(DESC_TNFSJ)
  !insertmacro MUI_FUNCTION_DESCRIPTION_END

;--------------------------------
;Uninstaller Section

Section "Uninstall"

  ExecShellWait "" "$INSTDIR\tnfsjd-service.exe" "stop" SW_HIDE
  ExecShellWait "" "$INSTDIR\tnfsjd-service.exe" "uninstall" SW_HIDE  
  ExecShellWait "" "$INSTDIR\tnfsjd-web-service.exe" "stop" SW_HIDE
  ExecShellWait "" "$INSTDIR\tnfsjd-web-service.exe" "uninstall" SW_HIDE
  
  Sleep 3000
  
  ;ADD YOUR OWN FILES HERE...

  Delete "$INSTDIR\Uninstall.exe"

  DELETE "$INSTDIR\sbin\tnfsjd.exe"
  DELETE "$INSTDIR\sbin\tnfsjd-web.exe"
  DELETE "$INSTDIR\sbin\tnfs-user.exe"
  DELETE "$INSTDIR\bin\tnfscp.exe"
  DELETE "$INSTDIR\bin\tnfstp.exe"
  DELETE "$INSTDIR\bin\tnfsfuse.exe"  
  DELETE "$INSTDIR\lib\*.jar"
  DELETE "$INSTDIR\doc\*.html"    
  DELETE "$INSTDIR\etc\*.sample"        
  DELETE "$INSTDIR\etc\tnfsjd\*.sample"
  DELETE "$INSTDIR\etc\tnfsjd-web\*.sample"
  DELETE "$INSTDIR\tnfsjd-service.exe"
  DELETE "$INSTDIR\tnfsjd-service.xml"
  DELETE "$INSTDIR\tnfsjd-web-service.exe"
  DELETE "$INSTDIR\tnfsjd-web-service.xml"
  DELETE "$INSTDIR\icon.ico"
  DELETE "$INSTDIR\README.md"
  DELETE "$INSTDIR\log\*.log"
  DELETE "$INSTDIR\LICENSE"
  DELETE "$INSTDIR\tnfs-java-src.zip"
  RMDir "$INSTDIR\bin"  
  RMDir "$INSTDIR\sbin"
  RMDir "$INSTDIR\doc"
  RMDir "$INSTDIR\etc\tnfsjd-web"  
  RMDir "$INSTDIR\etc\tnfsjd"
  RMDir "$INSTDIR\etc"
  RMDir "$INSTDIR\lib"
  RMDir "$INSTDIR\log"
  RMDir "$INSTDIR\logs"
  RMDir "$INSTDIR"

  !insertmacro MUI_STARTMENU_GETFOLDER Application $StartMenuFolder
    
  Delete "$SMPROGRAMS\$StartMenuFolder\Uninstall.lnk"
  Delete "$SMPROGRAMS\$StartMenuFolder\TNFSTP.lnk"
  Delete "$SMPROGRAMS\$StartMenuFolder\TNFSJ Documentation.lnk"
  
  RMDir "$SMPROGRAMS\$StartMenuFolder"

  DeleteRegKey /ifempty HKLM  "tnfsj"
  DeleteRegKey /ifempty HKLM "Software\TNFSJ"

SectionEnd

Function licpageshow
    FindWindow $0 "#32770" "" $HWNDPARENT
    CreateFont $1 "Courier New" "$(^FontSize)"
    GetDlgItem $0 $0 1000
    SendMessage $0 ${WM_SETFONT} $1 1
FunctionEnd

Function LaunchLink
   ExecShell "" "$SMPROGRAMS\$StartMenuFolder\TNFSTP.lnk"
FunctionEnd