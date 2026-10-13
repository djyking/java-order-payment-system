param([string]$AdminUsername = 'admin')
$ErrorActionPreference = 'Stop'
$repoRoot = Split-Path -Parent $PSScriptRoot
$envPath = Join-Path $repoRoot 'docker/.env'
if (Test-Path -LiteralPath $envPath) { throw 'docker/.env already exists. Edit it to add missing settings; this script will not overwrite credentials.' }
function New-LocalSecret {
    $buffer = New-Object byte[] 48
    $rng = [System.Security.Cryptography.RandomNumberGenerator]::Create()
    try { $rng.GetBytes($buffer) } finally { $rng.Dispose() }
    [Convert]::ToBase64String($buffer)
}
if ($AdminUsername -notmatch '^[A-Za-z0-9_]{3,32}$') { throw 'Use 3-32 letters, digits or underscores for the administrator name.' }
$lines = @(
    ('JWT_SECRET=' + (New-LocalSecret)),
    ('INTERNAL_SERVICE_TOKEN=' + (New-LocalSecret)),
    ('NACOS_AUTH_TOKEN=' + (New-LocalSecret)),
    ('NACOS_AUTH_IDENTITY_KEY=' + (New-LocalSecret)),
    ('NACOS_AUTH_IDENTITY_VALUE=' + (New-LocalSecret)),
    ('BOOTSTRAP_ADMIN_USERNAME=' + $AdminUsername),
    ('BOOTSTRAP_ADMIN_PASSWORD=' + (New-LocalSecret)),
    'PAYMENT_MOCK_ENABLED=false'
)
[IO.File]::WriteAllLines($envPath, $lines, (New-Object Text.UTF8Encoding($false)))
Write-Host ('Created local settings at ' + $envPath)
Write-Host 'Read BOOTSTRAP_ADMIN_USERNAME/PASSWORD in this ignored local file to sign in. Never commit this file.'
