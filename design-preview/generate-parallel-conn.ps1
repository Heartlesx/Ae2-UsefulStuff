Add-Type -AssemblyName System.Drawing

$source = [System.Drawing.Bitmap]::new((Join-Path $PSScriptRoot '像素链接图.png'))
$texture = [System.Drawing.Bitmap]::new(16, 16)
$preview = [System.Drawing.Bitmap]::new(256, 256)
$graphics = [System.Drawing.Graphics]::FromImage($preview)

# Average the generated vertical stripes so the stretched edge stays uniform along its length.
for ($y = 0; $y -lt 16; $y++) {
    $sy = [int][Math]::Floor(($y + 0.5) * $source.Height / 16)
    $r = 0; $g = 0; $b = 0
    for ($sample = 0; $sample -lt 16; $sample++) {
        $sx = 660 + [int][Math]::Floor(($sample + 0.5) * 1240 / 16)
        $pixel = $source.GetPixel($sx, $sy)
        $r += $pixel.R; $g += $pixel.G; $b += $pixel.B
    }
    $color = [System.Drawing.Color]::FromArgb(255, [int]($r / 16), [int]($g / 16), [int]($b / 16))
    $brush = [System.Drawing.SolidBrush]::new($color)
    for ($x = 0; $x -lt 16; $x++) {
        $texture.SetPixel($x, $y, $color)
        $graphics.FillRectangle($brush, $x * 16, $y * 16, 16, 16)
    }
    $brush.Dispose()
}

$graphics.Dispose()
$texture.Save((Join-Path $PSScriptRoot '..\src\main\resources\assets\ae2us\textures\blocks\parallel_conn.png'),
    [System.Drawing.Imaging.ImageFormat]::Png)
$preview.Save((Join-Path $PSScriptRoot 'parallel-conn-preview.png'), [System.Drawing.Imaging.ImageFormat]::Png)
$texture.Dispose()
$preview.Dispose()
$source.Dispose()
