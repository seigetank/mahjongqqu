[CmdletBinding()]
param()

$ErrorActionPreference = "Stop"

$root = git rev-parse --show-toplevel 2>$null
if (-not $root) {
    throw "Run this script from inside the project Git repository."
}

$root = (Resolve-Path $root).Path
$blockedPatterns = @(
    ("C:" + "\Users\"),
    ("C:" + "/Users/"),
    ("ANDROID" + "_HOME="),
    ("ANDROID" + "_SDK_ROOT="),
    ("JAVA" + "_HOME=")
)

$ignoredDirectorySegments = @(
    ".git",
    ".gradle",
    ".kotlin",
    "build"
)

$ignoredDirectoryPrefixes = @(
    "codex/conversations"
)

$ignoredFiles = @(
    "gradlew",
    "gradlew.bat"
)

$files = Get-ChildItem -LiteralPath $root -Recurse -File | Where-Object {
    $path = $_.FullName
    if ($ignoredFiles -contains $_.Name) {
        return $false
    }

    $relativePath = $path.Substring($root.Length).TrimStart('\', '/').Replace('\', '/')
    $segments = $relativePath -split '/'
    foreach ($ignored in $ignoredDirectorySegments) {
        if ($segments -contains $ignored) {
            return $false
        }
    }
    foreach ($ignored in $ignoredDirectoryPrefixes) {
        if ($relativePath -eq $ignored -or $relativePath.StartsWith("$ignored/")) {
            return $false
        }
    }
    return $true
}

$matches = @()
foreach ($file in $files) {
    foreach ($pattern in $blockedPatterns) {
        $found = Select-String -LiteralPath $file.FullName -SimpleMatch -Pattern $pattern -ErrorAction SilentlyContinue
        if ($found) {
            $matches += $found
        }
    }
}

if ($matches.Count -gt 0) {
    $matches | ForEach-Object {
        Write-Error "$($_.Path):$($_.LineNumber): contains non-portable path or local env assignment"
    }
    exit 1
}

Write-Host "Portable path check passed."
