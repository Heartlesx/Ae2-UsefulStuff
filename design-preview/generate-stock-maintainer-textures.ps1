Add-Type -AssemblyName System.Drawing

# ME Stock Maintainer 16x16 textures.
# Follows the AE2UEL machine palette and pixel density: light grey shell with a
# bevelled edge, dark recessed grille on the sides, and a reading panel on the front.
$textureDir = Join-Path $PSScriptRoot '..\src\main\resources\assets\ae2us\textures\blocks'
New-Item -ItemType Directory -Path $textureDir -Force | Out-Null

function Color([string]$hex) { return [System.Drawing.ColorTranslator]::FromHtml($hex) }

function Fill([System.Drawing.Bitmap]$image, [int]$x1, [int]$y1, [int]$x2, [int]$y2, [string]$hex) {
    $pixel = Color $hex
    for ($y = $y1; $y -le $y2; $y++) {
        for ($x = $x1; $x -le $x2; $x++) { $image.SetPixel($x, $y, $pixel) }
    }
}

function Dot([System.Drawing.Bitmap]$image, [int]$x, [int]$y, [string]$hex) { $image.SetPixel($x, $y, (Color $hex)) }

function Save-Texture([System.Drawing.Bitmap]$image, [string]$name) {
    $path = Join-Path $textureDir "stock_maintainer_$name.png"
    $image.Save($path, [System.Drawing.Imaging.ImageFormat]::Png)
    $image.Dispose()
}

# Machine shell: same light grey as AE2 crafting units, with top highlight and
# left/right/bottom shading so the cube reads correctly from every side.
function Shell([System.Drawing.Bitmap]$image) {
    Fill $image 0 0 15 15 '#9a9a9a'
    Fill $image 1 0 14 0 '#969696'
    Fill $image 0 1 0 14 '#838383'
    Fill $image 15 0 15 14 '#a5a5a5'
    Fill $image 1 15 14 15 '#7f7f7f'
    Dot $image 0 0 '#9a9a9a'; Dot $image 15 15 '#7d7d7d'
}

# Recessed grille: the standard AE2 machine side.
function Grille([System.Drawing.Bitmap]$image) {
    Fill $image 3 3 12 12 '#4d4d4d'
    foreach ($y in @(5, 8, 11)) {
        Fill $image 4 $y 11 $y '#5b5b5b'
        Fill $image 4 ($y - 1) 11 ($y - 1) '#575757'
    }
    foreach ($x in @(6, 9)) {
        Fill $image $x 2 $x 2 '#737f7f'
        Fill $image $x 6 $x 6 '#738080'
        Fill $image $x 10 $x 10 '#738080'
        Fill $image $x 13 $x 13 '#5e6868'
    }
}

# Front panel: a four-step stock gauge on top, a refill arrow below it.
function Front([System.Drawing.Bitmap]$image, [string]$lamp, [string]$lampLight) {
    Fill $image 3 3 12 12 '#3e3e3e'
    Fill $image 4 4 11 4 '#2b2b2b'
    Fill $image 4 6 5 6 '#4e6a67'
    Fill $image 6 6 7 6 '#3f5d5a'
    Fill $image 8 6 9 6 '#33504e'
    Fill $image 10 6 11 6 '#26403f'
    # Up arrow: tip, shoulders, stem, then the shelf it sits on.
    Fill $image 7 8 8 8 $lampLight
    Fill $image 6 9 9 9 $lamp
    Fill $image 7 10 8 10 $lamp
    Fill $image 7 11 8 11 $lamp
    Fill $image 5 12 10 12 '#9a9a9a'
    Fill $image 11 3 11 3 $lampLight
}

# Top face: recessed vent with a lamp in the centre, as on AE2 crafting units.
function Top([System.Drawing.Bitmap]$image, [string]$lamp) {
    Fill $image 3 3 12 12 '#8d8d8d'
    Fill $image 4 4 11 11 '#6a6a6a'
    Fill $image 5 5 10 10 '#4a4a4a'
    Fill $image 6 6 9 9 '#3a3a3a'
    Fill $image 7 7 8 8 $lamp
    Fill $image 5 2 5 2 '#a4a4a4'
    Fill $image 10 2 10 2 '#a4a4a4'
    Fill $image 5 13 5 13 '#4f4f4f'
    Fill $image 10 13 10 13 '#4f4f4f'
    Fill $image 2 5 2 10 '#7a7a7a'
    Fill $image 13 5 13 10 '#6d6d6d'
}

# Bottom face: darker shell with plain cooling slots.
function Bottom([System.Drawing.Bitmap]$image) {
    Fill $image 3 3 12 12 '#5a5a5a'
    Fill $image 4 4 11 11 '#4a4a4a'
    foreach ($y in @(6, 8, 10)) {
        Fill $image 5 $y 10 $y '#3b3b3b'
    }
}

$states = @(
    @('off', '#40585c', '#607579'),
    @('on', '#27b996', '#5ee2c1'),
    @('working', '#3cd4b2', '#9cf5e0')
)

foreach ($state in $states) {
    $name = $state[0]
    $lamp = $state[1]
    $light = $state[2]

    $side = [System.Drawing.Bitmap]::new(16, 16)
    Shell $side
    Grille $side
    Save-Texture $side "side_$name"

    $front = [System.Drawing.Bitmap]::new(16, 16)
    Shell $front
    Front $front $lamp $light
    Save-Texture $front "front_$name"

    $top = [System.Drawing.Bitmap]::new(16, 16)
    Shell $top
    Top $top $lamp
    Save-Texture $top "top_$name"
}

$bottom = [System.Drawing.Bitmap]::new(16, 16)
Shell $bottom
Bottom $bottom
Save-Texture $bottom 'bottom'

Write-Output "generated 10 textures in $textureDir"
