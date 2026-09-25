param(
    [string]$CubiomesDirectory = 'run/filter-reference/cubiomes',
    [string]$Compiler = 'gcc',
    [switch]$FinderOnly,
    [switch]$TestsOnly
)

$ErrorActionPreference = 'Stop'
$root = Split-Path $PSScriptRoot -Parent
$source = (Resolve-Path -LiteralPath (Join-Path $root $CubiomesDirectory)).Path
$expected = 'e61f90580cbdd883214a8054670dacae655e59c0'
$revision = & git -C $source rev-parse HEAD
if ($LASTEXITCODE -ne 0 -or $revision -ne $expected) {
    throw "Expected reviewed Cubiomes revision $expected. See tools/filter-worker/README.md."
}
$dirty = & git -C $source status --porcelain --untracked-files=all
if ($LASTEXITCODE -ne 0 -or $dirty) { throw 'Cubiomes checkout must be clean for a pinned worker build.' }
$output = Join-Path $root 'run/filter-worker'
New-Item -ItemType Directory -Force -Path $output | Out-Null
$files = @('noise.c', 'biomes.c', 'layers.c', 'biomenoise.c', 'generator.c', 'finders.c', 'util.c', 'quadbase.c') |
    ForEach-Object { Join-Path $source $_ }
if ($TestsOnly) {
    & $Compiler '-O3' '-std=c99' '-Wall' '-Wextra' '-fwrapv' '-ffp-contract=off' '-I' $source `
        (Join-Path $root 'tools/filter-worker/portal_test.c') @files '-lm' '-o' (Join-Path $output 'portal-test.exe')
    if ($LASTEXITCODE -ne 0) { throw 'Portal model test build failed.' }
    & $Compiler '-O3' '-std=c99' '-Wall' '-Wextra' '-fwrapv' '-ffp-contract=off' '-I' $source `
        (Join-Path $root 'tools/filter-worker/mapless_test.c') @files '-lm' '-o' (Join-Path $output 'mapless-test.exe')
    if ($LASTEXITCODE -ne 0) { throw 'Mapless model test build failed.' }
    & $Compiler '-O3' '-std=c99' '-Wall' '-Wextra' '-fwrapv' '-I' $source `
        (Join-Path $root 'tools/filter-worker/model_test.c') @files '-lm' '-o' (Join-Path $output 'model-test.exe')
    if ($LASTEXITCODE -ne 0) { throw 'Standalone model test build failed.' }
    & $Compiler '-O3' '-std=c99' '-Wall' '-Wextra' '-fwrapv' '-I' $source `
        (Join-Path $root 'tools/filter-worker/surface_test.c') @files '-lm' '-o' (Join-Path $output 'surface-test.exe')
    if ($LASTEXITCODE -ne 0) { throw 'Surface cache test build failed.' }
    & $Compiler '-O3' '-std=c99' '-Wall' '-Wextra' '-fwrapv' '-I' $source `
        (Join-Path $root 'tools/filter-worker/surface_query_test.c') @files '-lm' '-o' (Join-Path $output 'surface-query-test.exe')
    if ($LASTEXITCODE -ne 0) { throw 'Surface query test build failed.' }
    exit 0
}
if (-not $FinderOnly) {
    & $Compiler '-O3' '-std=c99' '-Wall' '-Wextra' '-fwrapv' '-I' $source `
        (Join-Path $root 'tools/filter-worker/structure_screen.c') @files '-lm' '-o' (Join-Path $output 'structure-screen.exe')
    if ($LASTEXITCODE -ne 0) { throw 'Native structure worker build failed.' }
    & $Compiler '-O3' '-std=c99' '-Wall' '-Wextra' '-fwrapv' '-I' $source `
        (Join-Path $root 'tools/filter-worker/spawn_model.c') @files '-lm' '-o' (Join-Path $output 'spawn-model.exe')
    if ($LASTEXITCODE -ne 0) { throw 'Native spawn model build failed.' }
}
& $Compiler '-O3' '-std=c99' '-Wall' '-Wextra' '-fwrapv' '-ffp-contract=off' '-I' $source `
    (Join-Path $root 'tools/filter-worker/seed_finder.c') @files '-lm' '-o' (Join-Path $output 'seed-finder.exe')
if ($LASTEXITCODE -ne 0) { throw 'Standalone finder build failed.' }
Copy-Item -LiteralPath (Join-Path $source 'LICENSE') -Destination (Join-Path $output 'CUBIOMES-LICENSE')
Write-Output 'Built the requested standalone executables against the reviewed Cubiomes revision.'
