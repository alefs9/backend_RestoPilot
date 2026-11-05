$ErrorActionPreference = 'Stop'
$raiz = Split-Path $PSScriptRoot -Parent
$destino = Join-Path $raiz '.env'
if (Test-Path $destino) { throw 'Ya existe .env. Se conserva; revisa sus valores manualmente.' }
$bytes = New-Object byte[] 32
$rng = [System.Security.Cryptography.RandomNumberGenerator]::Create()
try {
    $rng.GetBytes($bytes)
    $jwt = [Convert]::ToBase64String($bytes)
    $rng.GetBytes($bytes)
    $clave = [Convert]::ToBase64String($bytes)
} finally { $rng.Dispose() }
$texto = Get-Content -Raw (Join-Path $raiz '.env.example')
$texto = $texto -replace '(?m)^JWT_SECRET=.*$', "JWT_SECRET=$jwt"
$texto = $texto -replace '(?m)^DB_PASSWORD=.*$', "DB_PASSWORD=$clave"
$texto = $texto -replace '(?m)^POSTGRES_PASSWORD=.*$', "POSTGRES_PASSWORD=$clave"
# Sin BOM; no imprimir secretos.
[System.IO.File]::WriteAllText($destino, $texto, (New-Object System.Text.UTF8Encoding($false)))
Write-Host '.env creado para Docker Compose. Para PostgreSQL ya existente, ajusta DB_PASSWORD localmente.'
