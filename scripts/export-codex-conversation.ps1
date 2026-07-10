[CmdletBinding()]
param(
    [string]$ThreadId = $env:CODEX_THREAD_ID,
    [string]$CodexHome = $(if ($env:CODEX_HOME) { $env:CODEX_HOME } else { Join-Path $HOME ".codex" }),
    [string]$DestinationDir
)

$ErrorActionPreference = "Stop"

function Get-RepositoryRoot {
    $root = git rev-parse --show-toplevel 2>$null
    if (-not $root) {
        throw "Run this script from inside the project Git repository."
    }

    return (Resolve-Path $root).Path
}

function ConvertTo-PortableText {
    param(
        [AllowNull()][string]$Value,
        [string]$RepositoryRoot,
        [string]$HomeRoot
    )

    if ($null -eq $Value) {
        return $null
    }

    $result = $Value
    $paths = @(
        @{ From = $RepositoryRoot; To = '${PROJECT_ROOT}' },
        @{ From = $RepositoryRoot.Replace('\', '/'); To = '${PROJECT_ROOT}' },
        @{ From = $HomeRoot; To = '${USER_HOME}' },
        @{ From = $HomeRoot.Replace('\', '/'); To = '${USER_HOME}' }
    )

    foreach ($path in $paths) {
        if ($path.From) {
            $result = $result.Replace($path.From, $path.To)
        }
    }

    return $result
}

function Get-MessageText {
    param([object]$Message)

    $parts = New-Object System.Collections.Generic.List[string]

    foreach ($content in @($Message.content)) {
        if ($null -eq $content) {
            continue
        }

        if ($content.PSObject.Properties.Name -contains "text" -and $content.text) {
            $parts.Add([string]$content.text)
        }
    }

    return ($parts -join "`n")
}

if (-not $ThreadId) {
    throw "Thread ID was not provided. Run inside Codex Desktop or pass -ThreadId."
}

$repositoryRoot = Get-RepositoryRoot
$homeRoot = (Resolve-Path $HOME).Path

if (-not $DestinationDir) {
    $DestinationDir = Join-Path $repositoryRoot "codex/conversations"
}

$sessionsDir = Join-Path $CodexHome "sessions"
if (-not (Test-Path -LiteralPath $sessionsDir)) {
    throw "Codex sessions directory was not found: $sessionsDir"
}

$sessionFile = Get-ChildItem -Path $sessionsDir -Recurse -File -Filter "*.jsonl" |
    Where-Object { $_.Name -like "*$ThreadId*" } |
    Sort-Object LastWriteTime -Descending |
    Select-Object -First 1

if (-not $sessionFile) {
    throw "No Codex session JSONL found for thread ID: $ThreadId"
}

$threadName = $null
$sessionIndex = Join-Path $CodexHome "session_index.jsonl"
if (Test-Path -LiteralPath $sessionIndex) {
    foreach ($line in Get-Content -LiteralPath $sessionIndex -Encoding UTF8) {
        if ([string]::IsNullOrWhiteSpace($line)) {
            continue
        }

        try {
            $entry = $line | ConvertFrom-Json
        }
        catch {
            continue
        }

        if ($entry.id -eq $ThreadId) {
            $threadName = $entry.thread_name
            break
        }
    }
}

$messages = New-Object System.Collections.Generic.List[object]

foreach ($line in Get-Content -LiteralPath $sessionFile.FullName -Encoding UTF8) {
    if ([string]::IsNullOrWhiteSpace($line)) {
        continue
    }

    try {
        $record = $line | ConvertFrom-Json
    }
    catch {
        continue
    }

    if ($record.type -ne "response_item") {
        continue
    }

    $item = $record.payload
    if ($null -eq $item -or $item.type -ne "message") {
        continue
    }

    if ($item.role -ne "user" -and $item.role -ne "assistant") {
        continue
    }

    $text = Get-MessageText -Message $item
    if ([string]::IsNullOrWhiteSpace($text)) {
        continue
    }

    $messages.Add([ordered]@{
        timestamp = $record.timestamp
        role = $item.role
        text = ConvertTo-PortableText -Value $text -RepositoryRoot $repositoryRoot -HomeRoot $homeRoot
    })
}

New-Item -ItemType Directory -Force -Path $DestinationDir | Out-Null

$archive = [ordered]@{
    schema = "mahjongqqu.codex-conversation.v1"
    exportedAt = (Get-Date).ToUniversalTime().ToString("o")
    threadId = $ThreadId
    threadName = $threadName
    source = "Filtered Codex Desktop session archive. Internal instructions, tool calls, and tool outputs are omitted."
    workspace = '${PROJECT_ROOT}'
    messageCount = $messages.Count
    messages = $messages
}

$timestamp = Get-Date -Format "yyyyMMdd-HHmmss"
$fileName = "$timestamp-$ThreadId-visible-conversation.json"
$destination = Join-Path $DestinationDir $fileName

$archive |
    ConvertTo-Json -Depth 100 |
    Set-Content -LiteralPath $destination -Encoding UTF8

Write-Host "Exported $($messages.Count) messages to $destination"
