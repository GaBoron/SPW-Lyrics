param(
    [string]$OutputPath = (Join-Path $PSScriptRoot '..\winui\SpwLyrics.WinUI\Assets\AppIcon.ico')
)

Add-Type -AssemblyName System.Drawing

function New-RoundedRectanglePath {
    param(
        [System.Drawing.RectangleF]$Rectangle,
        [float]$Radius
    )

    $diameter = $Radius * 2
    $path = [System.Drawing.Drawing2D.GraphicsPath]::new()
    $path.AddArc($Rectangle.Left, $Rectangle.Top, $diameter, $diameter, 180, 90)
    $path.AddArc($Rectangle.Right - $diameter, $Rectangle.Top, $diameter, $diameter, 270, 90)
    $path.AddArc($Rectangle.Right - $diameter, $Rectangle.Bottom - $diameter, $diameter, $diameter, 0, 90)
    $path.AddArc($Rectangle.Left, $Rectangle.Bottom - $diameter, $diameter, $diameter, 90, 90)
    $path.CloseFigure()
    return $path
}

function New-IconFrame {
    param([int]$Size)

    $canvasSize = 1024
    $canvas = [System.Drawing.Bitmap]::new($canvasSize, $canvasSize)
    $graphics = [System.Drawing.Graphics]::FromImage($canvas)
    $graphics.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::AntiAlias
    $graphics.Clear([System.Drawing.Color]::Transparent)

    $backgroundPath = New-RoundedRectanglePath ([System.Drawing.RectangleF]::new(64, 64, 896, 896)) 208
    $backgroundBrush = [System.Drawing.Drawing2D.LinearGradientBrush]::new(
        [System.Drawing.PointF]::new(128, 96),
        [System.Drawing.PointF]::new(896, 928),
        [System.Drawing.Color]::FromArgb(255, 31, 132, 255),
        [System.Drawing.Color]::FromArgb(255, 42, 63, 181)
    )
    $graphics.FillPath($backgroundBrush, $backgroundPath)

    $bubblePath = New-RoundedRectanglePath ([System.Drawing.RectangleF]::new(176, 196, 672, 516)) 128
    $bubbleBrush = [System.Drawing.SolidBrush]::new([System.Drawing.Color]::White)
    $graphics.FillPath($bubbleBrush, $bubblePath)
    $tail = @(
        [System.Drawing.PointF]::new(256, 668),
        [System.Drawing.PointF]::new(192, 836),
        [System.Drawing.PointF]::new(464, 700)
    )
    $graphics.FillPolygon($bubbleBrush, $tail)

    $ink = [System.Drawing.Color]::FromArgb(255, 38, 73, 192)
    $linePen = [System.Drawing.Pen]::new($ink, 56)
    $linePen.StartCap = [System.Drawing.Drawing2D.LineCap]::Round
    $linePen.EndCap = [System.Drawing.Drawing2D.LineCap]::Round
    $graphics.DrawLine($linePen, 284, 368, 554, 368)
    $graphics.DrawLine($linePen, 284, 504, 466, 504)

    $notePen = [System.Drawing.Pen]::new($ink, 54)
    $notePen.StartCap = [System.Drawing.Drawing2D.LineCap]::Round
    $notePen.EndCap = [System.Drawing.Drawing2D.LineCap]::Round
    $graphics.DrawLine($notePen, 666, 334, 666, 530)
    $graphics.DrawLine($notePen, 666, 334, 770, 310)
    $noteBrush = [System.Drawing.SolidBrush]::new($ink)
    $graphics.FillEllipse($noteBrush, 574, 484, 120, 96)

    $frame = [System.Drawing.Bitmap]::new($Size, $Size)
    $frameGraphics = [System.Drawing.Graphics]::FromImage($frame)
    $frameGraphics.CompositingQuality = [System.Drawing.Drawing2D.CompositingQuality]::HighQuality
    $frameGraphics.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
    $frameGraphics.PixelOffsetMode = [System.Drawing.Drawing2D.PixelOffsetMode]::HighQuality
    $frameGraphics.DrawImage($canvas, 0, 0, $Size, $Size)

    $stream = [System.IO.MemoryStream]::new()
    $frame.Save($stream, [System.Drawing.Imaging.ImageFormat]::Png)
    $bytes = $stream.ToArray()

    $stream.Dispose()
    $frameGraphics.Dispose()
    $frame.Dispose()
    $noteBrush.Dispose()
    $notePen.Dispose()
    $linePen.Dispose()
    $bubbleBrush.Dispose()
    $bubblePath.Dispose()
    $backgroundBrush.Dispose()
    $backgroundPath.Dispose()
    $graphics.Dispose()
    $canvas.Dispose()
    return $bytes
}

$sizes = @(16, 20, 24, 32, 40, 48, 64, 128, 256)
$frames = [System.Collections.Generic.List[byte[]]]::new()
foreach ($size in $sizes) {
    [byte[]]$frame = New-IconFrame $size
    $frames.Add($frame)
}
$directory = Split-Path -Parent $OutputPath
[System.IO.Directory]::CreateDirectory($directory) | Out-Null

$stream = [System.IO.File]::Create($OutputPath)
$writer = [System.IO.BinaryWriter]::new($stream)
$writer.Write([uint16]0)
$writer.Write([uint16]1)
$writer.Write([uint16]$frames.Count)

$offset = 6 + (16 * $frames.Count)
for ($index = 0; $index -lt $frames.Count; $index++) {
    $size = $sizes[$index]
    $writer.Write([byte]$(if ($size -eq 256) { 0 } else { $size }))
    $writer.Write([byte]$(if ($size -eq 256) { 0 } else { $size }))
    $writer.Write([byte]0)
    $writer.Write([byte]0)
    $writer.Write([uint16]1)
    $writer.Write([uint16]32)
    $writer.Write([uint32]$frames[$index].Length)
    $writer.Write([uint32]$offset)
    $offset += $frames[$index].Length
}

$frames | ForEach-Object { $writer.Write($_) }
$writer.Dispose()
$stream.Dispose()
