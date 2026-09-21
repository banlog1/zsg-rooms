param(
    [string]$ImageDirectory,
    [string]$LoadingImageDirectory,
    [string]$OutputPath = 'build/distributions/zsg-rooms-filter-gallery.zip'
)

$ErrorActionPreference = 'Stop'
$root = Split-Path $PSScriptRoot -Parent
$pack = Join-Path $root 'optional-assets/filter-gallery'
$textures = Join-Path $pack 'assets/zsg-rooms/textures/gui/filters'
$names = @('bt', 'dt', 'rp', 'shipwreck', 'village')
$loadingTextures = Join-Path $pack 'assets/zsg-rooms/textures/gui/loading'
$loadingNames = @('bt', 'dt', 'dt2', 'rp', 'shipwreck1', 'shipwreck2', 'village', 'village2')

# Optional import resizes originals once; the checked-in pack contains only previews.
function Import-Images($Directory, $Destination, $Names, $Width, $Height) {
    Add-Type -AssemblyName System.Drawing
    [IO.Directory]::CreateDirectory($Destination) | Out-Null
    foreach ($name in $Names) {
        $source = [Drawing.Image]::FromFile((Join-Path $Directory "$name.png"))
        try {
            if ($source.Width * 9 -ne $source.Height * 16) { throw "$name must be a 16:9 image" }
            $bitmap = New-Object Drawing.Bitmap($Width, $Height)
            try {
                $graphics = [Drawing.Graphics]::FromImage($bitmap)
                $attributes = New-Object Drawing.Imaging.ImageAttributes
                try {
                    $graphics.InterpolationMode = [Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
                    $graphics.PixelOffsetMode = [Drawing.Drawing2D.PixelOffsetMode]::HighQuality
                    $attributes.SetWrapMode([Drawing.Drawing2D.WrapMode]::TileFlipXY)
                    $graphics.DrawImage($source, [Drawing.Rectangle]::new(0, 0, $Width, $Height),
                        0, 0, $source.Width, $source.Height, [Drawing.GraphicsUnit]::Pixel, $attributes)
                    $bitmap.Save((Join-Path $Destination "$name.png"), [Drawing.Imaging.ImageFormat]::Png)
                } finally { $attributes.Dispose(); $graphics.Dispose() }
            } finally { $bitmap.Dispose() }
        } finally { $source.Dispose() }
    }
}
if ($ImageDirectory) { Import-Images $ImageDirectory $textures $names 480 270 }
if ($LoadingImageDirectory) { Import-Images $LoadingImageDirectory $loadingTextures $loadingNames 1280 720 }

foreach ($name in $names) {
    if (!(Test-Path -LiteralPath (Join-Path $textures "$name.png"))) { throw "Missing preview: $name" }
}
if (![IO.Path]::IsPathRooted($OutputPath)) { $OutputPath = Join-Path $root $OutputPath }
[IO.Directory]::CreateDirectory((Split-Path $OutputPath -Parent)) | Out-Null
Add-Type -AssemblyName System.IO.Compression.FileSystem
Add-Type -AssemblyName System.IO.Compression
$stream = [IO.File]::Create($OutputPath)
try {
    $archive = [IO.Compression.ZipArchive]::new($stream, [IO.Compression.ZipArchiveMode]::Create)
    try {
        # Minecraft looks up forward-slash paths, including on Windows.
        [IO.Compression.ZipFileExtensions]::CreateEntryFromFile($archive, (Join-Path $pack 'pack.mcmeta'), 'pack.mcmeta') | Out-Null
        foreach ($name in $names) {
            [IO.Compression.ZipFileExtensions]::CreateEntryFromFile($archive, (Join-Path $textures "$name.png"),
                "assets/zsg-rooms/textures/gui/filters/$name.png") | Out-Null
        }
        foreach ($name in $loadingNames) {
            $path = Join-Path $loadingTextures "$name.png"
            if (Test-Path -LiteralPath $path) {
                [IO.Compression.ZipFileExtensions]::CreateEntryFromFile($archive, $path,
                        "assets/zsg-rooms/textures/gui/loading/$name.png") | Out-Null
            }
        }
    } finally { $archive.Dispose() }
} finally { $stream.Dispose() }
Get-Item -LiteralPath $OutputPath | Select-Object FullName, Length
