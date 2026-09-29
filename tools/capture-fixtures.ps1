param(
    [string]$Scm = 'C:\Program Files\IBM\TeamConcert\scmtools\eclipse\scm.exe',
    [string]$Sandbox,
    [string]$Repository,
    [string]$Workspace,
    [string]$ChangeSet,
    [string]$ProbeFile,
    [string]$Out = (Join-Path $PSScriptRoot '..\src\test\resources\fixtures\raw')
)

$local = Join-Path $PSScriptRoot 'capture-local.json'
if ((Test-Path $local) -and (-not $Sandbox -or -not $Repository)) {
    $config = Get-Content $local -Raw | ConvertFrom-Json
    if (-not $Sandbox) { $Sandbox = $config.sandbox }
    if (-not $Repository) { $Repository = $config.repository }
}
if (-not $Sandbox -or -not $Repository) { throw 'Pass -Sandbox and -Repository or create tools/capture-local.json' }

New-Item -ItemType Directory -Force $Out | Out-Null

function Capture([string]$Name, [string[]]$CommandArgs) {
    $file = Join-Path $Out $Name
    $stdout = & $Scm -nl en --non-interactive @CommandArgs 2> "$file.err"
    $code = $LASTEXITCODE
    [IO.File]::WriteAllLines($file, [string[]]@($stdout), (New-Object Text.UTF8Encoding($false)))
    if ((Get-Item "$file.err").Length -eq 0) { Remove-Item "$file.err" }
    "{0,-34} exit={1}" -f $Name, $code
}

Push-Location $Sandbox
try {
    Capture 'list_connections.json' @('list', 'connections', '-j')
    Capture 'status_clean.json' @('status', '-j', '-N', '-d', $Sandbox)
    Capture 'list_workspaces.json' @('list', 'workspaces', '-r', $Repository, '-j', '-m', '5')
    Capture 'list_projectareas.json' @('list', 'projectareas', '-r', $Repository, '-j')
    Capture 'list_streams.json' @('list', 'streams', '-r', $Repository, '-j', '-m', '5')
    if ($Workspace) {
        Capture 'list_components.json' @('list', 'components', '-r', $Repository, '-j', $Workspace)
        Capture 'list_changesets.json' @('list', 'changesets', '-r', $Repository, '-j', '-m', '3', '-w', $Workspace)
        Capture 'list_flowtargets.json' @('list', 'flowtargets', '-r', $Repository, '-j', $Workspace)
    }
    if ($ChangeSet) {
        Capture 'list_changes.json' @('list', 'changes', '-r', $Repository, '-j', $ChangeSet)
    }
    if ($ProbeFile) {
        Capture 'show_history.json' @('show', 'history', '-r', $Repository, '-j', '-m', '5', '-d', $Sandbox, $ProbeFile)
        Capture 'annotate.json' @('annotate', '-j', '-d', $Sandbox, $ProbeFile)
    }
} finally {
    Pop-Location
}
