[CmdletBinding()]
param(
    [int]$Port = 8080
)

$ErrorActionPreference = "Stop"
$env:PORT = "$Port"
& (Join-Path (git rev-parse --show-toplevel) "gradlew.bat") :server:run --no-daemon
