param(
    [switch]$Confirm,
    [string]$Scm = 'C:\Program Files\IBM\TeamConcert\scmtools\eclipse\scm.exe',
    [string]$Config = (Join-Path $PSScriptRoot 'validation-local.json')
)

$settings = Get-Content $Config -Raw | ConvertFrom-Json
$names = @($settings.workspaces | ForEach-Object { $_.name })
Write-Host "Workspaces to delete: $($names -join ', ')"
Write-Host "Sandboxes to remove : $(($settings.workspaces | ForEach-Object { $_.sandbox } | Where-Object { $_ }) -join ', ')"
if (-not $Confirm) {
    Write-Host 'Dry run. Pass -Confirm to unload, delete and remove.'
    return
}

Set-Location $env:USERPROFILE
$all = & $Scm -nl en --non-interactive list workspaces -r $settings.repository -j -m 5000 | ConvertFrom-Json
foreach ($entry in $settings.workspaces) {
    $match = @($all | Where-Object { $_.name -eq $entry.name })
    if ($match.Count -ne 1) {
        Write-Host "SKIP $($entry.name): found $($match.Count) workspaces with this exact name"
        continue
    }
    $uuid = $match[0].uuid
    if ($entry.sandbox -and (Test-Path $entry.sandbox)) {
        & $Scm -nl en --non-interactive unload -r $settings.repository -w $uuid -d $entry.sandbox -D | Out-Null
        Write-Host "unloaded $($entry.sandbox) (exit $LASTEXITCODE)"
    }
    & $Scm -nl en --non-interactive delete workspace -r $settings.repository $uuid | Out-Null
    Write-Host "deleted $($entry.name) (exit $LASTEXITCODE)"
    if ($entry.sandbox -and (Test-Path $entry.sandbox) -and $entry.sandbox.StartsWith($settings.sandboxRoot)) {
        Remove-Item $entry.sandbox -Recurse -Force
        Write-Host "removed folder $($entry.sandbox)"
    }
}
