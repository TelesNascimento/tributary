param(
    [string]$Jdk8 = 'C:\Program Files\Eclipse Adoptium\jdk-8.0.482.8-hotspot',
    [string]$Plugins = 'C:\Program Files\IBM\IBMIMShared\plugins',
    [string]$Bridge = (Join-Path $PSScriptRoot '..\bridge\build\libs\tributary-bridge.jar'),
    [string[]]$Requests = @(
        '{"jsonrpc":"2.0","id":1,"method":"initialize"}',
        '{"jsonrpc":"2.0","id":2,"method":"searchWorkItems","params":{"text":"x"}}',
        '{"jsonrpc":"2.0","id":3,"method":"nope"}'
    )
)

$jars = Get-Content (Join-Path $PSScriptRoot '..\bridge\classpath.txt') | Where-Object { $_.Trim() } | ForEach-Object { Join-Path $Plugins $_ }
$classpath = (@((Resolve-Path $Bridge).Path) + $jars) -join ';'
$psi = New-Object Diagnostics.ProcessStartInfo
$psi.FileName = Join-Path $Jdk8 'bin\java.exe'
$psi.Arguments = "-cp `"$classpath`" dev.tributary.bridge.Main"
$psi.RedirectStandardInput = $true
$psi.RedirectStandardOutput = $true
$psi.RedirectStandardError = $true
$psi.UseShellExecute = $false
$process = [Diagnostics.Process]::Start($psi)
foreach ($request in $Requests) { $process.StandardInput.WriteLine($request) }
$process.StandardInput.WriteLine('{"jsonrpc":"2.0","id":99,"method":"shutdown"}')
$process.StandardInput.Close()
if (-not $process.WaitForExit(60000)) { $process.Kill(); 'TIMEOUT' }
$process.StandardOutput.ReadToEnd()
"exit=$($process.ExitCode)"
