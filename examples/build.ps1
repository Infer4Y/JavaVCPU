$ErrorActionPreference = 'Stop'
# Hex listings use opcode bytes, not assembler mnemonics. Output stays beside source.
foreach ($folder in @('bare-metal', 'dos')) {
    Get-ChildItem -LiteralPath (Join-Path $PSScriptRoot $folder) -Filter '*.hex' | ForEach-Object {
        $source = (Get-Content -LiteralPath $_.FullName | ForEach-Object { ($_ -split '#', 2)[0] }) -join ' '
        $tokens = @($source -split '\s+' | Where-Object { $_ })
        $bytes = [byte[]] @($tokens | ForEach-Object {
            if ($_ -notmatch '^[0-9A-Fa-f]{2}$') { throw "Invalid byte: $_" }
            [Convert]::ToByte($_, 16)
        })
        $limit = if ($folder -eq 'dos') { 61200 } else { 256 }
        if ($bytes.Length -eq 0 -or $bytes.Length -gt $limit) { throw "Invalid length: $($_.Name)" }
        $extension = if ($folder -eq 'dos') { '.COM' } else { '.bin' }
        $target = [IO.Path]::ChangeExtension($_.FullName, $extension)
        [IO.File]::WriteAllBytes($target, $bytes)
        Write-Output "$([IO.Path]::GetFileName($target)): $($bytes.Length) bytes"
    }
}
