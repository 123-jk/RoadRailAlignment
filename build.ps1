param(
    [string]$JosmJar = ".\lib\josm-tested.jar",
    [string]$OutJar = ".\dist\RoadRailAlignment.jar"
)

$ErrorActionPreference = "Stop"

$root = Split-Path -Parent $MyInvocation.MyCommand.Path
Set-Location -LiteralPath $root

function Get-JdkTool {
    param([string]$ToolName)

    $command = Get-Command $ToolName -ErrorAction SilentlyContinue
    if ($command) {
        return $command.Source
    }

    if ($env:JAVA_HOME) {
        $candidate = Join-Path $env:JAVA_HOME "bin\$ToolName.exe"
        if (Test-Path -LiteralPath $candidate) {
            return $candidate
        }
    }

    $javaRoots = @(
        "C:\Program Files\Java",
        "C:\Program Files\Eclipse Adoptium",
        "C:\Program Files\Microsoft"
    )
    foreach ($javaRoot in $javaRoots) {
        if (-not (Test-Path -LiteralPath $javaRoot)) {
            continue
        }
        $candidate = Get-ChildItem -LiteralPath $javaRoot -Recurse -Filter "$ToolName.exe" -ErrorAction SilentlyContinue |
            Select-Object -First 1
        if ($candidate) {
            return $candidate.FullName
        }
    }

    throw "Unable to find $ToolName.exe. Install a JDK and set JAVA_HOME."
}

$josmJarPath = Resolve-Path -LiteralPath $JosmJar -ErrorAction SilentlyContinue
if (-not $josmJarPath) {
    throw "JOSM jar not found at $JosmJar. Run .\tools\download-josm.ps1 first, or pass -JosmJar <path>."
}

[xml]$pom = Get-Content -LiteralPath ".\pom.xml" -Encoding UTF8 -Raw
$pluginVersion = $pom.project.version
$pluginDate = Get-Date -Format "yyyy-MM-dd"

$javacExe = Get-JdkTool "javac"
$jarExe = Get-JdkTool "jar"

$buildDir = Join-Path $root "build"
$classesDir = Join-Path $buildDir "classes"
$manifestFile = Join-Path $buildDir "MANIFEST.MF"
$distDir = Split-Path -Parent (Join-Path $root $OutJar)

foreach ($path in @($buildDir, $distDir)) {
    if (-not (Test-Path -LiteralPath $path)) {
        New-Item -ItemType Directory -Path $path | Out-Null
    }
}

if (Test-Path -LiteralPath $classesDir) {
    $resolvedRoot = Resolve-Path -LiteralPath $root
    $resolvedClasses = Resolve-Path -LiteralPath $classesDir
    if (-not $resolvedClasses.Path.StartsWith($resolvedRoot.Path)) {
        throw "Refusing to clean classes directory outside workspace."
    }
    Remove-Item -LiteralPath $resolvedClasses.Path -Recurse -Force
}
New-Item -ItemType Directory -Path $classesDir | Out-Null

$javaFiles = Get-ChildItem -LiteralPath ".\src\main\java" -Recurse -Filter "*.java" | ForEach-Object { $_.FullName }
if (-not $javaFiles) {
    throw "No Java sources found."
}

& $javacExe --release 11 -encoding UTF-8 -cp $josmJarPath.Path -d $classesDir $javaFiles
if ($LASTEXITCODE -ne 0) {
    throw "javac failed with exit code $LASTEXITCODE."
}

if (Test-Path -LiteralPath ".\src\main\resources") {
    Copy-Item -Path ".\src\main\resources\*" -Destination $classesDir -Recurse -Force
}

@"
Manifest-Version: 1.0
Plugin-Class: org.openstreetmap.josm.plugins.roadrailalignment.RoadRailPlugin
Plugin-Description: Draw planar road and rail alignments in JOSM.
Plugin-Date: $pluginDate
Plugin-Icon: images/mapmode/roadrailalignment.svg
Plugin-Mainversion: 14134
Plugin-Version: $pluginVersion
Plugin-Canloadatruntime: true

"@ | Set-Content -LiteralPath $manifestFile -Encoding ASCII

if (Test-Path -LiteralPath $OutJar) {
    Remove-Item -LiteralPath $OutJar -Force
}

& $jarExe cfm $OutJar $manifestFile -C $classesDir .
if ($LASTEXITCODE -ne 0) {
    throw "jar failed with exit code $LASTEXITCODE."
}
Write-Host "Built $OutJar"
