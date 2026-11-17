# Gets the OFFICIAL Gradle wrapper from the exact Forge MDK, checks its SHA-1,
# and copies ONLY wrapper files into this project. No source files are replaced.
$ErrorActionPreference = 'Stop'
$project = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$wrapperJar = Join-Path $project 'gradle\wrapper\gradle-wrapper.jar'
$batch = Join-Path $project 'gradlew.bat'
if ((Test-Path $wrapperJar) -and (Test-Path $batch)) {
    Write-Host 'Official Gradle wrapper already available.'
    exit 0
}

$version = '1.20.1-47.4.28'
$url = "https://maven.minecraftforge.net/net/minecraftforge/forge/$version/forge-$version-mdk.zip"
# Published SHA-1 of the Forge 1.20.1-47.4.28 MDK archive.
$expectedSha1 = 'eee9145e892bb5ca5fe72323bd81c8522c53417f'
$temp = Join-Path ([System.IO.Path]::GetTempPath()) ('massfarmland-mdk-' + [Guid]::NewGuid().ToString('N'))
$zipFile = Join-Path $temp 'mdk.zip'
$unzipped = Join-Path $temp 'extracted'
New-Item -ItemType Directory -Path $temp -Force | Out-Null
try {
    [Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12
    Write-Host "Downloading official Forge MDK: $version"
    Invoke-WebRequest -Uri $url -OutFile $zipFile -UseBasicParsing
    $actualSha1 = (Get-FileHash -Path $zipFile -Algorithm SHA1).Hash.ToLowerInvariant()
    if ($actualSha1 -ne $expectedSha1) {
        throw "Forge MDK SHA-1 mismatch. Expected $expectedSha1; received $actualSha1"
    }
    Expand-Archive -Path $zipFile -DestinationPath $unzipped
    $wrapperDir = Join-Path $project 'gradle\wrapper'
    New-Item -ItemType Directory -Path $wrapperDir -Force | Out-Null
    foreach ($relative in @('gradlew', 'gradlew.bat', 'gradle/wrapper/gradle-wrapper.jar', 'gradle/wrapper/gradle-wrapper.properties')) {
        $source = Join-Path $unzipped $relative
        $destination = Join-Path $project $relative
        if (-not (Test-Path $source)) { throw "File missing from official MDK: $relative" }
        Copy-Item -Path $source -Destination $destination -Force
    }
    Write-Host 'Official Gradle wrapper installed.'
}
finally {
    Remove-Item $temp -Force -Recurse -ErrorAction SilentlyContinue
}
