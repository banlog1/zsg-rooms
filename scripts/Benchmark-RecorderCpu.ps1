param(
    [string]$JdkDirectory = '',
    [string]$Recording = '',
    [string]$BaselineBlob = '1eb8ea77e89555a834252571c963f150879f54e9',
    [string]$OutputDirectory = 'run/recorder-cpu'
)
$ErrorActionPreference = 'Stop'
$repository = Split-Path -Parent $PSScriptRoot
if (!$JdkDirectory) { $JdkDirectory = Split-Path -Parent (Split-Path -Parent (Get-Command javac).Source) }
$compiler = Join-Path $JdkDirectory 'bin/javac.exe'
$runtime = Join-Path $JdkDirectory 'bin/java.exe'
if (![IO.Path]::IsPathRooted($OutputDirectory)) { $OutputDirectory = Join-Path $repository $OutputDirectory }
if ($Recording) { $Recording = (Resolve-Path -LiteralPath $Recording).Path }
New-Item -ItemType Directory -Path $OutputDirectory -Force | Out-Null
$baseline = & git -C $repository show $BaselineBlob
if ($LASTEXITCODE -ne 0) { throw 'Cannot read original HUD source from Git.' }
$utf8 = New-Object Text.UTF8Encoding($false)
foreach ($version in @('before', 'after')) {
    $directory = Join-Path $OutputDirectory $version
    New-Item -ItemType Directory -Path $directory -Force | Out-Null
    $source = Join-Path $directory 'ReplayHudTrack.java'
    if ($version -eq 'before') { [IO.File]::WriteAllText($source, ($baseline -join "`n"), $utf8) }
    else { Copy-Item -LiteralPath (Join-Path $repository 'src/main/java/zsgrooms/modid/replay/ReplayHudTrack.java') -Destination $source }
    & $compiler --release 8 -Xlint:-options -d $directory $source (Join-Path $PSScriptRoot 'ReplayHudCpuBenchmark.java')
    if ($LASTEXITCODE -ne 0) { throw "Compilation failed: $version" }
}
Write-Output "Baseline HUD blob: $BaselineBlob"
Get-FileHash -Algorithm SHA256 -LiteralPath (Join-Path $OutputDirectory 'after/ReplayHudTrack.java') | Format-List
$index = 0
foreach ($version in @('before', 'after', 'after', 'before')) {
    $index++
    $log = Join-Path $OutputDirectory "$index-$version.log"
    Write-Output "Starting fork $index ($version), output: $log"
    $arguments = @('-Xms512m', '-Xmx512m', '-cp', (Join-Path $OutputDirectory $version),
        'zsgrooms.modid.replay.ReplayHudCpuBenchmark', $version)
    if ($Recording) { $arguments += $Recording }
    & $runtime @arguments > $log
    if ($LASTEXITCODE -ne 0) { throw "Benchmark failed: $log" }
    Get-Content -LiteralPath $log | Where-Object { $_ -match '^(JVM=|FIXTURE |RESULT )' }
}
