param(
    [switch]$Installer,
    [string]$JavaFxJmods = $env:JAVAFX_JMODS
)

$ErrorActionPreference = "Stop"

$repoRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
$pomPath = Join-Path $repoRoot "pom.xml"
[xml]$pom = Get-Content -LiteralPath $pomPath

$appName = "CyvoraX Suite"
$appVersion = $pom.project.version
$artifactId = $pom.project.artifactId
$mainJar = "$artifactId-$appVersion.jar"
$installerDir = Join-Path $repoRoot "installer"
$iconPath = Join-Path $installerDir "CyvoraX.ico"
$resourceDir = $installerDir
$runtimeImage = Join-Path $repoRoot "runtime"
$appInput = Join-Path $repoRoot "target\app-input"
$appContent = Join-Path $repoRoot "target\app-content"
$dest = Join-Path $repoRoot "target\jpackage"
$nsisScript = Join-Path $installerDir "cyvorax.nsi"
$nsisOutput = Join-Path $repoRoot "target\CyvoraX-Suite-Setup.exe"
$sfxArchive = Join-Path $repoRoot "target\CyvoraX-Suite-App.7z"
$maven = Join-Path $repoRoot "..\tools\apache-maven-3.9.14\bin\mvn.cmd"
if (-not (Test-Path -LiteralPath $maven)) {
    $maven = "mvn"
}
$script:javaFxJmodsPath = $null

function Stop-WithMessage {
    param([Parameter(Mandatory = $true)][string]$Message)

    Write-Host $Message -ForegroundColor Red
    exit 1
}

function Assert-DirectoryInsideRepo {
    param([Parameter(Mandatory = $true)][string]$Path)

    $resolvedRepo = [System.IO.Path]::GetFullPath($repoRoot)
    $resolvedPath = [System.IO.Path]::GetFullPath($Path)
    if (-not $resolvedPath.StartsWith($resolvedRepo, [System.StringComparison]::OrdinalIgnoreCase)) {
        throw "Refusing to remove path outside repository: $resolvedPath"
    }
}

function Remove-DirectoryIfExists {
    param([Parameter(Mandatory = $true)][string]$Path)

    if (Test-Path -LiteralPath $Path) {
        Assert-DirectoryInsideRepo -Path $Path
        Get-ChildItem -Path $Path -Recurse -Force -ErrorAction SilentlyContinue | ForEach-Object {
            if ($_.IsReadOnly) {
                $_.IsReadOnly = $false
            }
        }
        $item = Get-Item -LiteralPath $Path -Force
        if ($item.IsReadOnly) {
            $item.IsReadOnly = $false
        }
        Remove-Item -Recurse -Force -LiteralPath $Path
    }
}

function Resolve-Tool {
    param(
        [Parameter(Mandatory = $true)][string]$JavaHome,
        [Parameter(Mandatory = $true)][string]$ToolName
    )

    $toolPath = Join-Path $JavaHome "bin\$ToolName.exe"
    if (Test-Path -LiteralPath $toolPath) {
        return $toolPath
    }
    return $ToolName
}

function Find-JavaFxJmods {
    $candidates = @()
    if (-not [string]::IsNullOrWhiteSpace($JavaFxJmods)) {
        $candidates += $JavaFxJmods
    }
    if (-not [string]::IsNullOrWhiteSpace($env:JAVA_HOME)) {
        $candidates += (Join-Path $env:JAVA_HOME "jmods")
    }
    $candidates += (Join-Path $repoRoot "tools\javafx-jmods\jmods")
    $candidates += (Join-Path $repoRoot "tools\javafx-jmods-21.0.6\jmods")

    $toolCandidates = Get-ChildItem -Path (Join-Path $repoRoot "tools") -Directory -ErrorAction SilentlyContinue |
        Where-Object { $_.Name -like "javafx-jmods*" -or $_.Name -like "openjfx-*jmods*" }
    foreach ($directory in $toolCandidates) {
        $candidates += $directory.FullName
        $nested = Get-ChildItem -Path $directory.FullName -Directory -Recurse -ErrorAction SilentlyContinue |
            Where-Object { Test-Path -LiteralPath (Join-Path $_.FullName "javafx.controls.jmod") } |
            Select-Object -First 1
        if ($nested) {
            $candidates += $nested.FullName
        }
    }

    foreach ($candidate in $candidates) {
        if ([string]::IsNullOrWhiteSpace($candidate)) {
            continue
        }
        if (Test-Path -LiteralPath (Join-Path $candidate "javafx.controls.jmod")) {
            return [System.IO.Path]::GetFullPath($candidate)
        }
    }
    return $null
}

