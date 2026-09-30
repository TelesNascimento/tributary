param(
    [string]$Version = 'latest',
    [string]$PluginsDir,
    [string]$Zip,
    [switch]$AllIdes
)

$ErrorActionPreference = 'Stop'
$repo = 'TelesNascimento/tributary'

function Find-PluginDirs {
    $root = Join-Path $env:APPDATA 'JetBrains'
    if (-not (Test-Path $root)) { return @() }
    Get-ChildItem $root -Directory |
        Where-Object { $_.Name -match '^(IntelliJIdea|IdeaIC)\d{4}\.\d+$' } |
        Sort-Object { [version]($_.Name -replace '^\D+', '') } -Descending |
        ForEach-Object { Join-Path $_.FullName 'plugins' }
}

function Get-ReleaseZip([string]$version, [string]$folder) {
    $api = if ($version -eq 'latest') {
        "https://api.github.com/repos/$repo/releases/latest"
    } else {
        "https://api.github.com/repos/$repo/releases/tags/$version"
    }
    $release = Invoke-RestMethod -Uri $api -Headers @{ 'User-Agent' = 'tributary-installer' }
    $asset = $release.assets | Where-Object { $_.name -like '*.zip' } | Select-Object -First 1
    if (-not $asset) { throw "Release $($release.tag_name) has no plugin zip." }
    $target = Join-Path $folder $asset.name
    Write-Host "Downloading $($asset.name) ($($release.tag_name))"
    Invoke-WebRequest -Uri $asset.browser_download_url -OutFile $target -UseBasicParsing
    $target
}

$targets = @()
if ($PluginsDir) {
    $targets = @($PluginsDir)
} else {
    $found = @(Find-PluginDirs)
    if ($found.Count -eq 0) {
        throw 'No IntelliJ IDEA configuration found. Start the IDE once, or pass -PluginsDir <path>.'
    }
    $targets = if ($AllIdes) { $found } else { @($found[0]) }
}

if (Get-Process -Name idea64 -ErrorAction SilentlyContinue) {
    Write-Warning 'IntelliJ IDEA is running. Close it first, or restart it after this script finishes.'
}

$temp = Join-Path ([IO.Path]::GetTempPath()) ('tributary-install-' + [guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory -Path $temp | Out-Null
try {
    $archive = if ($Zip) { (Resolve-Path $Zip).Path } else { Get-ReleaseZip $Version $temp }
    foreach ($dir in $targets) {
        New-Item -ItemType Directory -Force -Path $dir | Out-Null
        $existing = Join-Path $dir 'tributary'
        if (Test-Path $existing) { Remove-Item $existing -Recurse -Force }
        Expand-Archive -Path $archive -DestinationPath $dir -Force
        Write-Host "Installed to $existing"
    }
} finally {
    Remove-Item $temp -Recurse -Force -ErrorAction SilentlyContinue
}

Write-Host ''
Write-Host 'Done. Restart IntelliJ IDEA, then open a folder that contains a .jazz5 directory.'
Write-Host 'Log in once from a terminal with: scm login -r <server-uri> -u <user>'
