param(
    [Parameter(Mandatory)][string]$Uri,
    [Parameter(Mandatory)][string]$User,
    [string]$Text = '',
    [switch]$MineOnly,
    [switch]$IncludeResolved,
    [string]$Jdk8 = 'C:\Program Files\Eclipse Adoptium\jdk-8.0.482.8-hotspot',
    [string]$Plugins = 'C:\Program Files\IBM\IBMIMShared\plugins',
    [string]$Bridge = (Join-Path $PSScriptRoot '..\bridge\build\libs\tributary-bridge.jar')
)

$secure = Read-Host -AsSecureString "Password for $User @ $Uri"
$bstr = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($secure)
$password = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($bstr)
[Runtime.InteropServices.Marshal]::ZeroFreeBSTR($bstr)

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

$login = @{ jsonrpc = '2.0'; id = 1; method = 'login'; params = @{ uri = $Uri; user = $User; password = $password } } | ConvertTo-Json -Compress -Depth 4
$search = @{ jsonrpc = '2.0'; id = 2; method = 'searchWorkItems'; params = @{ text = $Text; mineOnly = [bool]$MineOnly; includeResolved = [bool]$IncludeResolved; max = 20 } } | ConvertTo-Json -Compress -Depth 4
$watch = [Diagnostics.Stopwatch]::StartNew()
$process.StandardInput.WriteLine($login)
$process.StandardInput.WriteLine($search)
$process.StandardInput.WriteLine('{"jsonrpc":"2.0","id":99,"method":"shutdown"}')
$process.StandardInput.Close()
$password = $null
if (-not $process.WaitForExit(180000)) { $process.Kill(); 'TIMEOUT' }
"--- bridge output ($($watch.ElapsedMilliseconds) ms total, includes JVM start and login)"
$process.StandardOutput.ReadToEnd()
"--- bridge log (last lines)"
($process.StandardError.ReadToEnd() -split "`n" | Select-Object -Last 8) -join "`n"
