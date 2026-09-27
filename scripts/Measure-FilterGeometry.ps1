[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)][string]$OutputDirectory,
    [ValidateRange(1, 1000000000)][long]$Families = 10000000,
    [ValidateRange(0, 281474976710655)][long]$Stream = 1537660560030,
    [string]$Compiler = 'gcc'
)

$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
$source = Join-Path $root 'run/filter-reference/cubiomes'
$revision = & git -C $source rev-parse HEAD
if ($LASTEXITCODE -ne 0 -or $revision -cne 'e61f90580cbdd883214a8054670dacae655e59c0') {
    throw 'Expected the reviewed Cubiomes checkout.'
}
$dirty = & git -C $source status --porcelain --untracked-files=all
if ($LASTEXITCODE -ne 0 -or $dirty) { throw 'Cubiomes checkout must be clean.' }
if (Test-Path -LiteralPath $OutputDirectory) { throw 'Use a fresh diagnostic directory.' }
$null = New-Item -ItemType Directory -Path $OutputDirectory
$directory = (Resolve-Path -LiteralPath $OutputDirectory).Path
$files = @('noise.c', 'biomes.c', 'layers.c', 'biomenoise.c', 'generator.c', 'finders.c', 'util.c', 'quadbase.c') |
    ForEach-Object { Join-Path $source $_ }
$exe = Join-Path $directory 'geometry-benchmark.exe'
& $Compiler '-O3' '-std=c99' '-Wall' '-Wextra' '-fwrapv' '-ffp-contract=off' '-I' $source `
    (Join-Path $root 'tools/filter-worker/geometry_benchmark.c') @files '-lm' '-o' $exe
if ($LASTEXITCODE -ne 0) { throw 'Diagnostic build failed.' }
$inputs = @(Get-ChildItem (Join-Path $root 'tools/filter-worker') -File |
    Where-Object { $_.Extension -in @('.c', '.h') } | Get-FileHash | Select-Object Path,Hash)
[IO.File]::WriteAllText((Join-Path $directory 'runtime.json'), (@{
    cubiomesRevision = $revision; inputs = $inputs; executableHash = (Get-FileHash $exe).Hash
    compiler = @(& $Compiler --version); families = $Families; stream = $Stream
    flags = '-O3 -std=c99 -Wall -Wextra -fwrapv -ffp-contract=off'
    scope = 'Isolated geometry and emulated clock bookkeeping, not full search'
} | ConvertTo-Json -Depth 5), [Text.UTF8Encoding]::new($false))
$results = @(& $exe $Families $Stream)
$code = $LASTEXITCODE
[IO.File]::WriteAllLines((Join-Path $directory 'results.jsonl'), [string[]]$results, [Text.UTF8Encoding]::new($false))
if ($code -ne 0) { throw "Diagnostic failed: $code" }
$results
Write-Output 'PASS: all six passes matched counts and coordinate digests.'
