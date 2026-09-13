$ErrorActionPreference = 'Stop'
Set-Location (Split-Path $PSScriptRoot -Parent)
if (Test-Path '.env') { Write-Output 'Keeping existing configuration and data.'; exit 0 }
function New-LocalSecret { $bytes = New-Object byte[] 32; [System.Security.Cryptography.RandomNumberGenerator]::Fill($bytes); return [Convert]::ToBase64String($bytes) }
$settings = [ordered]@{}
foreach ($name in @('DB_PASSWORD','IDENTITY_DB_PASSWORD','RABBIT_PASSWORD','ADMIN_PASSWORD','DEMO_PASSWORD','QA_ALICE_SECRET','QA_BOB_SECRET')) { $settings[$name] = New-LocalSecret }
New-Item -ItemType Directory -Force '.local' | Out-Null
$realm = Get-Content 'infra/realm.template.json' -Raw
foreach ($name in $settings.Keys) { $realm = $realm.Replace('__' + $name + '__', $settings[$name]) }
[IO.File]::WriteAllText((Join-Path (Get-Location) '.local/realm.json'), $realm)
[IO.File]::WriteAllLines((Join-Path (Get-Location) '.env'), @($settings.GetEnumerator() | ForEach-Object { $_.Key + '=' + $_.Value }))
Write-Output 'Created ignored .env. Username demo; password in DEMO_PASSWORD.'
