# Build 0.1.0-ntsd-cf3

Minecraft 1.20.1, Forge 47.4.28, Java 17.

```powershell
Set-Location -LiteralPath '.\ntnhcore-port'
$env:JAVA_HOME = '<your Java 17 installation>'
$env:PATH = "$env:JAVA_HOME\bin;$env:PATH"
python prepare_build.py --mods-dir '<mods directory matching BUILD_DEPENDENCIES.json>'
.\gradlew.bat build
```

`prepare_build.py` checks the exact hashes in `BUILD_DEPENDENCIES.json`. Obtain these dependencies from their official projects; do not commit them. Core is built inside `ntnhcore-port` so its historical Implementation-Title stays stable. A configured Gradle dependency cache permits `--offline --no-daemon build`; a first build may require authorized downloads. Output: `ntnhcore-port/build/libs/ntnhcore-0.1.0-ntsd-cf3.jar`.

The exact corresponding-source ZIP SHA-256 is `4f28bff6cd34fed3dc835b3745a1c0461041e472813f64cac8960ec5f69d5138`. ZIP timestamps and generated manifest timestamps may differ on rebuild; class/resource comparison is the correspondence check. Retain all license notices.
