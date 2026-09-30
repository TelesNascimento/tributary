$Rest = [string[]]$args
$dataDir = Join-Path $PSScriptRoot 'data'
$args2 = @($Rest | Where-Object { $_ -notin '--non-interactive', '--no-mask' })
if ($args2.Count -ge 2 -and $args2[0] -eq '-nl') { $args2 = @($args2 | Select-Object -Skip 2) }
if ($args2.Count -eq 0) { [Console]::Error.WriteLine('no command'); exit 1 }

$command = $args2[0]
$sub = if ($args2.Count -gt 1) { $args2[1] } else { '' }
$status = Get-Content (Join-Path $dataDir 'status.json') -Raw | ConvertFrom-Json
$lists = Get-Content (Join-Path $dataDir 'lists.json') -Raw | ConvertFrom-Json

function Write-Json($value) { ConvertTo-Json -InputObject $value -Depth 30 }

function Find-ChangeSet([string]$uuid) {
    foreach ($component in $status.workspaces[0].components) {
        foreach ($set in @($component.'outgoing-changes') + @($component.'incoming-changes')) {
            if ($set.uuid -eq $uuid) { return $set }
        }
    }
    return $null
}

switch ($command) {
    'list' {
        switch ($sub) {
            'connections' { Write-Json @($lists.connections) }
            'workspaces' { Write-Json @($lists.workspaces) }
            'projectareas' { Write-Json @($lists.projectareas) }
            'streams' { Write-Json @($lists.streams) }
            'components' { Write-Json @{ workspaces = @(@{ components = @(@{ name = 'orders-service'; uuid = '_DEMOCOMPONENT00000000001' }) }) } }
            'changes' { $set = Find-ChangeSet $args2[-1]; Write-Json @{ changes = @($set) } }
            'flowtargets' { Write-Json @() }
            default { Write-Json @{ changes = @() } }
        }
    }
    'status' { Get-Content (Join-Path $dataDir 'status.json') -Raw }
    'show' {
        if ($sub -eq 'conflicts') { '{"conflicts": []}' } else { '{}' }
    }
    'get' {
        $files = Get-Content (Join-Path $dataDir 'files.json') -Raw | ConvertFrom-Json
        $target = $args2[-1]
        $state = $args2[-2]
        $text = $files.$state
        if ($null -eq $text) { [Console]::Error.WriteLine("Problem running 'get file':`nUnknown state $state"); exit 25 }
        [IO.File]::WriteAllText($target, $text)
        "Successfully extracted file to `"$target`"."
    }
    'diff' {
        $file = $args2[-1]
        $name = Split-Path $file -Leaf
        $patch = Join-Path (Get-Location) ".jazz5\demo\patches\$name.patch"
        if (Test-Path $patch) { Get-Content $patch -Raw } else { '' }
    }
    'login' { 'Login successful.' }
    default { [Console]::Error.WriteLine("Problem running '$command':`nNot available in the demo."); exit 1 }
}
exit 0
