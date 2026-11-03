# Build Core 0.1.0-ntsd-cf4

Minecraft 1.20.1, Forge 47.4.28, Java 17, Gradle 8.8. Build from `ntnhcore-port/`
so the historical Implementation-Title remains stable.

```powershell
Set-Location -LiteralPath '.\ntnhcore-port'
$env:JAVA_HOME = '<your Java 17 installation>'
$env:PATH = "$env:JAVA_HOME\bin;$env:PATH"
python prepare_build.py --mods-dir '<mods directory matching BUILD_DEPENDENCIES.json>'
.\gradlew.bat build
```

Linux: run the same preparation helper using Python 3, then `sh gradlew build`
with Java 17 configured. Native Linux runtime behavior has not been certified.

The preparation helper verifies exact dependency hashes in
`ntnhcore-port/BUILD_DEPENDENCIES.json`. Obtain these official mod files yourself;
they are not committed or bundled as source. A first build needs network access;
`--offline --no-daemon build` requires a populated ForgeGradle cache.

Output: `ntnhcore-port/build/libs/ntnhcore-0.1.0-ntsd-cf4.jar`.
All class and resource entries match the tested binary on the recorded clean
rebuild. ZIP/container and generated manifest timestamps can differ, so rebuilding
does not promise identical whole-JAR SHA-256. No new rebuild is claimed here.

Source archive SHA-256: `6efac932c546dcbd89b14d5824ddf13ec6bf22bb600bdc667aded73b0a117307`.
Tested JAR SHA-256: `4a4919433050c87421eeacfef882d8c5e5cdd0e7f9880b8526724d497883a6c0`.
Preserve `LICENSE`, `LICENSES.md`, `NOTICE`, `CREDITS.md` and all embedded notices.
