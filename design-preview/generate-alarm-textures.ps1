Add-Type -AssemblyName System.Drawing

$textureDir = Join-Path $PSScriptRoot '..\src\main\resources\assets\ae2us\textures\blocks'
New-Item -ItemType Directory -Path $textureDir -Force | Out-Null

function Color([string]$hex) { return [System.Drawing.ColorTranslator]::FromHtml($hex) }

function Fill([System.Drawing.Bitmap]$image, [int]$x1, [int]$y1, [int]$x2, [int]$y2, [string]$hex) {
    $pixel = Color $hex
    for ($y = $y1; $y -le $y2; $y++) {
        for ($x = $x1; $x -le $x2; $x++) { $image.SetPixel($x, $y, $pixel) }
    }
}

function Save-Texture([System.Drawing.Bitmap]$image, [string]$name) {
    $path = Join-Path $textureDir "storage_alarm_$name.png"
    $image.Save($path, [System.Drawing.Imaging.ImageFormat]::Png)
    $image.Dispose()
}

function Side([string]$name, [string]$signal, [string]$highlight) {
    $image = [System.Drawing.Bitmap]::new(32, 32)
    Fill $image 0 0 31 31 '#a9bbb5'
    Fill $image 0 0 31 1 '#e4ece6'
    Fill $image 0 0 1 31 '#d6e2da'
    Fill $image 30 0 31 31 '#496265'
    Fill $image 0 30 31 31 '#5b7473'
    Fill $image 2 2 29 3 '#cbdad2'
    Fill $image 2 28 29 29 '#708987'
    Fill $image 3 5 28 26 '#30494e'
    Fill $image 4 6 27 25 '#718b87'
    Fill $image 5 7 26 24 '#263e43'
    Fill $image 6 8 25 23 '#172c34'
    Fill $image 8 10 19 10 '#8ba8a3'
    Fill $image 8 12 16 12 '#617e7d'
    Fill $image 8 15 22 15 '#8ba8a3'
    Fill $image 8 17 18 17 '#617e7d'
    Fill $image 8 20 23 21 $signal
    Fill $image 8 20 23 20 $highlight
    Fill $image 3 3 4 4 '#edf2ec'
    Fill $image 27 3 28 4 '#4c6565'
    Fill $image 3 27 4 28 '#59716f'
    Fill $image 27 27 28 28 '#3b5457'
    Save-Texture $image "side_$name"
}

function Top([string]$name, [string]$signal, [string]$highlight) {
    $image = [System.Drawing.Bitmap]::new(32, 32)
    Fill $image 0 0 31 31 '#b6c7bf'
    Fill $image 0 0 31 1 '#e6eee7'
    Fill $image 0 0 1 31 '#e0e9e0'
    Fill $image 30 0 31 31 '#657e7b'
    Fill $image 0 30 31 31 '#708a85'
    Fill $image 5 5 26 26 '#536d6d'
    Fill $image 6 6 25 25 '#d9e3da'
    Fill $image 8 8 23 23 '#2e484d'
    Fill $image 9 9 22 22 '#142a32'
    Fill $image 11 11 20 20 $signal
    Fill $image 11 11 20 12 $highlight
    Fill $image 11 19 20 20 '#314b4e'
    Fill $image 3 3 4 4 '#edf2ec'
    Fill $image 27 3 28 4 '#5a7471'
    Fill $image 3 27 4 28 '#6e8782'
    Fill $image 27 27 28 28 '#4d6868'
    Save-Texture $image "top_$name"
}

$states = @(
    @('off', '#40585c', '#607579'),
    @('normal', '#27b996', '#5ee2c1'),
    @('alert', '#d84c47', '#f48d7f'),
    @('item', '#8b5658', '#ad797b')
)
foreach ($state in $states) {
    Side $state[0] $state[1] $state[2]
    Top $state[0] $state[1] $state[2]
}

$bottom = [System.Drawing.Bitmap]::new(32, 32)
Fill $bottom 0 0 31 31 '#6b8581'
Fill $bottom 2 2 29 29 '#91a9a0'
Fill $bottom 5 8 26 10 '#30494e'
Fill $bottom 5 15 26 17 '#30494e'
Fill $bottom 5 22 26 24 '#30494e'
Save-Texture $bottom 'bottom'

$sheet = [System.Drawing.Bitmap]::new(1024, 512)
$graphics = [System.Drawing.Graphics]::FromImage($sheet)
$graphics.Clear((Color '#f5f7f4'))
$graphics.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::NearestNeighbor
$graphics.PixelOffsetMode = [System.Drawing.Drawing2D.PixelOffsetMode]::Half
for ($i = 0; $i -lt $states.Count; $i++) {
    foreach ($entry in @(@('side', 0), @('top', 1))) {
        $path = Join-Path $textureDir "storage_alarm_$($entry[0])_$($states[$i][0]).png"
        $texture = [System.Drawing.Image]::FromFile($path)
        $graphics.DrawImage($texture, [System.Drawing.Rectangle]::new($i * 256, [int]$entry[1] * 256, 256, 256),
            [System.Drawing.Rectangle]::new(0, 0, 32, 32), [System.Drawing.GraphicsUnit]::Pixel)
        $texture.Dispose()
    }
}
$graphics.Dispose()
$sheet.Save((Join-Path $PSScriptRoot 'storage-alarm-textures.png'), [System.Drawing.Imaging.ImageFormat]::Png)
$sheet.Dispose()