function Test-PackagingPrerequisites {
    if ([string]::IsNullOrWhiteSpace($env:JAVA_HOME)) {
        Stop-WithMessage "JAVA_HOME is not set. Set JAVA_HOME to a Java 17+ JDK before packaging."
    }

    $jmods = Join-Path $env:JAVA_HOME "jmods"
    if (-not (Test-Path -LiteralPath (Join-Path $jmods "java.base.jmod"))) {
        Stop-WithMessage "JAVA_HOME does not point to a JDK with jmods. Set JAVA_HOME to Java 17+ before packaging."
    }

    $script:javaFxJmodsPath = Find-JavaFxJmods
    if (-not $script:javaFxJmodsPath) {
        Stop-WithMessage "JavaFX jmods not found. Download JavaFX full SDK from https://gluonhq.com/products/javafx/ and set JAVA_HOME to the JDK+JavaFX combined path or set JAVAFX_JMODS to the JavaFX jmods folder."
    }
}

function New-CyvoraXRuntimeImage {
    $jlink = Resolve-Tool -JavaHome $env:JAVA_HOME -ToolName "jlink"
    $jmods = Join-Path $env:JAVA_HOME "jmods"
    $modulePath = @($jmods, $script:javaFxJmodsPath) -join [System.IO.Path]::PathSeparator
    $modules = @(
        "java.base",
        "java.desktop",
        "java.logging",
        "java.naming",
        "java.net.http",
        "java.sql",
        "java.security.jgss",
        "jdk.crypto.ec",
        "jdk.unsupported",
        "javafx.controls",
        "javafx.fxml",
        "javafx.graphics",
        "javafx.base",
        "javafx.swing"
    ) -join ","

    Remove-DirectoryIfExists -Path $runtimeImage

    & $jlink `
        --module-path $modulePath `
        --add-modules $modules `
        --output $runtimeImage `
        --strip-debug `
        --no-man-pages `
        --no-header-files `
        --compress=2

    if ($LASTEXITCODE -ne 0) {
        throw "jlink failed with exit code $LASTEXITCODE."
    }
}

function Sign-JPackageExecutables {
    param(
        [Parameter(Mandatory = $true)]
        [string]$OutputDirectory
    )

    try {
        $certSubject = "CN=CyvoraX Suite"
        $cert = Get-ChildItem -Path Cert:\CurrentUser\My | Where-Object {
            $_.Subject -eq $certSubject -and
            ($_.EnhancedKeyUsageList | Where-Object {
                $_.ObjectId -eq "1.3.6.1.5.5.7.3.3" -or $_.FriendlyName -eq "Code Signing"
            })
        } | Sort-Object NotAfter -Descending | Select-Object -First 1

        if (-not $cert) {
            $cert = New-SelfSignedCertificate `
                -Type CodeSigning `
                -Subject $certSubject `
                -KeyUsage DigitalSignature `
                -FriendlyName "CyvoraX Suite Code Signing" `
                -CertStoreLocation Cert:\CurrentUser\My `
                -TextExtension @("2.5.29.37={text}1.3.6.1.5.5.7.3.3")
        }

        $exeFiles = Get-ChildItem -Path $OutputDirectory -Recurse -Filter "*.exe" -File -ErrorAction Stop
        foreach ($exe in $exeFiles) {
            try {
                if ($exe.IsReadOnly) {
                    $exe.IsReadOnly = $false
                }
                $signature = Set-AuthenticodeSignature -FilePath $exe.FullName -Certificate $cert -ErrorAction Stop
                if ($signature.SignerCertificate -and $signature.SignerCertificate.Thumbprint -eq $cert.Thumbprint) {
                    Write-Host "Signed: $($exe.FullName)"
                } else {
                    Write-Warning "Signing failed: $($exe.FullName)"
                }
            } catch {
                Write-Warning "Signing failed: $($exe.FullName) ($($_.Exception.Message))"
            }
        }
    } catch {
        Write-Warning "Signing failed: $($_.Exception.Message)"
    }
}

function Resolve-Makensis {
    $cmd = Get-Command "makensis.exe" -ErrorAction SilentlyContinue
    if ($cmd) {
        return $cmd.Source
    }

    $portable = Get-ChildItem -Path (Join-Path $repoRoot "tools") -Filter "makensis.exe" -Recurse -File -ErrorAction SilentlyContinue |
        Select-Object -First 1
    if ($portable) {
        return $portable.FullName
    }

    $defaultPath = "C:\Program Files (x86)\NSIS\makensis.exe"
    if (Test-Path -LiteralPath $defaultPath) {
        return $defaultPath
    }

    return $null
}

function Resolve-SevenZip {
    $cmd = Get-Command "7z.exe" -ErrorAction SilentlyContinue
    if ($cmd) {
        return $cmd.Source
    }

    $defaultPath = "C:\Program Files\7-Zip\7z.exe"
    if (Test-Path -LiteralPath $defaultPath) {
        return $defaultPath
    }

    return $null
}

function Resolve-SevenZipSfx {
    $defaultPath = "C:\Program Files\7-Zip\7z.sfx"
    if (Test-Path -LiteralPath $defaultPath) {
        return $defaultPath
    }

    $toolSfx = Get-ChildItem -Path (Join-Path $repoRoot "tools") -Filter "7z.sfx" -Recurse -File -ErrorAction SilentlyContinue |
        Select-Object -First 1
    if ($toolSfx) {
        return $toolSfx.FullName
    }

    return $null
}

function Append-BinaryFile {
    param(
        [Parameter(Mandatory = $true)][string]$Output,
        [Parameter(Mandatory = $true)][byte[]]$Bytes
    )

    $stream = [System.IO.File]::Open($Output, [System.IO.FileMode]::Append, [System.IO.FileAccess]::Write)
    try {
        $stream.Write($Bytes, 0, $Bytes.Length)
    } finally {
        $stream.Dispose()
    }
}

function Append-FileBytes {
    param(
        [Parameter(Mandatory = $true)][string]$Output,
        [Parameter(Mandatory = $true)][string]$InputFile
    )

    $inputStream = [System.IO.File]::OpenRead($InputFile)
    $outputStream = [System.IO.File]::Open($Output, [System.IO.FileMode]::Append, [System.IO.FileAccess]::Write)
    try {
        $inputStream.CopyTo($outputStream)
    } finally {
        $inputStream.Dispose()
        $outputStream.Dispose()
    }
}

function Build-SfxInstaller {
    $sevenZip = Resolve-SevenZip
    $sfx = Resolve-SevenZipSfx
    if (-not $sevenZip -or -not $sfx) {
        Write-Host "7-Zip not found; skipping self-extracting fallback installer."
        return
    }

    $appImage = Join-Path $dest $appName
    if (-not (Test-Path -LiteralPath $appImage)) {
        throw "App image not found: $appImage"
    }

    if (Test-Path -LiteralPath $sfxArchive) {
        Remove-Item -Force -LiteralPath $sfxArchive
    }
    if (Test-Path -LiteralPath $nsisOutput) {
        Remove-Item -Force -LiteralPath $nsisOutput
    }

    Push-Location $appImage
    try {
        & $sevenZip a -t7z $sfxArchive ".\*" -mx=9 -r | Out-Host
    } finally {
        Pop-Location
    }
    if ($LASTEXITCODE -ne 0) {
        throw "7-Zip archive build failed with exit code $LASTEXITCODE."
    }

    Copy-Item -Force -LiteralPath $sfx -Destination $nsisOutput
    $config = @"
;!@Install@!UTF-8!
Title="CyvoraX Suite $appVersion"
BeginPrompt="Install CyvoraX Suite $appVersion?"
InstallPath="%LocalAppData%\\CyvoraX Suite"
RunProgram="CyvoraX Suite.exe"
GUIMode="2"
;!@InstallEnd@!
"@
    Append-BinaryFile -Output $nsisOutput -Bytes ([System.Text.Encoding]::UTF8.GetBytes($config))
    Append-FileBytes -Output $nsisOutput -InputFile $sfxArchive
    Write-Host "SFX installer built: target\CyvoraX-Suite-Setup.exe"
    Sign-JPackageExecutables -OutputDirectory (Join-Path $repoRoot "target")
}

function Build-NsisInstaller {
    $makensis = Resolve-Makensis
    if (-not $makensis) {
        Write-Host "Install NSIS from https://nsis.sourceforge.io to build the setup wizard installer"
        Build-SfxInstaller
        return
    }

    if (Test-Path -LiteralPath $nsisOutput) {
        Remove-Item -Force -LiteralPath $nsisOutput
    }

    & $makensis "/DAPP_VERSION=$appVersion" $nsisScript
    if ($LASTEXITCODE -ne 0) {
        throw "makensis failed with exit code $LASTEXITCODE."
    }

    Write-Host "NSIS installer built: target\CyvoraX-Suite-Setup.exe"
    Sign-JPackageExecutables -OutputDirectory (Join-Path $repoRoot "target")
}

Test-PackagingPrerequisites

Push-Location $repoRoot
try {
    Remove-DirectoryIfExists -Path (Join-Path $repoRoot "target")

    & $maven package
    if ($LASTEXITCODE -ne 0) {
        throw "Maven build failed with exit code $LASTEXITCODE."
    }

    Remove-DirectoryIfExists -Path $appInput
    Remove-DirectoryIfExists -Path $appContent
    Remove-DirectoryIfExists -Path $dest

    New-Item -ItemType Directory -Force -Path $appInput, $appContent | Out-Null
    Copy-Item -Force -LiteralPath (Join-Path $repoRoot "target\$mainJar") -Destination (Join-Path $appInput $mainJar)
    if (Test-Path -LiteralPath (Join-Path $repoRoot "target\lib")) {
        Copy-Item -Recurse -Force -LiteralPath (Join-Path $repoRoot "target\lib") -Destination (Join-Path $appInput "lib")
    }

    $sourceTools = Join-Path $repoRoot "tools"
    $appTools = Join-Path $appContent "tools"
    if (Test-Path -LiteralPath $sourceTools) {
        New-Item -ItemType Directory -Force -Path $appTools | Out-Null
        foreach ($toolName in @("ffuf", "katana")) {
            $toolPath = Join-Path $sourceTools $toolName
            if (Test-Path -LiteralPath $toolPath) {
                Copy-Item -Recurse -Force -LiteralPath $toolPath -Destination (Join-Path $appTools $toolName)
            }
        }
    }

    New-CyvoraXRuntimeImage

    $jpackage = Resolve-Tool -JavaHome $env:JAVA_HOME -ToolName "jpackage"
    $jpackageArgs = @(
        "--type", "app-image",
        "--name", $appName,
        "--app-version", $appVersion,
        "--input", $appInput,
        "--main-jar", $mainJar,
        "--main-class", "com.venomproxy.Main",
        "--runtime-image", $runtimeImage,
        "--dest", $dest
    )
    if (Test-Path -LiteralPath $appTools) {
        $jpackageArgs += @("--app-content", $appTools)
    }
    if (Test-Path -LiteralPath $iconPath) {
        $jpackageArgs += @("--icon", $iconPath)
    }
    if (Test-Path -LiteralPath $resourceDir) {
        $jpackageArgs += @("--resource-dir", $resourceDir)
    }

    & $jpackage @jpackageArgs
    if ($LASTEXITCODE -ne 0) {
        throw "jpackage failed with exit code $LASTEXITCODE."
    }

    $resolvedDest = (Resolve-Path -LiteralPath $dest).Path
    Sign-JPackageExecutables -OutputDirectory $resolvedDest

    if ($Installer) {
        Build-NsisInstaller
    }
} finally {
    Pop-Location
}
