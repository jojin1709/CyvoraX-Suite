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
$dest = Join-Path $repoRoot "dist"
$nsisScript = Join-Path $installerDir "cyvorax.nsi"
$setupFileName = "CyvoraX-Setup-$appVersion.exe"
$nsisOutput = Join-Path $repoRoot "dist\$setupFileName"
$maven = $null
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
        try {
            Remove-Item -Recurse -Force -LiteralPath $Path -ErrorAction Stop
        } catch {
            Write-Warning "Non-fatal cleanup warning for path ${Path}: $($_.Exception.Message)"
        }
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

function Resolve-Maven {
    $candidates = @()
    if (-not [string]::IsNullOrWhiteSpace($env:MAVEN_HOME)) {
        $candidates += (Join-Path $env:MAVEN_HOME "bin\mvn.cmd")
    }
    $candidates += (Join-Path $repoRoot "tools\apache-maven-3.9.14\bin\mvn.cmd")
    $candidates += (Join-Path $repoRoot "..\tools\apache-maven-3.9.14\bin\mvn.cmd")
    $candidates += "C:\Program Files\JetBrains\IntelliJ IDEA Community Edition 2025.2\plugins\maven\lib\maven3\bin\mvn.cmd"

    foreach ($candidate in $candidates) {
        if (Test-Path -LiteralPath $candidate) {
            return $candidate
        }
    }

    $command = Get-Command "mvn.cmd" -ErrorAction SilentlyContinue
    if ($command) {
        return $command.Source
    }
    $command = Get-Command "mvn" -ErrorAction SilentlyContinue
    if ($command) {
        return $command.Source
    }

    throw "Maven was not found. Install Maven, set MAVEN_HOME, or run from an environment with mvn on PATH."
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

    if (-not (Test-Path -LiteralPath $runtimeImage)) {
        $script:javaFxJmodsPath = Find-JavaFxJmods
        if (-not $script:javaFxJmodsPath) {
            Stop-WithMessage "JavaFX jmods not found. Download JavaFX full SDK from https://gluonhq.com/products/javafx/ and set JAVA_HOME to the JDK+JavaFX combined path or set JAVAFX_JMODS to the JavaFX jmods folder."
        }
    }
}

function New-CyvoraXRuntimeImage {
    if (Test-Path -LiteralPath $runtimeImage) {
        Write-Host "Using existing runtime image: $runtimeImage" -ForegroundColor Green
        return
    }
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

function Build-NsisInstaller {
    $makensis = Resolve-Makensis
    if (-not $makensis) {
        throw "NSIS is required to build the upgrade-aware CyvoraX setup wizard. Install NSIS from https://nsis.sourceforge.io and rerun .\package-windows.ps1 -Installer."
    }

    if (Test-Path -LiteralPath $nsisOutput) {
        Remove-Item -Force -LiteralPath $nsisOutput
    }

    & $makensis "/DAPP_VERSION=$appVersion" "/DPROJECT_DIR=$repoRoot" "/DINSTALLER_DIR=$installerDir" $nsisScript
    if ($LASTEXITCODE -ne 0) {
        throw "makensis failed with exit code $LASTEXITCODE."
    }

    if (-not (Test-Path -LiteralPath $nsisOutput)) {
        throw "Expected NSIS installer was not created: $nsisOutput"
    }

    Write-Host "NSIS installer built: target\$setupFileName"
    Sign-JPackageExecutables -OutputDirectory (Join-Path $repoRoot "target")
}

Test-PackagingPrerequisites
$maven = Resolve-Maven

Push-Location $repoRoot
try {
    try { Remove-DirectoryIfExists -Path (Join-Path $repoRoot "target\app-input") } catch {}
    try { Remove-DirectoryIfExists -Path (Join-Path $repoRoot "target\app-content") } catch {}
    try { Remove-DirectoryIfExists -Path (Join-Path $repoRoot "target\jpackage") } catch {}

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
        "--main-class", "com.venomproxy.Launcher",
        "--runtime-image", $runtimeImage,
        "--dest", $dest
    )
    if (Test-Path -LiteralPath $iconPath) {
        $jpackageArgs += @("--icon", $iconPath)
    }
    if (Test-Path -LiteralPath $resourceDir) {
        $jpackageArgs += @("--resource-dir", $resourceDir)
    }

    $appImageRoot = Join-Path $dest $appName
    Remove-DirectoryIfExists -Path $appImageRoot
    if (Test-Path -LiteralPath $appImageRoot) {
        $timestamp = Get-Date -Format "yyyyMMdd-HHmmss"
        $dest = Join-Path $repoRoot "target\jpackage-$timestamp"
        $appImageRoot = Join-Path $dest $appName
        $jpackageArgs = @(
            "--type", "app-image",
            "--name", $appName,
            "--app-version", $appVersion,
            "--input", $appInput,
            "--main-jar", $mainJar,
            "--main-class", "com.venomproxy.Launcher",
            "--runtime-image", $runtimeImage,
            "--dest", $dest
        )
        if (Test-Path -LiteralPath $iconPath) {
            $jpackageArgs += @("--icon", $iconPath)
        }
        if (Test-Path -LiteralPath $resourceDir) {
            $jpackageArgs += @("--resource-dir", $resourceDir)
        }
    }

    & $jpackage @jpackageArgs
    if ($LASTEXITCODE -ne 0) {
        throw "jpackage failed with exit code $LASTEXITCODE."
    }

    $appImageRoot = Join-Path $dest $appName
    if (Test-Path -LiteralPath $appTools) {
        $targetTools = Join-Path $appImageRoot "tools"
        Remove-DirectoryIfExists -Path $targetTools
        Copy-Item -Recurse -Force -LiteralPath $appTools -Destination $targetTools
    }

    $resolvedDest = (Resolve-Path -LiteralPath $dest).Path
    Sign-JPackageExecutables -OutputDirectory $resolvedDest

    if ($Installer) {
        Build-NsisInstaller
    }
} finally {
    Pop-Location
}
