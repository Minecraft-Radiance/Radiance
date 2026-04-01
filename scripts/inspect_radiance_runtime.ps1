param(
    [string]$JarPath = "C:\Users\Felix\Downloads\Radiance-0.1.3-alpha-fabric-1.21.4-windows.jar",
    [string]$CoreDllPath
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

Add-Type -AssemblyName System.IO.Compression.FileSystem

function Get-AsciiStrings {
    param(
        [byte[]]$Bytes,
        [int]$MinLength = 4
    )

    $results = New-Object System.Collections.Generic.List[string]
    $builder = New-Object System.Text.StringBuilder

    foreach ($byteValue in $Bytes) {
        if (($byteValue -ge 32 -and $byteValue -le 126) -or $byteValue -eq 9) {
            [void]$builder.Append([char]$byteValue)
        } else {
            if ($builder.Length -ge $MinLength) {
                $results.Add($builder.ToString())
            }
            [void]$builder.Clear()
        }
    }

    if ($builder.Length -ge $MinLength) {
        $results.Add($builder.ToString())
    }

    return $results
}

function Get-ZipEntryBytes {
    param(
        [System.IO.Compression.ZipArchive]$Archive,
        [string]$EntryName
    )

    $entry = $Archive.Entries | Where-Object { $_.FullName -eq $EntryName } | Select-Object -First 1
    if ($null -eq $entry) {
        return $null
    }

    $stream = $entry.Open()
    try {
        $memoryStream = New-Object System.IO.MemoryStream
        $stream.CopyTo($memoryStream)
        return $memoryStream.ToArray()
    } finally {
        $stream.Dispose()
    }
}

if (-not $CoreDllPath -and -not (Test-Path $JarPath)) {
    throw "Jar not found: $JarPath"
}

$coreDllBytes = $null
$shaderEntryMap = [ordered]@{}
$jar = $null

if ($CoreDllPath) {
    if (-not (Test-Path $CoreDllPath)) {
        throw "core.dll path not found: $CoreDllPath"
    }
    $coreDllBytes = [System.IO.File]::ReadAllBytes((Resolve-Path $CoreDllPath))
}

if (Test-Path $JarPath) {
    $resolvedJarPath = (Resolve-Path $JarPath)
    $jar = [System.IO.Compression.ZipFile]::OpenRead($resolvedJarPath)
    try {
        if ($null -eq $coreDllBytes) {
            $coreDllBytes = Get-ZipEntryBytes -Archive $jar -EntryName "core.dll"
        }

        foreach ($entry in $jar.Entries) {
            if ($entry.FullName -like "shaders/world/ray_tracing/*.spv") {
                $shaderEntryMap[$entry.FullName] = Get-ZipEntryBytes -Archive $jar -EntryName $entry.FullName
            }
        }
    } finally {
        $jar.Dispose()
    }
}

if ($null -eq $coreDllBytes) {
    throw "Unable to locate core.dll bytes from jar or explicit path."
}

$optionPatterns = [ordered]@{
    "world_representation_mode" = @("world_representation_mode", "chunk_aabb")
    "chunk_traversal_mode" = @("chunk_traversal_mode", "voxel_dda", "brick", "macrocell")
    "chunk_data_layout" = @("chunk_data_layout", "occupancy_bitmask", "occupancy_palette", "face_mask")
    "chunk_macrocell_size" = @("chunk_macrocell_size", "macrocell")
    "diffuse_gi_mode" = @("diffuse_gi_mode", "radiance_cache", "low_cost_hybrid", "probe", "sharc")
    "num_ray_bounces" = @("num_ray_bounces", "numRayBounces", "rayBounces")
    "use_jitter" = @("use_jitter", "cameraJitter", "unjitteredPixelCenter")
}

$coreStrings = Get-AsciiStrings -Bytes $coreDllBytes -MinLength 4
$coreSha256 = [System.BitConverter]::ToString(
    [System.Security.Cryptography.SHA256]::HashData($coreDllBytes)
).Replace("-", "").ToLowerInvariant()

Write-Host "=== core.dll ==="
Write-Host "size   : $($coreDllBytes.Length)"
Write-Host "sha256 : $coreSha256"
Write-Host ""

Write-Host "=== core.dll option evidence ==="
foreach ($kvp in $optionPatterns.GetEnumerator()) {
    $hits = New-Object System.Collections.Generic.List[string]
    foreach ($needle in $kvp.Value) {
        foreach ($candidate in $coreStrings) {
            if ($candidate -like "*$needle*" -and -not $hits.Contains($candidate)) {
                $hits.Add($candidate)
            }
        }
    }

    if ($hits.Count -eq 0) {
        Write-Host ("- {0}: no direct core.dll string evidence" -f $kvp.Key)
    } else {
        Write-Host ("- {0}:" -f $kvp.Key)
        foreach ($hit in $hits | Select-Object -First 6) {
            Write-Host ("    {0}" -f $hit)
        }
    }
}

Write-Host ""
Write-Host "=== shader option evidence (world/ray_tracing) ==="
foreach ($kvp in $optionPatterns.GetEnumerator()) {
    $shaderHits = New-Object System.Collections.Generic.List[string]
    foreach ($shaderEntry in $shaderEntryMap.GetEnumerator()) {
        $shaderStrings = Get-AsciiStrings -Bytes $shaderEntry.Value -MinLength 4
        $matched = $false
        foreach ($needle in $kvp.Value) {
            if ($matched) {
                break
            }
            foreach ($candidate in $shaderStrings) {
                if ($candidate -like "*$needle*") {
                    $shaderHits.Add("$($shaderEntry.Key) :: $candidate")
                    $matched = $true
                    break
                }
            }
        }
    }

    if ($shaderHits.Count -eq 0) {
        Write-Host ("- {0}: no ray-tracing shader string evidence" -f $kvp.Key)
    } else {
        Write-Host ("- {0}:" -f $kvp.Key)
        foreach ($hit in $shaderHits | Select-Object -First 8) {
            Write-Host ("    {0}" -f $hit)
        }
    }
}
