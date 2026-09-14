[CmdletBinding()]
param(
    [ValidateSet('temple', 'village', 'shipwreck')][string]$Type = 'temple',
    [ValidateRange(1, 281474976710655)][long]$Families = 100000000,
    [ValidateRange(1, 65536)][int]$Sisters = 65536,
    [ValidateRange(1, 1000000)][int]$Target = 10,
    [ValidateRange(1, 4)][int]$FamilyCap = 1,
    [ValidateRange(1, 86400)][int]$Seconds = 60,
    [ValidateRange(0, 9223372036854775807)][long]$Stream = 0,
    [string]$OutputFile,
    [string]$Java = 'java',
    [string]$Finder,
    [string]$ModelDirectory
)

$ErrorActionPreference = 'Stop'
# Operator defaults from the bounded policy trials; explicit budgets always win.
if (-not $PSBoundParameters.ContainsKey('Sisters')) {
    $Sisters = switch ($Type) { 'temple' { 4096 }; 'shipwreck' { 16384 }; 'village' { 1024 } }
}
if (-not $PSBoundParameters.ContainsKey('FamilyCap')) {
    $FamilyCap = if ($Type -eq 'shipwreck') { 4 } else { 2 }
}
$root = Split-Path -Parent $PSScriptRoot
$finder = if ($Finder) { (Resolve-Path -LiteralPath $Finder).Path } else { Join-Path $root 'run/filter-worker/seed-finder.exe' }
if (-not (Test-Path -LiteralPath $finder)) { throw 'Build the standalone finder first. See docs/MODEL_SEED_FINDER.md.' }
if (-not $OutputFile) {
    $directory = Join-Path $root 'run/model-bank'
    New-Item -ItemType Directory -Path $directory -Force | Out-Null
    $OutputFile = Join-Path $directory ([guid]::NewGuid().ToString() + '.jsonl')
}
$arguments = @('search', $Type, "$Families", "$Sisters", "$Target", "$Seconds", "$Stream", $OutputFile)
if ($FamilyCap -ne 1) { $arguments += "$FamilyCap" }
$watch = [Diagnostics.Stopwatch]::StartNew()
$installed = if ($ModelDirectory) { (Resolve-Path -LiteralPath $ModelDirectory).Path } else { Join-Path $root 'run/model-finder' }
if (-not (Test-Path -LiteralPath (Join-Path $installed 'classes/zsgrooms/model/ModelFinder.class')) -or
    -not (Test-Path -LiteralPath (Join-Path $installed 'nether/classes/zsgrooms/model/nether/NetherModel.class'))) {
    throw 'Build the standalone model coordinator first. See docs/MODEL_SEED_FINDER.md.'
}
$classpath = (Join-Path $installed 'classes') + [IO.Path]::PathSeparator + (Join-Path $installed 'lib/*')
$netherClasspath = (Join-Path $installed 'nether/classes') + [IO.Path]::PathSeparator + (Join-Path $installed 'nether/lib/*')
$messages = @(& $Java '-Xmx1G' "-Dzsg.netherModelClasspath=$netherClasspath" '-cp' $classpath 'zsgrooms.model.ModelFinder' $finder @arguments)
if ($LASTEXITCODE -ne 0) { throw 'Model search failed. No Minecraft fallback was attempted.' }
$report = [ordered]@{
    type = $Type; familiesLimit = $Families; sistersLimit = $Sisters; familyCap = $FamilyCap; target = $Target
    secondsLimit = $Seconds; streamOffset = $Stream; processWallMs = $watch.ElapsedMilliseconds
    villageModelRevision = 'ee9e0c6c82aeac3fef9b1ffa3f452a2a1eae6339'
    zsgRevision = '073e1d1f3150213c7c3787eda7ef8106cb789817'
    bastionModelRevision = '9cccd19863e941d2dc8d2820037c48d49fe7d526'
    results = @($messages | ForEach-Object { $_ | ConvertFrom-Json })
}
$reportPath = $OutputFile + '.report.json'
$reportStream = [IO.File]::Open($reportPath, [IO.FileMode]::CreateNew, [IO.FileAccess]::Write)
try {
    $writer = [IO.StreamWriter]::new($reportStream, [Text.UTF8Encoding]::new($false))
    try { $writer.Write(($report | ConvertTo-Json -Depth 10)) } finally { $writer.Dispose() }
} finally { $reportStream.Dispose() }
Write-Output $messages
Write-Output "Private model bank: $OutputFile"
Write-Output "Seed-free timing report: $reportPath"
