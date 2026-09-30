param(
    [string]$Scm = 'C:\Program Files\IBM\TeamConcert\scmtools\eclipse\scm.exe',
    [string]$Out = (Join-Path $PSScriptRoot '..\docs\cli-reference')
)

New-Item -ItemType Directory -Force $Out | Out-Null
Get-ChildItem $Out -File | Remove-Item -Force

function Get-Help([string[]]$Path) {
    $text = (& $Scm -nl en help @Path 2>&1 | Out-String)
    return $text
}

function Get-SubCommands([string]$Text) {
    $subs = @()
    $inBlock = $false
    foreach ($line in ($Text -split "`r?`n")) {
        if ($line -match '^Subcommands:') { $inBlock = $true; continue }
        if ($inBlock) {
            if ($line -match '^\s{2}(\S+)\s+-') { $subs += $Matches[1] }
            elseif ($line -match '^\S') { $inBlock = $false }
        }
    }
    return $subs
}

$root = Get-Help @()
[IO.File]::WriteAllText((Join-Path $Out '_root.txt'), $root, [Text.Encoding]::UTF8)
$commands = @()
foreach ($line in ($root -split "`r?`n")) {
    if ($line -match '^\s{2}([a-z][a-z-]*)\s+-\s') { $commands += $Matches[1] }
}
$index = @()
foreach ($command in ($commands | Where-Object { $_ -ne 'command' } | Sort-Object -Unique)) {
    $text = Get-Help @($command)
    [IO.File]::WriteAllText((Join-Path $Out "$command.txt"), $text, [Text.Encoding]::UTF8)
    $subs = Get-SubCommands $text
    $index += "${command}: $($subs -join ', ')"
    foreach ($sub in $subs) {
        $subText = Get-Help @($command, $sub)
        [IO.File]::WriteAllText((Join-Path $Out "${command}_$sub.txt"), $subText, [Text.Encoding]::UTF8)
    }
}
$index | Out-File (Join-Path $Out '_index.txt') -Encoding utf8
"Wrote $((Get-ChildItem $Out -File).Count) files to $Out"
