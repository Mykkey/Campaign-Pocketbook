# Small "starting" splash shown while the app builds and launches.
# It closes itself as soon as the app window appears (or after a timeout).
Add-Type -AssemblyName System.Windows.Forms
Add-Type -AssemblyName System.Drawing
[System.Windows.Forms.Application]::EnableVisualStyles()

$root = Split-Path -Parent $MyInvocation.MyCommand.Path
$iconPath = Join-Path $root "src\main\resources\icon.png"

$form = New-Object System.Windows.Forms.Form
$form.Text = "Starting"
$form.FormBorderStyle = "None"
$form.StartPosition = "CenterScreen"
$form.Size = New-Object System.Drawing.Size(360, 190)
$form.BackColor = [System.Drawing.Color]::FromArgb(20, 18, 43)
$form.TopMost = $true
$form.ShowInTaskbar = $false

if (Test-Path $iconPath) {
    $pic = New-Object System.Windows.Forms.PictureBox
    $pic.Image = [System.Drawing.Image]::FromFile($iconPath)
    $pic.SizeMode = "Zoom"
    $pic.Size = New-Object System.Drawing.Size(72, 72)
    $pic.Location = New-Object System.Drawing.Point(144, 22)
    $form.Controls.Add($pic)
}

$label = New-Object System.Windows.Forms.Label
$label.Text = "Starting D&&D Campaign Notes..."
$label.ForeColor = [System.Drawing.Color]::White
$label.Font = New-Object System.Drawing.Font("Segoe UI", 11, [System.Drawing.FontStyle]::Bold)
$label.TextAlign = "MiddleCenter"
$label.Size = New-Object System.Drawing.Size(360, 30)
$label.Location = New-Object System.Drawing.Point(0, 104)
$form.Controls.Add($label)

$bar = New-Object System.Windows.Forms.ProgressBar
$bar.Style = "Marquee"
$bar.MarqueeAnimationSpeed = 30
$bar.Size = New-Object System.Drawing.Size(240, 8)
$bar.Location = New-Object System.Drawing.Point(60, 148)
$form.Controls.Add($bar)

$started = Get-Date
$timer = New-Object System.Windows.Forms.Timer
$timer.Interval = 200
$timer.Add_Tick({
    $up = Get-Process -ErrorAction SilentlyContinue |
        Where-Object { $_.MainWindowTitle -like "D&D Campaign Notes*" }
    $elapsed = ((Get-Date) - $started).TotalSeconds
    # keep the splash a moment after the window shows so it never flashes
    if (($up -and $elapsed -gt 1.0) -or $elapsed -gt 90) { $timer.Stop(); $form.Close() }
})
$timer.Start()
[void]$form.ShowDialog()
