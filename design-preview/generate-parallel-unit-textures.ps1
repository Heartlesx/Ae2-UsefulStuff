Add-Type -AssemblyName System.Drawing

$source = [System.Drawing.Bitmap]::new((Join-Path $PSScriptRoot '并行处理单元.png'))
$textureDir = Join-Path $PSScriptRoot '..\src\main\resources\assets\ae2us\textures\blocks'
$tiers = @('16', '64', '256', '1024', '2048', '4096')
$states = @('', '_formed', '_powered')
$left = @(66, 484, 901, 1316, 1734, 2152)
$width = @(345, 344, 344, 345, 344, 344)
$top = @(128, 533, 942)
$height = @(338, 342, 345)

$scale = 12
$gap = 12
$preview = [System.Drawing.Bitmap]::new((6 * 16 * $scale + 5 * $gap), (3 * 16 * $scale + 2 * $gap))
$graphics = [System.Drawing.Graphics]::FromImage($preview)
$graphics.Clear([System.Drawing.ColorTranslator]::FromHtml('#293033'))

for ($row = 0; $row -lt 3; $row++) {
    for ($column = 0; $column -lt 6; $column++) {
        $tile = [System.Drawing.Bitmap]::new(16, 16)
        for ($y = 0; $y -lt 16; $y++) {
            $sy = $top[$row] + [int][Math]::Floor(($y + 0.5) * $height[$row] / 16)
            for ($x = 0; $x -lt 16; $x++) {
                $sx = $left[$column] + [int][Math]::Floor(($x + 0.5) * $width[$column] / 16)
                $color = $source.GetPixel($sx, $sy)
                $tile.SetPixel($x, $y, $color)
                $brush = [System.Drawing.SolidBrush]::new($color)
                $graphics.FillRectangle($brush, ($column * (16 * $scale + $gap) + $x * $scale),
                    ($row * (16 * $scale + $gap) + $y * $scale), $scale, $scale)
                $brush.Dispose()
            }
        }
        $tile.Save((Join-Path $textureDir "parallel_unit_$($tiers[$column])$($states[$row]).png"),
            [System.Drawing.Imaging.ImageFormat]::Png)
        $tile.Dispose()
    }
}

$graphics.Dispose()
$preview.Save((Join-Path $PSScriptRoot 'parallel-unit-textures.png'), [System.Drawing.Imaging.ImageFormat]::Png)
$preview.Dispose()
$source.Dispose()
