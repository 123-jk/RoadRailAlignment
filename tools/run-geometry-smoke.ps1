$ErrorActionPreference = "Stop"

$root = Split-Path -Parent (Split-Path -Parent $MyInvocation.MyCommand.Path)
Set-Location -LiteralPath $root

mvn test-compile
if ($LASTEXITCODE -ne 0) {
    throw "mvn test-compile failed."
}

$classpath = ".\target\test-classes;.\target\classes;.\lib\josm-tested.jar"
java -ea -cp $classpath org.openstreetmap.josm.plugins.roadrailalignment.geometry.GeometrySmokeTest
if ($LASTEXITCODE -ne 0) {
    throw "Geometry smoke tests failed."
}

