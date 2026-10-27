Add-Type -AssemblyName System.Drawing

# Renders an 8x zoomed preview of the maintainer textures side by side with AE2's
# own interface and crafting unit, so the pixel density and palette can be compared.
$root = Join-Path $PSScriptRoot '..'
$texDir = Join-Path $root 'src\main\resources\assets\ae2us\textures\blocks'
$refDir = Join-Path $root '.ref\AE2-UEL\src\main\resources\assets\appliedenergistics2\textures\blocks'
$out = Join-Path $PSScriptRoot 'stock-maintainer-preview.png'

$zoom = 8
$pad = 8
$label = 14

$ours = @(
    'stock_maintainer_front_off.png',
    'stock_maintainer_front_on.png',
    'stock_maintainer_front_working.png',
    'stock_maintainer_side_on.png',
    'stock_maintainer_top_on.png',
    'stock_maintainer_bottom.png'
)
$theirs = @(
    (Join-Path $refDir 'interface.png'),
    (Join-Path $refDir 'crafting\unit.png'),
    (Join-Path $refDir 'crafting\monitor.png'),
    (Join-Path $refDir 'crafting\unit_base.png')
)

$columns = $ours.Count + $theirs.Count
$cell = 16 * $zoom
$width = $pad + $columns * ($cell + $pad)
$height = $pad * 2 + $cell + $label

$canvas = New-Object System.Drawing.Bitmap -ArgumentList $width, $height
$graphics = [System.Drawing.Graphics]::FromImage($canvas)
$graphics.Clear([System.Drawing.ColorTranslator]::FromHtml('#2b3a3c'))
$graphics.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::NearestNeighbor
$graphics.PixelOffsetMode = [System.Drawing.Drawing2D.PixelOffsetMode]::Half

$index = 0
foreach ($name in $ours) {
    $path = Join-Path $texDir $name
    $image = New-Object System.Drawing.Bitmap -ArgumentList $path
    $graphics.DrawImage($image, ($pad + $index * ($cell + $pad)), $pad, $cell, $cell)
    $image.Dispose()
    $index++
}
foreach ($path in $theirs) {
    if (-not (Test-Path $path)) { continue }
    $image = New-Object System.Drawing.Bitmap -ArgumentList $path
    $graphics.DrawImage($image, ($pad + $index * ($cell + $pad)), $pad, $cell, $cell)
    $image.Dispose()
    $index++
}

$graphics.Dispose()
$canvas.Save($out, [System.Drawing.Imaging.ImageFormat]::Png)
$canvas.Dispose()
Write-Output "preview written to $out"
